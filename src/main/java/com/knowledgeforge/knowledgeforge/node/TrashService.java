package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.activity.ActivityLogService;
import com.knowledgeforge.knowledgeforge.ai.AiAnswerRepository;
import com.knowledgeforge.knowledgeforge.ai.AiSummaryRepository;
import com.knowledgeforge.knowledgeforge.attachment.Attachment;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentRepository;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentStorageService;
import com.knowledgeforge.knowledgeforge.comment.CommentRepository;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.editlock.EditLock;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersion;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trash, archive, restore and purge (domain spec 4.5). A branch is a page plus its ACTIVE descendants; they get
 * the same {@code trashRootId} (archive reuses that field as "branch root"), and restore works per root.
 */
@Service
public class TrashService {
    private static final Logger log = LoggerFactory.getLogger(TrashService.class);
    static final Duration RETENTION = Duration.ofDays(30);
    private static final int CHUNK = 1000, BATCH = 100;

    private final NodeRepository nodes;
    private final SpaceRepository spaces;
    private final UserRepository users;
    private final NodeAccessService access;
    private final ActivityLogService activity;
    private final AttachmentRepository attachments;
    private final AttachmentStorageService storage;
    private final CommentRepository comments;
    private final AiSummaryRepository summaries;
    private final AiAnswerRepository answers;
    private final MongoTemplate mongo;

    public TrashService(NodeRepository nodes, SpaceRepository spaces, UserRepository users, NodeAccessService access,
                        ActivityLogService activity, AttachmentRepository attachments, AttachmentStorageService storage,
                        CommentRepository comments, AiSummaryRepository summaries, AiAnswerRepository answers,
                        MongoTemplate mongo) {
        this.nodes = nodes;
        this.spaces = spaces;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.attachments = attachments;
        this.storage = storage;
        this.comments = comments;
        this.summaries = summaries;
        this.answers = answers;
        this.mongo = mongo;
    }

    // ---------- trash / archive ----------

    /** Sub-pages that would move with this page (for the confirm dialog). */
    public CountDTO subtreeCount(String id) {
        access.requireEdit(id);
        return new CountDTO((int) mongo.count(Query.query(Criteria.where("ancestors").is(id)
                .and("status").is(NodeStatus.ACTIVE)), Node.class));
    }

    public CountDTO trash(String id) {
        Node node = access.requireEdit(id);
        int count = mark(node, NodeStatus.TRASHED);
        log("TRASH_NODE", node);
        return new CountDTO(count);
    }

    public CountDTO archive(String id) {
        Node node = access.requireEdit(id);
        int count = mark(node, NodeStatus.ARCHIVED);
        log("ARCHIVE_NODE", node);
        return new CountDTO(count);
    }

    /** Moves the page and its ACTIVE descendants to {@code to}; returns the number of sub-pages. */
    private int mark(Node root, NodeStatus to) {
        User user = access.currentUser();
        Date now = new Date();
        List<String> ids = new ArrayList<>();
        ids.add(root.getId());
        mongo.find(Query.query(Criteria.where("ancestors").is(root.getId()).and("status").is(NodeStatus.ACTIVE)), Node.class)
                .forEach(n -> ids.add(n.getId()));
        Update u = new Update().set("status", to).set("trashRootId", root.getId());
        if (to == NodeStatus.TRASHED) u.set("trashedAt", now).set("trashedBy", user.getId());
        for (List<String> chunk : chunks(ids)) {
            mongo.updateMulti(byIds(chunk), u, Node.class);
            mongo.updateMulti(byIds(chunk), new Update().set("search.status", to), NodeContent.class);
        }
        mongo.updateFirst(byIds(List.of(root.getId())), new Update()
                .set("updatedBy", new UserRef(user.getId(), user.getFullName(), user.getProfilePicture()))
                .set("updatedAt", now), Node.class);
        return ids.size() - 1;
    }

    // ---------- lists ----------

    public BranchListDTO trashList(String spaceId) {
        return branches(spaceId, NodeStatus.TRASHED);
    }

    public BranchListDTO archivedList(String spaceId) {
        return branches(spaceId, NodeStatus.ARCHIVED);
    }

