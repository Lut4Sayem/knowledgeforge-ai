package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends MongoRepository<TeamMember, String> {
    List<TeamMember> findByTeamId(String teamId);
    Optional<TeamMember> findByTeamIdAndUserId(String teamId, String userId);
    List<TeamMember> findByUserId(String userId);
    boolean existsByTeamIdAndUserId(String teamId, String userId);

}
