package com.knowledgeforge.knowledgeforge.space;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpaceRepository  extends MongoRepository<Space, String> {
    List<Space> findByTeamId(String teamId);
}
