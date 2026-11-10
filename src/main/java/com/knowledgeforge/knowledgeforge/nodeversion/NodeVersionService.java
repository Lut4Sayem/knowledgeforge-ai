package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.user.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

/** Page versions. Only the recovered copy exists so far; sessions, named versions and restore come in Task 6.1. */
@Service
public class NodeVersionService {
    private final NodeVersionRepository versions;
    private final NodeAccessService access;
    private final MongoTemplate mongo;

    public NodeVersionService(NodeVersionRepository versions, NodeAccessService access, MongoTemplate mongo) {
        this.versions = versions;
        this.access = access;
        this.mongo = mongo;
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
}
