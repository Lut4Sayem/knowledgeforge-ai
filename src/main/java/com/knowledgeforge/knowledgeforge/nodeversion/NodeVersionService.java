package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/** Page versions: sessions closed into AUTO versions, named / published / restored versions, private recovered copies. */
@Service
public class NodeVersionService {
    private static final Logger log = LoggerFactory.getLogger(NodeVersionService.class);

    private final NodeVersionRepository versions;
    private final NodeAccessService access;
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final Clock clock;

    public NodeVersionService(NodeVersionRepository versions, NodeAccessService access, MongoTemplate mongo,
                              UserRepository users, Clock clock) {
        this.versions = versions;
        this.access = access;
        this.mongo = mongo;
        this.users = users;
        this.clock = clock;
    }

    // ---------- sessions ----------

    /** Called by the job: closes every session whose time is up. One failing page does not stop the others. */
    public void closeDueSessions() {
        Instant now = clock.instant();
        // 30 s of quiet is the least any rule needs, so this narrows the scan (index editSession_lastEditAt)
        List<NodeContent> due = mongo.find(Query.query(Criteria.where("editSession.lastEditAt")
                .lte(Date.from(now.minus(VersionRules.PAUSE)))), NodeContent.class);
        for (NodeContent c : due) {
            NodeContent.EditSession s = c.getEditSession();
            if (s == null || !VersionRules.shouldClose(s.getStartedAt().toInstant(), s.getLastEditAt().toInstant(), now)) continue;
            try {
                closeSession(c);
            } catch (RuntimeException e) {
                log.warn("Could not close the edit session of page {}", c.getId(), e);
            }
        }
    }

    /** The page was left: turns the open edit session into a version now (nothing happens when there is none). */
    public void endSession(String nodeId) {
        access.requireEditIgnoringLock(nodeId);
        closeSession(content(nodeId));
    }

    /** AUTO version from the saved content: skipped when unchanged, merged into the latest AUTO or inserted (spec 5.1). */
    void closeSession(NodeContent c) {
        NodeContent.EditSession session = c.getEditSession();
        if (session == null) return;
        NodeVersion next = snapshot(c, VersionKind.AUTO, null, authorsOf(session.getAuthors()));
        NodeVersion latest = latest(c.getId());
        switch (VersionRules.decide(latest, next, clock.instant())) {
            case SKIP -> { }
            case MERGE -> mongo.updateFirst(Query.query(Criteria.where("_id").is(latest.getId())),
                    new Update().set("state", next.getState()).set("text", next.getText()).set("title", next.getTitle())
                            .set("contentHash", next.getContentHash()).set("updatedAt", next.getUpdatedAt()), NodeVersion.class);
            case INSERT -> versions.save(next);
        }
        // clears only the session we read: a save that came in meanwhile keeps its session for the next round
        mongo.getCollection("node_contents").updateOne(new Document("_id", new ObjectId(c.getId()))
                .append("editSession.lastEditAt", session.getLastEditAt()), new Document("$unset", new Document("editSession", "")));
    }

    // ---------- creating versions ----------

    /** Snapshot of the page as saved now, for NAMED and PUBLISHED; ends the edit session. No access check: callers did it. */
    public NodeVersion createVersion(String nodeId, VersionKind kind, String name) {
        NodeContent c = content(nodeId);
        List<UserRef> authors = c.getEditSession() == null ? List.of() : authorsOf(c.getEditSession().getAuthors());
        if (authors.isEmpty()) authors = List.of(me());
        NodeVersion v = versions.save(snapshot(c, kind, name, authors));
        finishSession(c);
        return v;
    }

