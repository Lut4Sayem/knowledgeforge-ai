package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

// Cached summary, one per document. Valid while documentUpdatedAt equals the document's updatedAt.
@Document(collection = "ai_summaries")
public class AiSummary {

    @Id
    private String id;
    @Indexed(unique = true)
    private String documentId;
    private String summary;
    private boolean trimmed;        // only the start of a long document was sent
    private Date documentUpdatedAt;
    private Date createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public boolean isTrimmed() { return trimmed; }
    public void setTrimmed(boolean trimmed) { this.trimmed = trimmed; }

    public Date getDocumentUpdatedAt() { return documentUpdatedAt; }
    public void setDocumentUpdatedAt(Date documentUpdatedAt) { this.documentUpdatedAt = documentUpdatedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
