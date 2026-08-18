package com.knowledgeforge.knowledgeforge.team;

import java.util.Date;

public class TeamJoinRequestDTO {
    private String id;
    private String fullName;
    private String email;
    private String status;
    private Date requestedAt;

    public TeamJoinRequestDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date requestedAt) { this.requestedAt = requestedAt; }
}