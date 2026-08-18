package com.knowledgeforge.knowledgeforge.team;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "team_join_requests")
public class TeamJoinRequest {

    @Id
    private String id;
    private String teamId;
    private String userId;
    private String status;
    private Date requestedAt;

    public TeamJoinRequest(){}

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
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Date getRequestedAt() {
        return requestedAt;
    }
    public void setRequestedAt(Date requestedAt) {
        this.requestedAt = requestedAt;
    }
}
