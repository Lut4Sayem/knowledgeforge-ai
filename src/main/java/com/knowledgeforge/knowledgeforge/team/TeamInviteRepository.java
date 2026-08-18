package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface TeamInviteRepository extends MongoRepository<TeamInvite, String> {
    Optional<TeamInvite> findByInviteCode(String inviteCode);
}
