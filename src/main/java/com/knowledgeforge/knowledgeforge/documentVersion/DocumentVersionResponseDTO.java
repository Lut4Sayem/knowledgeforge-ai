package com.knowledgeforge.knowledgeforge.documentVersion;

import java.util.Date;

public class DocumentVersionResponseDTO {
    private String id;
    private Integer versionNumber;
    private Date savedAt;

    private String title;
    private String content;

    private String savedByUserId;
    private String savedByFullName;
    private String savedByEmail;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public Date getSavedAt() { return savedAt; }
    public void setSavedAt(Date savedAt) { this.savedAt = savedAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSavedByUserId() { return savedByUserId; }
    public void setSavedByUserId(String savedByUserId) { this.savedByUserId = savedByUserId; }

    public String getSavedByFullName() { return savedByFullName; }
    public void setSavedByFullName(String savedByFullName) { this.savedByFullName = savedByFullName; }

    public String getSavedByEmail() { return savedByEmail; }
    public void setSavedByEmail(String savedByEmail) { this.savedByEmail = savedByEmail; }
}