package com.knowledgeforge.knowledgeforge.documentVersion;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends MongoRepository<DocumentVersion, String> {

    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(String documentId);

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(String documentId);
}