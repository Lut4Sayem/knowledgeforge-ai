package com.knowledgeforge.knowledgeforge.team;

public class CreateTeamRequest {
    private String teamName;
    private String teamDescription;

    public String getTeamName() {
        return teamName;
    }
    public String getTeamDescription() {
        return teamDescription;
    }
    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }
    public void setTeamDescription(String teamDescription) {
        this.teamDescription = teamDescription;
    }
}
