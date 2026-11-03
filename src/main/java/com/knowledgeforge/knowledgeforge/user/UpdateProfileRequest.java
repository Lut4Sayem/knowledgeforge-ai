package com.knowledgeforge.knowledgeforge.user;

/** Pictures are uploaded through POST /api/me/picture, not set here. */
public class UpdateProfileRequest {
    private String fullName;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
