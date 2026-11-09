package com.knowledgeforge.knowledgeforge.editlock;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EditLockRepository extends MongoRepository<EditLock, String> {
}
