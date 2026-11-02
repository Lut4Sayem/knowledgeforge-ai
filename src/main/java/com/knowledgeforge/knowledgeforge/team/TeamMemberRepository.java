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

    // ADMIN and EDITOR can write content; VIEWER is read-only.
    default boolean canEdit(String teamId, String userId) {
        return findByTeamIdAndUserId(teamId, userId)
                .map(m -> "ADMIN".equals(m.getRole()) || "EDITOR".equals(m.getRole()))
                .orElse(false);
    }

}
