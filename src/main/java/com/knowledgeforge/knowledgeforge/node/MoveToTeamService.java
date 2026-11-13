package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersionService;
import com.knowledgeforge.knowledgeforge.nodeversion.VersionKind;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.user.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/** Moves a private page (with its sub-pages) into a team space, one way (domain spec 4.4, decision D2). */
@Service
public class MoveToTeamService {
    private final NodeRepository nodes;
    private final NodeAccessService access;
    private final NodeService nodeService;
    private final NodeVersionService versions;
    private final MongoTemplate mongo;

    public MoveToTeamService(NodeRepository nodes, NodeAccessService access, NodeService nodeService,
                             NodeVersionService versions, MongoTemplate mongo) {
        this.nodes = nodes;
        this.access = access;
        this.nodeService = nodeService;
        this.versions = versions;
        this.mongo = mongo;
    }

    public NodeDTO moveToTeam(String id, MoveToTeamRequest req) {
        if (req.spaceId() == null) throw NodeService.bad("Choose a team project");
        Node node = access.requireEdit(id);
        User user = access.currentUser();
        Space dest = nodeService.space(req.spaceId());
        if (dest.getOwnerType() == Space.OwnerType.USER) throw NodeService.bad("Copy it instead");
        if (nodeService.space(node.getSpaceId()).getOwnerType() != Space.OwnerType.USER) {
            throw NodeService.bad("Only private pages can be moved to a team project");
        }
        if (!access.canEditSpace(user.getId(), dest)) throw NodeService.forbidden("You can't edit this space");
        Node parent = null;
        if (req.parentId() != null) {
            parent = access.requireEdit(req.parentId());
            if (!dest.getId().equals(parent.getSpaceId())) throw NodeService.bad("That page is in another space");
        }
        List<String> newAncestors = NodeService.ancestorsUnder(parent);
        int oldDepth = node.getAncestors().size();
        List<Node> descendants = nodes.findByAncestors(id);
        int deepest = descendants.stream().mapToInt(d -> d.getAncestors().size() - oldDepth).max().orElse(0);
        if (newAncestors.size() + deepest > NodeService.MAX_DEPTH) {
            throw NodeService.bad("Pages can't be nested deeper than " + NodeService.MAX_DEPTH + " levels");
        }
        String parentId = parent == null ? null : parent.getId();
        List<Node> siblings = nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(dest.getId(), NodeStatus.ACTIVE, parentId);
        String rank = nodeService.rank(siblings, req.beforeId(), req.afterId());

        List<String> ids = Stream.concat(Stream.of(id), descendants.stream().map(Node::getId)).toList();
        List<ObjectId> oids = ids.stream().map(ObjectId::new).toList();
        ObjectId me = new ObjectId(user.getId()), destId = new ObjectId(dest.getId());

        // shortcut: no transaction. The node itself moves last, so a failure earlier leaves it private and the steps can be repeated
        // (a repeat adds a second PUBLISHED_TO_TEAM version).
        mongo.getCollection("node_versions").updateMany(new Document("nodeId", new Document("$in", oids)),
                new Document("$set", new Document("privateTo", me).append("spaceId", destId)));
        // comments are still keyed by documentId (= node id) until Task 9.3, so that is the key here
        mongo.getCollection("comments").updateMany(new Document("documentId", new Document("$in", ids)),
                new Document("$set", new Document("privateTo", me)));
        mongo.getCollection("node_contents").updateMany(new Document("_id", new Document("$in", oids)),
                new Document("$set", new Document("spaceId", destId)));
        for (String nid : ids) versions.createVersion(nid, VersionKind.PUBLISHED_TO_TEAM, null);

        if (!descendants.isEmpty()) {
            List<ObjectId> prefix = newAncestors.stream().map(ObjectId::new).toList();
            mongo.getCollection("nodes").updateMany(new Document("ancestors", new ObjectId(id)), List.of(new Document("$set",
                    new Document("spaceId", destId).append("restriction", null).append("ancestors",
                            new Document("$concatArrays", List.of(prefix, new Document("$slice",
                                    List.of("$ancestors", oldDepth, NodeService.MAX_DEPTH + 1))))))));
        }
        node.setSpaceId(dest.getId());
        node.setParentId(parentId);
        node.setAncestors(newAncestors);
        node.setRank(rank);
        node.setRestriction(null);
        node.setUpdatedAt(new Date());
        nodes.save(node);
        return nodeService.get(id);
    }
}
