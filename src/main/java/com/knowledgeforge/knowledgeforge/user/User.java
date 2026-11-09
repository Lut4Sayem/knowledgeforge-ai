package com.knowledgeforge.knowledgeforge.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String fullName;
    private String email;
    private String passwordHash;

    // NEW
    private String profilePicture; // URL shown as the avatar: an uploaded picture's /api URL, or an old external link
    private String pictureFileId;      // GridFS id of the uploaded picture, null if none
    private String pictureContentType;
    private Date createdAt;
    private Date updatedAt;

    /** Last opened pages, newest last, max 20. Written with $push/$slice by NodeService; mapped here so save() keeps it. */
    private java.util.List<RecentNode> recentNodes = new java.util.ArrayList<>();

    public static class RecentNode {
        @org.springframework.data.mongodb.core.mapping.Field(targetType = org.springframework.data.mongodb.core.mapping.FieldType.OBJECT_ID)
        private String nodeId;
        private Date at;

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public Date getAt() { return at; }
        public void setAt(Date at) { this.at = at; }
    }

    public java.util.List<RecentNode> getRecentNodes() { return recentNodes; }
    public void setRecentNodes(java.util.List<RecentNode> recentNodes) { this.recentNodes = recentNodes; }

    // IMPORTANT: Spring Data needs a no-args constructor
    public User() {
    }

    // Keep your existing constructor (so your register code still works)
    public User(String fullName, String email, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public String getPictureFileId() { return pictureFileId; }
    public void setPictureFileId(String pictureFileId) { this.pictureFileId = pictureFileId; }

    public String getPictureContentType() { return pictureContentType; }
    public void setPictureContentType(String pictureContentType) { this.pictureContentType = pictureContentType; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}