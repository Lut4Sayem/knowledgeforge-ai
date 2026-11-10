package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentRepository;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NodeService {
    static final int MAX_DEPTH = 50, MAX_TITLE = 200, MAX_ICON = 16, MAX_TAGS = 20, MAX_TAG = 40, MAX_RECENT = 20;

    private final NodeRepository nodes;
    private final SpaceRepository spaces;
    private final NodeContentRepository contents;
    private final NodeAccessService access;
    private final TeamMemberRepository members;
    private final MongoTemplate mongo;

    public NodeService(NodeRepository nodes, SpaceRepository spaces, NodeContentRepository contents,
                       NodeAccessService access, TeamMemberRepository members, MongoTemplate mongo) {
        this.nodes = nodes;
        this.spaces = spaces;
        this.contents = contents;
        this.access = access;
        this.members = members;
        this.mongo = mongo;
    }

    // ---------- reads ----------

    /** ACTIVE pages the user can at least see the title of, ordered by parent then rank. */
    public List<NodeSummaryDTO> tree(String spaceId) {
        Space space = space(spaceId);
        List<Node> all = nodes.findBySpaceIdAndStatusOrderByParentIdAscRankAsc(spaceId, NodeStatus.ACTIVE);
        Map<String, Access> acc = access.resolveAll(access.currentUser().getId(), space, all);
        return all.stream().filter(n -> acc.get(n.getId()).compareTo(Access.TITLE_ONLY) >= 0)
                .map(n -> new NodeSummaryDTO(n.getId(), n.getParentId(), n.getRank(), n.getTitle(), n.getIcon(),
                        n.getType(), n.getPublishState(), n.isLocked(), acc.get(n.getId()) == Access.TITLE_ONLY))
                .toList();
    }

    public NodeDTO get(String id) {
        Node node = access.requireView(id);
        User user = access.currentUser();
        pushRecent(user.getId(), id);
        return toDto(node, user);
    }

    // ---------- writes ----------

    public NodeDTO create(String spaceId, CreateNodeRequest req) {
        Space space = space(spaceId);
        User user = access.currentUser();
        Node parent = null;
        if (req.parentId() == null) {
            if (!access.canEditSpace(user.getId(), space)) throw forbidden("You can't add pages to this space");
        } else {
            parent = access.requireEdit(req.parentId());
            if (!spaceId.equals(parent.getSpaceId())) throw bad("That page is in another space");
        }
        String title = title(req.title() == null ? "" : req.title());
        List<String> ancestors = ancestorsUnder(parent);
        if (ancestors.size() > MAX_DEPTH) throw bad("Pages can't be nested deeper than " + MAX_DEPTH + " levels");

        List<Node> siblings = nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(
                spaceId, NodeStatus.ACTIVE, parent == null ? null : parent.getId());
        Date now = new Date();
        Node node = new Node();
        node.setSpaceId(spaceId);
        node.setParentId(parent == null ? null : parent.getId());
        node.setAncestors(ancestors);
        node.setRank(rank(siblings, null, req.afterId()));
        node.setTitle(title);
        node.setCreatedBy(ref(user));
        node.setUpdatedBy(ref(user));
        node.setContentUpdatedAt(now);
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        node = nodes.save(node);

        NodeContent content = new NodeContent();
        content.setId(node.getId());
        content.setSpaceId(spaceId);
        content.getSearch().setTitle(title);
        content.setUpdatedAt(now);
        // shortcut: no transaction, if the second save fails the page is removed again by hand-rolled cleanup
        try {
            contents.save(content);
        } catch (RuntimeException e) {
            nodes.deleteById(node.getId());
            throw e;
        }
        return toDto(node, user);
    }

    public NodeDTO update(String id, UpdateNodeRequest req) {
        Node node = access.requireEdit(id);
        User user = access.currentUser();
        if (req.title() != null) node.setTitle(title(req.title()));
        if (req.icon() != null) {
            String icon = req.icon().trim();
            if (icon.codePointCount(0, icon.length()) > MAX_ICON) throw bad("Icon is too long");
            node.setIcon(icon.isEmpty() ? null : icon);
        }
        if (req.tags() != null) node.setTags(normalizeTags(req.tags()));
        node.setUpdatedBy(ref(user));
        node.setUpdatedAt(new Date());
        node = nodes.save(node);
        syncSearchFields(node);
        return toDto(node, user);
    }

    /** Keeps the copy of title/tags/status/publishState in node_contents.search current (call after any of them change). */
    public void syncSearchFields(Node node) {
        NodeContent.Search s = new NodeContent.Search();
        s.setTitle(node.getTitle());
        s.setTags(node.getTags());
        s.setStatus(node.getStatus());
        s.setPublishState(node.getPublishState());
        mongo.updateFirst(Query.query(Criteria.where("id").is(node.getId())), new Update().set("search", s), NodeContent.class);
    }

    public NodeDTO move(String id, MoveNodeRequest req) {
        Node node = access.requireEdit(id);
        User user = access.currentUser();
        Node parent = null;
        if (req.parentId() == null) {
            if (!access.canEditSpace(user.getId(), space(node.getSpaceId()))) throw forbidden("You can't edit this space");
        } else {
            if (req.parentId().equals(id)) throw bad("Can't move a page inside itself");
            parent = access.requireEdit(req.parentId());
            if (!node.getSpaceId().equals(parent.getSpaceId())) throw bad("Pages can only be moved within their space");
            if (parent.getAncestors().contains(id)) throw bad("Can't move a page inside itself");
        }
        List<String> newAncestors = ancestorsUnder(parent);
        int oldDepth = node.getAncestors().size();
        List<Node> descendants = nodes.findByAncestors(id);
        int deepest = descendants.stream().mapToInt(d -> d.getAncestors().size() - oldDepth).max().orElse(0);
        if (newAncestors.size() + deepest > MAX_DEPTH) throw bad("Pages can't be nested deeper than " + MAX_DEPTH + " levels");

        String parentId = parent == null ? null : parent.getId();
        List<Node> siblings = nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(node.getSpaceId(), NodeStatus.ACTIVE, parentId)
                .stream().filter(s -> !s.getId().equals(id)).collect(Collectors.toCollection(ArrayList::new));
        node.setParentId(parentId);
        node.setAncestors(newAncestors);
        node.setRank(rank(siblings, req.beforeId(), req.afterId()));
        node.setUpdatedBy(ref(user));
        node.setUpdatedAt(new Date());
        node = nodes.save(node);

        // shortcut: no transaction; if this fails the node is moved but descendants keep old ancestors, repeating the move repairs them
        if (!descendants.isEmpty()) {
            List<ObjectId> prefix = newAncestors.stream().map(ObjectId::new).toList();
            mongo.getCollection("nodes").updateMany(new Document("ancestors", new ObjectId(id)), List.of(new Document("$set",
                    new Document("ancestors", new Document("$concatArrays", List.of(prefix,
                            new Document("$slice", List.of("$ancestors", oldDepth, MAX_DEPTH + 1))))))));
        }
        return toDto(node, user);
    }

    /** Only flips the state; the PUBLISHED version entry comes with Task 6.1. */
    public NodeDTO publish(String id) {
        return setPublishState(id, PublishState.PUBLISHED);
    }

    public NodeDTO unpublish(String id) {
        return setPublishState(id, PublishState.DRAFT);
    }

    private NodeDTO setPublishState(String id, PublishState state) {
        Node node = access.requireEdit(id);
        User user = access.currentUser();
        node.setPublishState(state);
        node.setUpdatedBy(ref(user));
        node.setUpdatedAt(new Date());
        node = nodes.save(node);
        syncSearchFields(node);
        return toDto(node, user);
    }

    /** Page editors only. Both ignore the lock itself, so an editor can unlock (and re-locking is harmless). */
    public NodeDTO lock(String id) {
        return setLocked(id, true);
    }

    public NodeDTO unlock(String id) {
        return setLocked(id, false);
    }

    private NodeDTO setLocked(String id, boolean locked) {
        Node node = access.requireEditIgnoringLock(id);
        User user = access.currentUser();
        node.setLocked(locked);
        node.setLockedBy(locked ? user.getId() : null);
        node.setUpdatedBy(ref(user));
        node.setUpdatedAt(new Date());
        return toDto(nodes.save(node), user);
    }

    /** ACTIVE pages with this tag in every space I can enter, newest first, only those I can at least view. */
    public List<TaggedNodeDTO> taggedPages(String rawTag) {
        String tag = rawTag.trim().toLowerCase(Locale.ROOT);
        User user = access.currentUser();
        List<String> teamIds = members.findByUserId(user.getId()).stream().map(TeamMember::getTeamId).toList();
        Map<String, Space> mine = new java.util.LinkedHashMap<>();
        if (!teamIds.isEmpty()) spaces.findByTeamIdIn(teamIds).forEach(s -> mine.put(s.getId(), s));
        spaces.findByOwnerUserId(user.getId()).ifPresent(s -> mine.put(s.getId(), s));
        if (mine.isEmpty() || tag.isEmpty()) return List.of();

        Query q = Query.query(Criteria.where("tags").is(tag).and("status").is(NodeStatus.ACTIVE)
                .and("spaceId").in(mine.keySet())).limit(200);
        // shortcut: one access check per hit (capped at 200 hits); needed so restrictions on ancestors are honoured
        return mongo.find(q, Node.class).stream()
                .filter(n -> access.resolve(user.getId(), n).compareTo(Access.VIEW) >= 0)
                .sorted(java.util.Comparator.comparing(Node::getContentUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(n -> new TaggedNodeDTO(n.getId(), n.getSpaceId(), mine.get(n.getSpaceId()).getName(), n.getTitle(),
                        n.getIcon(), n.getTags(), n.getPublishState()))
                .toList();
    }

    // ---------- helpers ----------

    private Space space(String spaceId) {
        return spaces.findById(spaceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));
    }

    private static List<String> ancestorsUnder(Node parent) {
        if (parent == null) return new ArrayList<>();
        List<String> a = new ArrayList<>(parent.getAncestors());
        a.add(parent.getId());
        return a;
    }

    private static String title(String raw) {
        String t = raw.trim();
        if (t.codePointCount(0, t.length()) > MAX_TITLE) throw bad("Title can be at most " + MAX_TITLE + " characters");
        return t;
    }

    private static List<String> normalizeTags(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            if (r == null) continue;
            String t = r.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.codePointCount(0, t.length()) > MAX_TAG) throw bad("A tag can be at most " + MAX_TAG + " characters");
            out.add(t);
        }
        if (out.size() > MAX_TAGS) throw bad("A page can have at most " + MAX_TAGS + " tags");
        return new ArrayList<>(out);
    }

    /** Rank for a node placed before/after a sibling (or at the end). {@code siblings} is sorted and excludes the node. */
    private String rank(List<Node> siblings, String beforeId, String afterId) {
        try {
            return placeAmong(siblings, beforeId, afterId);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // keys ran out of room (or two siblings share a key): spread the siblings out and try once more
            List<String> fresh = RankKeys.spread(siblings.size());
            for (int i = 0; i < siblings.size(); i++) siblings.get(i).setRank(fresh.get(i));
            nodes.saveAll(siblings);
            return placeAmong(siblings, beforeId, afterId);
        }
    }

    private static String placeAmong(List<Node> siblings, String beforeId, String afterId) {
        String id = beforeId != null ? beforeId : afterId;
        if (id == null) return RankKeys.between(siblings.isEmpty() ? null : siblings.get(siblings.size() - 1).getRank(), null);
        int i = -1;
        for (int k = 0; k < siblings.size(); k++) if (siblings.get(k).getId().equals(id)) i = k;
        if (i < 0) throw bad("That sibling page is not in the destination");
        if (beforeId != null) return RankKeys.between(i > 0 ? siblings.get(i - 1).getRank() : null, siblings.get(i).getRank());
        return RankKeys.between(siblings.get(i).getRank(), i + 1 < siblings.size() ? siblings.get(i + 1).getRank() : null);
    }

    private void pushRecent(String userId, String nodeId) {
        Query q = Query.query(Criteria.where("id").is(userId));
        ObjectId oid = new ObjectId(nodeId);
        mongo.updateFirst(q, new Update().pull("recentNodes", new Document("nodeId", oid)), User.class);
        mongo.updateFirst(q, new Update().push("recentNodes").slice(-MAX_RECENT)
                .each(new Document("nodeId", oid).append("at", new Date())), User.class);
    }

    private NodeDTO toDto(Node n, User user) {
        Map<String, Node> up = n.getAncestors().isEmpty() ? Map.of()
                : nodes.findAllById(n.getAncestors()).stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        List<NodeDTO.Crumb> crumbs = n.getAncestors().stream().filter(up::containsKey)
                .map(a -> new NodeDTO.Crumb(a, up.get(a).getTitle())).toList();
        return new NodeDTO(n.getId(), n.getSpaceId(), n.getParentId(), n.getTitle(), n.getIcon(), n.getTags(),
                n.getPublishState(), n.isLocked(), n.getLockedBy(), n.getStatus(), access.resolve(user.getId(), n),
                access.resolveIgnoringState(user.getId(), n).compareTo(Access.EDIT) >= 0,
                crumbs, n.getCreatedBy(), n.getUpdatedBy(), n.getContentUpdatedAt());
    }

    private static UserRef ref(User u) {
        return new UserRef(u.getId(), u.getFullName(), u.getProfilePicture());
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException forbidden(String m) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, m);
    }
}