    /** Named version on request; when nothing changed since the latest AUTO version, that one gets the name instead. */
    public VersionSummaryDTO name(String nodeId, String name) {
        access.requireEdit(nodeId);
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A version needs a name");
        name = name.trim();
        if (name.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name can be at most 100 characters");
        NodeContent c = content(nodeId);
        NodeVersion latest = latest(nodeId);
        if (latest != null && latest.getKind() == VersionKind.AUTO && latest.getContentHash().equals(c.getContentHash())) {
            latest.setKind(VersionKind.NAMED);
            latest.setName(name);
            latest = versions.save(latest);
            finishSession(c);
            return summary(latest);
        }
        return summary(createVersion(nodeId, VersionKind.NAMED, name));
    }

    /**
     * Puts a version's content back. Replaces the state wholesale, so the revision AND the content epoch go up (D6):
     * a client older than this keeps its copy privately instead of merging it back in. The open session is closed
     * first so nothing typed so far is lost, and the restore itself is a new RESTORED version.
     */
    public VersionSummaryDTO restore(String versionId) {
        NodeVersion v = visible(versionId);
        access.requireEdit(v.getNodeId());
        closeSession(content(v.getNodeId()));
        Date now = Date.from(clock.instant());
        mongo.getCollection("node_contents").updateOne(new Document("_id", new ObjectId(v.getNodeId())),
                new Document("$set", new Document("state", new Binary(v.getState())).append("text", v.getText())
                        .append("contentHash", v.getContentHash()).append("updatedAt", now))
                        .append("$inc", new Document("revision", 1L).append("contentEpoch", 1)));
        mongo.updateFirst(Query.query(Criteria.where("id").is(v.getNodeId())),
                new Update().set("updatedBy", me()).set("contentUpdatedAt", now).set("updatedAt", now), Node.class);
        return summary(versions.save(snapshot(content(v.getNodeId()), VersionKind.RESTORED, null, List.of(me()))));
    }

    // ---------- reading ----------

    public List<VersionSummaryDTO> list(String nodeId) {
        access.requireView(nodeId);
        Query q = Query.query(Criteria.where("nodeId").is(nodeId).and("privateTo").in(null, access.currentUser().getId()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        q.fields().exclude("state", "text");
        return mongo.find(q, NodeVersion.class).stream().map(NodeVersionService::summary).toList();
    }

    public VersionDetailDTO get(String versionId) {
        NodeVersion v = visible(versionId);
        return new VersionDetailDTO(v.getId(), v.getKind(), v.getName(), v.getAuthors(), v.getCreatedAt(), v.getUpdatedAt(),
                v.getPrivateTo() != null, Base64.getEncoder().encodeToString(v.getState()), v.getText());
    }

    /**
     * Keeps a client's unsaved copy that must not be merged into the page (it was based on an older content
     * epoch) as a version only that user can see. View access is enough: the page itself is not changed.
     * Sending the same copy again returns the first one, so a client may retry safely.
     */
    public RecoveredVersionDTO saveRecovered(String nodeId, RecoveredVersionRequest req) {
        Node node = access.requireView(nodeId);
        User user = access.currentUser();
        if (req.baseEpoch() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseEpoch is required");
        String text = req.text() == null ? "" : req.text();
        byte[] state = NodeContentService.decodeState(req.state(), text);
        String hash = NodeContentService.sha256(state);

        NodeVersion same = mongo.findOne(Query.query(Criteria.where("nodeId").is(nodeId).and("privateTo").is(user.getId())
                .and("kind").is(VersionKind.RECOVERED).and("contentHash").is(hash)), NodeVersion.class);
        if (same != null) return new RecoveredVersionDTO(same.getId(), same.getCreatedAt());

        Date now = new Date();
        NodeVersion v = new NodeVersion();
        v.setNodeId(nodeId);
        v.setSpaceId(node.getSpaceId());
        v.setKind(VersionKind.RECOVERED);
        v.setTitle(node.getTitle());
        v.setState(state);
        v.setText(text);
        v.setContentHash(hash);
        v.setAuthors(List.of(new UserRef(user.getId(), user.getFullName(), user.getProfilePicture())));
        v.setPrivateTo(user.getId());
        v.setBaseEpoch(req.baseEpoch());
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        v = versions.save(v);
        return new RecoveredVersionDTO(v.getId(), v.getCreatedAt());
    }

    // ---------- helpers ----------

    private NodeContent content(String nodeId) {
        NodeContent c = mongo.findById(nodeId, NodeContent.class);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page content not found");
        return c;
    }

    /** A version the caller may see: view access to the page, and not someone else's private one (404, not 403). */
    private NodeVersion visible(String versionId) {
        NodeVersion v = versions.findById(versionId).orElseThrow(NodeVersionService::versionNotFound);
        access.requireView(v.getNodeId());
        if (v.getPrivateTo() != null && !v.getPrivateTo().equals(access.currentUser().getId())) throw versionNotFound();
        return v;
    }

    private static ResponseStatusException versionNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found");
    }

    /** Newest shared version; recovered copies are private and never count (merge rule, hash check, naming). */
    private NodeVersion latest(String nodeId) {
        return mongo.findOne(Query.query(Criteria.where("nodeId").is(nodeId).and("kind").ne(VersionKind.RECOVERED))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(1), NodeVersion.class);
    }

    private NodeVersion snapshot(NodeContent c, VersionKind kind, String name, List<UserRef> authors) {
        Node node = mongo.findById(c.getId(), Node.class);
        Date now = Date.from(clock.instant());
        NodeVersion v = new NodeVersion();
        v.setNodeId(c.getId());
        v.setSpaceId(c.getSpaceId());
        v.setKind(kind);
        v.setName(name);
        v.setTitle(node == null ? "" : node.getTitle());
        v.setState(c.getState());
        v.setText(c.getText());
        v.setContentHash(c.getContentHash());
        v.setAuthors(authors);
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        return v;
    }

    /** Ends the session (unless a save came in since {@code c} was read) and sets the node's contentUpdatedAt. */
    private void finishSession(NodeContent c) {
        mongo.getCollection("node_contents").updateOne(new Document("_id", new ObjectId(c.getId()))
                .append("revision", c.getRevision()), new Document("$unset", new Document("editSession", "")));
        mongo.updateFirst(Query.query(Criteria.where("id").is(c.getId())),
                new Update().set("contentUpdatedAt", c.getUpdatedAt()), Node.class);
    }

    private List<UserRef> authorsOf(List<String> ids) {
        List<UserRef> out = users.findAllById(ids).stream()
                .map(u -> new UserRef(u.getId(), u.getFullName(), u.getProfilePicture())).toList();
        return out;
    }

    private UserRef me() {
        User u = access.currentUser();
        return new UserRef(u.getId(), u.getFullName(), u.getProfilePicture());
    }

    private static VersionSummaryDTO summary(NodeVersion v) {
        return new VersionSummaryDTO(v.getId(), v.getKind(), v.getName(), v.getAuthors(), v.getCreatedAt(), v.getUpdatedAt(),
                v.getPrivateTo() != null);
    }
}
