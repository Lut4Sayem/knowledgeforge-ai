package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends MongoRepository<Team, String> {
    List<Team> findByCreatedBy(String createdBy);
    List<Team> findByIdIn(List<String> ids);

}
