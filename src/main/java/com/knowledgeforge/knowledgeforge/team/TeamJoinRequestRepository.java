package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamJoinRequestRepository extends MongoRepository<TeamJoinRequest,String> {
    boolean existsByTeamIdAndUserIdAndStatus(String teamId, String userId, String status);
    List<TeamJoinRequest> findByTeamIdAndStatus(String teamId, String status);
}
