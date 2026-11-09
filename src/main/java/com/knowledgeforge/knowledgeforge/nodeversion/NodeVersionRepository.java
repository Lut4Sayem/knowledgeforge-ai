package com.knowledgeforge.knowledgeforge.nodeversion;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NodeVersionRepository extends MongoRepository<NodeVersion, String> {
}
