package com.knowledgeforge.knowledgeforge.migration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MigrationRecordRepository extends MongoRepository<MigrationRecord, String> {
}
