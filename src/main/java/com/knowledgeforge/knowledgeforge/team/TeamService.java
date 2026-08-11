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
//1. Get current user's ID
//2. Find TeamMembers by that userId
//3. Extract teamIds from those TeamMembers
//4. Find Teams using those teamIds
//5. Return the list of Teams
    public List<Team> getTeams(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        boolean emailNotInDB = userOpt.isEmpty();
        if(emailNotInDB){
            throw new RuntimeException("User Not Found");
        }
        // ... top part stays the same ...
        String userId = userOpt.get().getId();
        System.out.println("DEBUG: Found userId: " + userId);

        List<TeamMember> teamMembers = teamMemberRepository.findByUserId(userId);
        System.out.println("DEBUG: Found " + teamMembers.size() + " TeamMembers for this user.");

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
                smallMemberDTOs.add(MemberDTO);
            }
        }
        TeamDetailsResponseDTO response = new TeamDetailsResponseDTO();
        response.setTeam(team);
        response.setTeamMembers(smallMemberDTOs);
        return  response;
    }
}
