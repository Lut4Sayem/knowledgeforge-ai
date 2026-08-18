package com.knowledgeforge.knowledgeforge.team;

import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class TeamInviteService {
    final TeamInviteRepository teamInviteRepository;
    final UserRepository userRepository;
    final TeamMemberRepository teamMemberRepository;
    public TeamInviteService(UserRepository userRepository, TeamMemberRepository teamMemberRepository,TeamInviteRepository teamInviteRepository) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInviteRepository = teamInviteRepository;
    }

    public TeamInvite createTeamInvite(String teamId){
        String email= SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String userId = user.getId();
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new RuntimeException("User is not part of the team"));
        if(!(Objects.equals(member.getRole(), "ADMIN"))){
            throw new RuntimeException("You are not allowed to invite a team member");
        }
        TeamInvite teamInvite = new TeamInvite();
        teamInvite.setTeamId(teamId);
        teamInvite.setCreatedAt(new Date());
        teamInvite.setCreatedBy(userId);

        String code = UUID.randomUUID().toString();
        teamInvite.setInviteCode(code);

        return  teamInviteRepository.save(teamInvite);
    }
}
