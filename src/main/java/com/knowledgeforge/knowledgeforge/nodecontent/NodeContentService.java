package com.knowledgeforge.knowledgeforge.nodecontent;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.user.User;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

@Service
public class NodeContentService {
    static final int MAX_STATE = 4 * 1024 * 1024, MAX_TEXT = 1_000_000, MAX_AUTHORS = 100;
    // longest base64 text a 4 MB state can have, so we refuse before decoding
    private static final int MAX_STATE_BASE64 = (MAX_STATE + 2) / 3 * 4;

    private final NodeContentRepository contents;
    private final NodeAccessService access;
    private final MongoTemplate mongo;

    public NodeContentService(NodeContentRepository contents, NodeAccessService access, MongoTemplate mongo) {
        this.contents = contents;
        this.access = access;
        this.mongo = mongo;
    }

    public ContentDTO get(String nodeId) {
        access.requireView(nodeId);
        NodeContent c = contents.findById(nodeId).orElseThrow(NodeContentService::notFound);
        return new ContentDTO(c.getFormat().name(), Base64.getEncoder().encodeToString(c.getState()), c.getRevision(), c.getContentEpoch());
    }

    /** One atomic findAndModify on {_id, revision}: a save from an old revision matches nothing and gets 409. */
    public SavedDTO save(String nodeId, SaveContentRequest req) {
        Node node = access.requireEdit(nodeId);
        User user = access.currentUser();
        if (req.baseRevision() == null) throw bad("baseRevision is required");
        String text = req.text() == null ? "" : req.text();
        byte[] state = decodeState(req.state(), text);

        Date now = new Date();
        ObjectId me = new ObjectId(user.getId());
        // $literal everywhere: in a pipeline a string starting with "$" would be read as a field path
        Document search = new Document("title", lit(node.getTitle())).append("tags", lit(node.getTags()))
                .append("status", lit(node.getStatus().name())).append("publishState", lit(node.getPublishState().name()));
        // shortcut: at 100 authors the union may drop the newest, upgrade when versions need exact attribution
        Document session = new Document("startedAt", new Document("$ifNull", List.of("$editSession.startedAt", now)))
                .append("lastEditAt", now)
                .append("authors", new Document("$slice", List.of(new Document("$setUnion",
                        List.of(new Document("$ifNull", List.of("$editSession.authors", List.of())), List.of(me))), MAX_AUTHORS)));
        Document set = new Document("state", lit(new Binary(state))).append("text", lit(text))
                .append("contentHash", sha256(state)).append("search", search).append("editSession", session)
                .append("updatedAt", now).append("revision", new Document("$add", List.of("$revision", 1)));

        Document saved = mongo.getCollection("node_contents").findOneAndUpdate(
                new Document("_id", new ObjectId(nodeId)).append("revision", req.baseRevision()),
                List.of(new Document("$set", set)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (saved == null) {
            NodeContent current = contents.findById(nodeId).orElseThrow(NodeContentService::notFound);
            throw new StaleRevisionException(current.getRevision());
        }
        mongo.updateFirst(Query.query(Criteria.where("id").is(nodeId)),
                new Update().set("updatedBy", new UserRef(user.getId(), user.getFullName(), user.getProfilePicture()))
                        .set("contentUpdatedAt", now).set("updatedAt", now), Node.class);
        return new SavedDTO(((Number) saved.get("revision")).longValue(), now);
    }

    /** Size caps and base64 check shared by every endpoint that stores page state (400 / 413). */
    public static byte[] decodeState(String base64, String text) {
        if (base64 == null) throw bad("state is required");
        if (text.length() > MAX_TEXT || base64.length() > MAX_STATE_BASE64) throw tooLarge();
        byte[] state;
        try {
            state = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw bad("state is not valid base64");
        }
        if (state.length > MAX_STATE) throw tooLarge();
        return state;
    }

    private static Document lit(Object v) {
        return new Document("$literal", v);
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Page content not found");
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This page is too large to save (limit 4 MB of content, 1 MB of text)");
    }
}