    private BranchListDTO branches(String spaceId, NodeStatus status) {
        Space space = space(spaceId);
        User user = access.currentUser();
        access.resolveAll(user.getId(), space, List.of(), true); // 403 when the user cannot enter the space
        List<Node> all = mongo.find(Query.query(Criteria.where("spaceId").is(spaceId).and("status").is(status)), Node.class);
        Map<String, Integer> sizes = new HashMap<>();
        for (Node n : all) sizes.merge(String.valueOf(n.getTrashRootId()), 1, Integer::sum);

        // shortcut: one access check per branch root (they are few); a restricted ancestor is honoured this way
        Map<String, Access> levels = new HashMap<>();
        List<Node> roots = all.stream().filter(n -> n.getId().equals(n.getTrashRootId()))
                .filter(n -> levels.computeIfAbsent(n.getId(), id -> access.resolveForTrash(user.getId(), n)).compareTo(Access.VIEW) >= 0)
                .sorted(Comparator.comparing(Node::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();

        Set<String> related = new HashSet<>();
        roots.forEach(r -> {
            related.addAll(r.getAncestors());
            if (r.getParentId() != null) related.add(r.getParentId());
        });
        Map<String, Node> byId = related.isEmpty() ? Map.of()
                : nodes.findAllById(related).stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        Map<String, User> actors = status == NodeStatus.TRASHED
                ? users.findAllById(roots.stream().map(Node::getTrashedBy).filter(Objects::nonNull).distinct().toList())
                        .stream().collect(Collectors.toMap(User::getId, Function.identity())) : Map.of();

        List<BranchDTO> out = new ArrayList<>();
        for (Node r : roots) {
            List<NodeDTO.Crumb> path = r.getAncestors().stream().filter(byId::containsKey)
                    .map(a -> new NodeDTO.Crumb(a, byId.get(a).getTitle())).toList();
            Node parent = r.getParentId() == null ? null : byId.get(r.getParentId());
            boolean parentGone = r.getParentId() != null && (parent == null || parent.getStatus() != NodeStatus.ACTIVE);
            UserRef by;
            Date at, purgeAt = null;
            if (status == NodeStatus.TRASHED) {
                User who = actors.get(r.getTrashedBy());
                by = who == null ? null : new UserRef(who.getId(), who.getFullName(), who.getProfilePicture());
                at = r.getTrashedAt();
                if (at != null) purgeAt = new Date(at.getTime() + RETENTION.toMillis());
            } else {
                by = r.getUpdatedBy();
                at = r.getUpdatedAt();
            }
            out.add(new BranchDTO(r.getId(), r.getTitle(), r.getIcon(), path, sizes.get(r.getId()) - 1, by, at, purgeAt, parentGone,
                    levels.get(r.getId()).compareTo(Access.EDIT) >= 0));
        }
        return new BranchListDTO(access.canPurge(user.getId(), space), out);
    }

    // ---------- restore ----------

    public RestoreDTO restore(String trashRootId) {
        return bringBack(trashRootId, NodeStatus.TRASHED);
    }

    public RestoreDTO unarchive(String id) {
        return bringBack(id, NodeStatus.ARCHIVED);
    }

    private RestoreDTO bringBack(String id, NodeStatus from) {
        Node node = nodes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
        User user = access.currentUser();
        if (access.resolveForTrash(user.getId(), node).compareTo(Access.EDIT) < 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page");
        }
        String what = from == NodeStatus.TRASHED ? "trashed" : "archived";
        if (node.getStatus() != from) throw bad("This page is not " + (from == NodeStatus.TRASHED ? "in the trash" : "archived"));
        if (!id.equals(node.getTrashRootId())) {
            throw bad("This page was " + what + " together with another page. Restore that page instead.");
        }

        Node parent = node.getParentId() == null ? null : nodes.findById(node.getParentId()).orElse(null);
        boolean movedToTop = node.getParentId() != null && (parent == null || parent.getStatus() != NodeStatus.ACTIVE);
        if (movedToTop) {
            int oldDepth = node.getAncestors().size();
            List<Node> top = nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(node.getSpaceId(), NodeStatus.ACTIVE, null);
            String rank = RankKeys.between(top.isEmpty() ? null : top.get(top.size() - 1).getRank(), null);
            mongo.updateFirst(byIds(List.of(id)), new Update().unset("parentId").set("ancestors", List.<String>of()).set("rank", rank), Node.class);
            // shortcut: no transaction; a repeat of the restore finds the node already at the top and just flips the status
            mongo.getCollection("nodes").updateMany(new Document("ancestors", new ObjectId(id)), List.of(new Document("$set",
                    new Document("ancestors", new Document("$slice", List.of("$ancestors", oldDepth, 51))))));
        }

        List<String> ids = mongo.find(Query.query(Criteria.where("trashRootId").is(id).and("status").is(from)), Node.class)
                .stream().map(Node::getId).toList();
        for (List<String> chunk : chunks(ids)) {
            mongo.updateMulti(byIds(chunk), new Update().set("status", NodeStatus.ACTIVE)
                    .unset("trashedAt").unset("trashedBy").unset("trashRootId"), Node.class);
            mongo.updateMulti(byIds(chunk), new Update().set("search.status", NodeStatus.ACTIVE), NodeContent.class);
        }
        log(from == NodeStatus.TRASHED ? "RESTORE_NODE" : "UNARCHIVE_NODE", node);
        return new RestoreDTO(movedToTop);
    }

    // ---------- delete forever ----------

    public void deleteForever(String trashRootId) {
        Node node = nodes.findById(trashRootId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
        Space space = space(node.getSpaceId());
        User user = access.currentUser();
        if (!access.canPurge(user.getId(), space)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only team admins can delete pages forever");
        }
        if (node.getStatus() != NodeStatus.TRASHED) throw bad("Move the page to the trash first");
        if (!trashRootId.equals(node.getTrashRootId())) {
            throw bad("This page was trashed together with another page. Delete that page instead.");
        }
        purgeBranch(trashRootId);
        log("PURGE_NODE", node); // after the purge: a failed purge leaves no "purged" row
    }

    /** Deletes every branch trashed before {@code cutoff}; returns how many branches. Used by the daily job. */
    public int purgeExpired(Date cutoff) {
        int purged = 0;
        Set<String> done = new HashSet<>();
        while (true) {
            // status literal + trashedAt: the partial index trashedAt_trashed serves this query. Branches that
            // already failed in this run are left out in the query, or a big failing branch would fill every batch.
            Set<String> roots = mongo.find(Query.query(Criteria.where("status").is(NodeStatus.TRASHED)
                            .and("trashedAt").lt(cutoff).and("trashRootId").nin(done)).limit(BATCH), Node.class).stream()
                    .map(Node::getTrashRootId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (roots.isEmpty()) return purged;
            for (String root : roots) {
                done.add(root); // a root that fails is not retried in this run, so the loop always ends
                try {
                    purgeBranch(root);
                    purged++;
                } catch (RuntimeException e) {
                    log.warn("Purge of trashed branch {} failed, will retry on the next run", root, e);
                }
            }
        }
    }

    /**
     * The ONE place that removes a branch and everything it owns. Dependents go first and the pages last, so a
     * crash in the middle leaves pages that are still trashed and the next run finishes the job.
     */
    private void purgeBranch(String rootId) {
        List<Node> branch = mongo.find(Query.query(Criteria.where("trashRootId").is(rootId)
                .and("status").is(NodeStatus.TRASHED)), Node.class);
        if (branch.isEmpty()) return;
        String spaceId = branch.get(0).getSpaceId();
        for (List<String> chunk : chunks(branch.stream().map(Node::getId).toList())) {
            // shortcut: comments and AI summaries are still keyed by documentId (same value as the node id);
            // switch to nodeId in Task 9.3.
            List<Attachment> files = attachments.findByNodeIdIn(chunk);
            files.forEach(a -> storage.delete(a.getFileId())); // file first: a repeat run still finds the record
            attachments.deleteAll(files);
            comments.deleteByDocumentIdIn(chunk);
            summaries.deleteByDocumentIdIn(chunk);
            mongo.remove(Query.query(Criteria.where("nodeId").in(chunk)), NodeVersion.class); // every kind, RECOVERED included
            mongo.remove(byIds(chunk), EditLock.class);
            mongo.remove(byIds(chunk), NodeContent.class);
            mongo.remove(byIds(chunk), Node.class);
        }
        answers.deleteBySpaceId(spaceId); // saved answers may quote the deleted pages
    }

    // ---------- helpers ----------

    private void log(String action, Node node) {
        Space space = space(node.getSpaceId());
        activity.log(space.getTeamId(), access.currentUser().getId(), action, "NODE", node.getId(), node.getTitle());
    }

    private Space space(String spaceId) {
        return spaces.findById(spaceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
    }

    private static Query byIds(Collection<String> ids) {
        return Query.query(Criteria.where("id").in(ids));
    }

    private static List<List<String>> chunks(List<String> ids) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) out.add(ids.subList(i, Math.min(ids.size(), i + CHUNK)));
        return out;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
