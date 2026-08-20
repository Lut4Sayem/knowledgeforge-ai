package com.knowledgeforge.knowledgeforge.user;

public class UpdateProfileRequest {
    private String fullName;
    private String profilePicture;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
}