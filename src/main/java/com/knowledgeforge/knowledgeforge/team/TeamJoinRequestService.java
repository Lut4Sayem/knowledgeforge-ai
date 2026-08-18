package com.knowledgeforge.knowledgeforge.team;

import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class TeamJoinRequestService {
    final TeamJoinRequestRepository teamJoinRequestRepository;
    final  UserRepository userRepository;
    final TeamMemberRepository teamMemberRepository;
    final TeamInviteRepository teamInviteRepository;
    private final TeamRepository teamRepository;

    public TeamJoinRequestService(TeamJoinRequestRepository teamJoinRequestRepository, TeamMemberRepository teamMemberRepository, UserRepository userRepository, TeamInviteRepository teamInviteRepository, TeamRepository teamRepository) {
        this.teamJoinRequestRepository = teamJoinRequestRepository;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInviteRepository = teamInviteRepository;
        this.teamRepository = teamRepository;
    }

    public TeamJoinRequest requestToJoin(String inviteCode) {
        TeamInvite teamInvite = teamInviteRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new RuntimeException("Invite code not found"));
        String teamId = teamInvite.getTeamId();
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String userId= user.getId();
        boolean exists= teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
        if(exists){
            throw new RuntimeException("You are already a member of this team");
        }
        boolean pending= teamJoinRequestRepository.existsByTeamIdAndUserIdAndStatus(teamId, userId, "PENDING");
        if(pending){
            throw new RuntimeException("You are already invited");
        }
        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);
        teamJoinRequest.setUserId(userId);
        teamJoinRequest.setStatus("PENDING");
        teamJoinRequest.setRequestedAt(new Date());
        return teamJoinRequestRepository.save(teamJoinRequest);
    }

    public List<TeamJoinRequestDTO> getPendingRequests(String teamId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User adminUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TeamMember teamMember = teamMemberRepository.findByTeamIdAndUserId(teamId, adminUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this team"));

        if (!teamMember.getRole().equals("ADMIN")) {
            throw new RuntimeException("You are not allowed to view join requests");
        }

        List<TeamJoinRequest> pending = teamJoinRequestRepository.findByTeamIdAndStatus(teamId, "PENDING");

        List<TeamJoinRequestDTO> result = new ArrayList<>();

        for (TeamJoinRequest req : pending) {
            TeamJoinRequestDTO dto = new TeamJoinRequestDTO();
            dto.setId(req.getId());
            dto.setStatus(req.getStatus());
            dto.setRequestedAt(req.getRequestedAt());

            User requester = userRepository.findById(req.getUserId()).orElse(null);
            if (requester != null) {
                dto.setFullName(requester.getFullName());
                dto.setEmail(requester.getEmail());
            } else {
                dto.setFullName("(unknown)");
                dto.setEmail("");
            }

            result.add(dto);
        }

        return result;
    }


    public TeamJoinRequest acceptRequest(String teamId, String requestId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isEmpty()){throw new RuntimeException("User not found");}
        Optional<TeamMember> teamMember= teamMemberRepository.findByTeamIdAndUserId(teamId,user.get().getId());
        String role= teamMember.get().getRole();
        if(!role.equals("ADMIN")){
            throw new RuntimeException("You are not allowed to accept this team");
        }
        TeamJoinRequest teamJoinRequest = teamJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        String status= teamJoinRequest.getStatus();
        if(!status.equals("PENDING")){
            throw new RuntimeException("The request is not pending anymore");
        }
        teamJoinRequest.setStatus("ACCEPTED");
        TeamMember teamMember1 = new TeamMember();
        teamMember1.setTeamId(teamId);
        teamMember1.setUserId(teamJoinRequest.getUserId());
        teamMember1.setRole("VIEWER");
        teamMember1.setJoinedAt(new Date());
        teamMemberRepository.save(teamMember1);
        return  teamJoinRequestRepository.save(teamJoinRequest);


    }

    public TeamJoinRequest rejectRequest(String teamId, String requestId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isEmpty()){throw new RuntimeException("User not found");}
        Optional<TeamMember> teamMember= teamMemberRepository.findByTeamIdAndUserId(teamId,user.get().getId());
        String role= teamMember.get().getRole();
        if(!role.equals("ADMIN")){
            throw new RuntimeException("You are not allowed to accept this team");
        }
        TeamJoinRequest teamJoinRequest = teamJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        String status= teamJoinRequest.getStatus();
        if(!status.equals("PENDING")){
            throw new RuntimeException("The request is not pending anymore");
        }
        teamJoinRequest.setStatus("REJECTED");

        return teamJoinRequestRepository.save(teamJoinRequest);
    }
}
