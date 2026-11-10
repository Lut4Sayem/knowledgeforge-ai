package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiAnswerRepository extends MongoRepository<AiAnswer, String> {
    Optional<AiAnswer> findBySpaceIdAndQuestionKey(String spaceId, String questionKey);

    void deleteBySpaceId(String spaceId);
}
