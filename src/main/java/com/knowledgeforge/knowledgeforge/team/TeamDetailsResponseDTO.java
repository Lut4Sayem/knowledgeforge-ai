package com.knowledgeforge.knowledgeforge.team;

import java.util.List;

public class TeamDetailsResponseDTO {
    private Team team;
    private List<TeamMemberDTO> teamMembers;

    public TeamDetailsResponseDTO(){}
    public Team getTeam() {
        return team;
    }
    public void setTeam(Team team) {
        this.team = team;
    }
    public List<TeamMemberDTO> getTeamMembers() {
        return teamMembers;
    }
    public void setTeamMembers(List<TeamMemberDTO> teamMembers) {
        this.teamMembers = teamMembers;
    }
}
