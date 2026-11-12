package com.knowledgeforge.knowledgeforge.attachment;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "attachments")
public class Attachment {

    @Id
    private String id;

    private String documentId; // old document pages; new pages use nodeId (documentId goes away in Phase 9)
    @Indexed
    private String nodeId;

    // Display name for both kinds. Link attachments set url; file attachments set fileId/contentType/size.
    private String originalName;
    private String url;

    private String fileId;       // GridFS id
    private String contentType;  // from our extension allowlist, never from the client
    private Long size;           // bytes

    private String uploadedBy;
    private Date uploadedAt;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Date getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Date uploadedAt) { this.uploadedAt = uploadedAt; }
}