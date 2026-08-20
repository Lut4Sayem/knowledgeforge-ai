package com.knowledgeforge.knowledgeforge.team;

import com.knowledgeforge.knowledgeforge.user.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.knowledgeforge.knowledgeforge.user.UserRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    public List<Team> getTeams(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        boolean emailNotInDB = userOpt.isEmpty();
        if(emailNotInDB){
            throw new RuntimeException("User Not Found");
        }
        String userId = userOpt.get().getId();
        List<TeamMember> teamMembers = teamMemberRepository.findByUserId(userId);
        List<String> teamIds = new ArrayList<>();
        for (TeamMember teamMember : teamMembers) {
            teamIds.add(teamMember.getTeamId());
        }
        List<Team> teams = teamRepository.findByIdIn(teamIds);
        return teams;
    }


    public TeamDetailsResponseDTO getTeamDetails(String teamId) {
        Optional<Team> teamOpt = teamRepository.findById(teamId);
        if (teamOpt.isEmpty()){
            throw new RuntimeException("Team Not Found");
        }
        Team team = teamOpt.get();
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamId(team.getId());
        List<TeamMemberDTO> smallMemberDTOs = new ArrayList<>();
        for (TeamMember teamMember : teamMembers) {
            Optional<User> userOpt = userRepository.findById(teamMember.getUserId());
            if (userOpt.isPresent()){
                User user = userOpt.get();
                TeamMemberDTO MemberDTO = new TeamMemberDTO();
                MemberDTO.setFullName(user.getFullName());
                MemberDTO.setEmail(user.getEmail());
                MemberDTO.setRole(teamMember.getRole());
                MemberDTO.setUserId(user.getId());
                smallMemberDTOs.add(MemberDTO);
            }
        }
        TeamDetailsResponseDTO response = new TeamDetailsResponseDTO();
        response.setTeam(team);
        response.setTeamMembers(smallMemberDTOs);
        return  response;
    }

    public void removeMember(String teamId, String userIdToRemove) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        TeamMember currentMembership = teamMemberRepository.findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this team"));
        if (!currentMembership.getRole().equals("ADMIN")) {
            throw new RuntimeException("Only an ADMIN can remove members");
        }
        if (currentUser.getId().equals(userIdToRemove)) {
            throw new RuntimeException("You cannot remove yourself");
        }
        TeamMember memberToRemove = teamMemberRepository.findByTeamIdAndUserId(teamId, userIdToRemove)
                .orElseThrow(() -> new RuntimeException("That user is not a member of this team"));
        teamMemberRepository.delete(memberToRemove);
    }

    public void updateMemberRole(String teamId, String userIdToUpdate, String newRole) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        TeamMember currentMembership = teamMemberRepository
                .findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this team"));
        if (!"ADMIN".equals(currentMembership.getRole())) {
            throw new RuntimeException("Only an ADMIN can change roles");
        }
        if (currentUser.getId().equals(userIdToUpdate)) {
            throw new RuntimeException("You cannot change your own role");
        }
        if (!"ADMIN".equals(newRole) && !"EDITOR".equals(newRole) && !"VIEWER".equals(newRole)) {
            throw new RuntimeException("Invalid role: " + newRole);
        }
        TeamMember targetMembership = teamMemberRepository.findByTeamIdAndUserId(teamId, userIdToUpdate)
                .orElseThrow(() -> new RuntimeException("That user is not a member of this team"));
        targetMembership.setRole(newRole);
        teamMemberRepository.save(targetMembership);
    }

}
