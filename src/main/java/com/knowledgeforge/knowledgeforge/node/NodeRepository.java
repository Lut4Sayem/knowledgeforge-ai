package com.knowledgeforge.knowledgeforge.node;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NodeRepository extends MongoRepository<Node, String> {
}
