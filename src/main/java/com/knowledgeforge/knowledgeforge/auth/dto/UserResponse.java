package com.knowledgeforge.knowledgeforge.auth.dto;

public class UserResponse {
    private String id;
    private String fullName;
    private String email;

    public UserResponse(String id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getFullname() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }
}

