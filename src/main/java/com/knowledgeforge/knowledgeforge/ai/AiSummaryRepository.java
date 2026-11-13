package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiSummaryRepository extends MongoRepository<AiSummary, String> {
    Optional<AiSummary> findByNodeId(String nodeId);

    void deleteByNodeId(String nodeId);

    void deleteByNodeIdIn(java.util.Collection<String> nodeIds);
}
