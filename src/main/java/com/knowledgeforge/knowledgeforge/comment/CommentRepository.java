package com.knowledgeforge.knowledgeforge.comment;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findByDocumentIdOrderByCreatedAtAsc(String documentId);
}