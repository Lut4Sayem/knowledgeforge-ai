package com.knowledgeforge.knowledgeforge.attachment;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AttachmentRepository extends MongoRepository<Attachment, String> {
    List<Attachment> findByDocumentId(String documentId);
}