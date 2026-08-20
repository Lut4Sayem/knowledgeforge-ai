package com.knowledgeforge.knowledgeforge.activity;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {
    List<ActivityLog> findTop20ByTeamIdInOrderByCreatedAtDesc(List<String> teamIds);
}