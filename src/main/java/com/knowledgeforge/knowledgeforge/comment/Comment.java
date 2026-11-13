package com.knowledgeforge.knowledgeforge.comment;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.Date;

@Document(collection = "comments")
public class Comment {

    @Id
    private String id;

    private String documentId;
    private String content;

    private String createdBy;
    private Date createdAt;
    /** Set when a personal page moves to a team: only this user sees the comment. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String privateTo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getPrivateTo() { return privateTo; }
    public void setPrivateTo(String privateTo) { this.privateTo = privateTo; }
}
