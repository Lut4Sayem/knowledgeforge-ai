package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface AiUsageRepository extends MongoRepository<AiUsage, String> {
    long countByUserIdAndCreatedAtGreaterThanEqual(String userId, Date since);

    // Summed in Java: the site makes only a few dozen AI calls a day.
    List<AiUsage> findByCreatedAtGreaterThanEqual(Date since);
}
