package com.knowledgeforge.knowledgeforge.node;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NodeRepository extends MongoRepository<Node, String> {
    /** Uses index spaceId_status_parentId_rank. */
    List<Node> findBySpaceIdAndStatusOrderByParentIdAscRankAsc(String spaceId, NodeStatus status);

    /** Siblings in order; parentId null = top level. Uses index spaceId_status_parentId_rank. */
    List<Node> findBySpaceIdAndStatusAndParentIdOrderByRankAsc(String spaceId, NodeStatus status, String parentId);

    /** Newest pages across spaces, for the dashboard. Uses index spaceId_status_contentUpdatedAt. */
    List<Node> findTop30BySpaceIdInAndStatusOrderByContentUpdatedAtDesc(java.util.Collection<String> spaceIds, NodeStatus status);

    long countBySpaceIdAndStatus(String spaceId, NodeStatus status);

    /** All descendants of a node (any status). Uses index ancestors. */
    List<Node> findByAncestors(String ancestorId);
}
