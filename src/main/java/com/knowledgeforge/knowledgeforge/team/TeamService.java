package com.knowledgeforge.knowledgeforge.team;

import com.knowledgeforge.knowledgeforge.user.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.knowledgeforge.knowledgeforge.user.UserRepository;

import java.util.Date;
import java.util.Optional;

@Service
public class TeamService {

    final TeamRepository teamRepository;
    final UserRepository userRepository;
    final TeamMemberRepository teamMemberRepository;
    public TeamService(TeamRepository teamRepository, UserRepository userRepository, TeamMemberRepository teamMemberRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    public Team createTeam(String name, String description) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<User> userOpt = userRepository.findByEmail(email);

        boolean emailNotInDB = userOpt.isEmpty();

        if(emailNotInDB){
            throw new RuntimeException("User Not Found");
        }
        String userId = userOpt.get().getId();
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setCreatedAt(new Date());
        team.setCreatedBy(userId);
        team.setUpdatedAt(new Date());
        teamRepository.save(team);

        TeamMember teamMember = new TeamMember();
        teamMember.setTeamId(team.getId());
        teamMember.setRole("ADMIN");
        teamMember.setUserId(userId);
        teamMember.setJoinedAt(new Date());
        teamMemberRepository.save(teamMember);

        return team;


    }


}
