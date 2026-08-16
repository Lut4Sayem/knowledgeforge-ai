package com.knowledgeforge.knowledgeforge.space;

import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Date;
import java.util.Optional;


@Service
public class SpaceService {
    final TeamMemberRepository teamMemberRepository;
    final UserRepository userRepository;
    final SpaceRepository spaceRepository;
    public SpaceService(TeamMemberRepository teamMemberRepository, SpaceRepository spaceRepository, UserRepository userRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
    }

    public Space createSpace(String teamId, CreateSpaceRequest createSpaceRequest) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userEmail= userRepository.findByEmail(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String userId = user.getId();
        boolean exists = teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);

        if(!exists){
            throw new RuntimeException("User or Team Not Found");
        }

        Space space = new Space();
        space.setName(createSpaceRequest.getName());
        space.setDescription(createSpaceRequest.getDescription());
        space.setTeamId(teamId);
        space.setCreatedAt(new Date());
        space.setCreatedBy(userId);
        space.setUpdatedAt(new Date());
        return spaceRepository.save(space);

    }

}
