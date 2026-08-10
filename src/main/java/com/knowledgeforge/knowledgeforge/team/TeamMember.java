package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "team_members")
public class TeamMember {
    @Id
    private String id;

    private String teamId;
    private String userId;
    private String role;
    private Date joinedAt;

    public TeamMember(){};

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getTeamId() {
        return teamId;
    }
    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
    public Date getJoinedAt() {
        return joinedAt;
    }
    public void setJoinedAt(Date joinedAt) {
        this.joinedAt = joinedAt;
    }

}
