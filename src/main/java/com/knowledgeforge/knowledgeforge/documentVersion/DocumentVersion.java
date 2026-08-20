package com.knowledgeforge.knowledgeforge.documentVersion;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "document_versions")
public class DocumentVersion {

    @Id
    private String id;

    private String documentId;
    private String title;
    private String content;

    private Integer versionNumber;

    private String savedBy;
    private Date savedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getSavedBy() { return savedBy; }
    public void setSavedBy(String savedBy) { this.savedBy = savedBy; }

    public Date getSavedAt() { return savedAt; }
    public void setSavedAt(Date savedAt) { this.savedAt = savedAt; }
}