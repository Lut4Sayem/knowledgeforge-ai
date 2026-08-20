package com.knowledgeforge.knowledgeforge.documentPkg;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {
    List<DocumentEntity> findBySpaceId(String spaceId);
    List<DocumentEntity> findBySpaceIdIn(List<String> spaceIds);
}
