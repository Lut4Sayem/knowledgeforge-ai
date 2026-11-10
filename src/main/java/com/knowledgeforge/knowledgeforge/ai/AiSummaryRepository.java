package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiSummaryRepository extends MongoRepository<AiSummary, String> {
    Optional<AiSummary> findByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);

    void deleteByDocumentIdIn(java.util.Collection<String> documentIds);
}
