package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

// Cached summary, one per page. Valid while contentUpdatedAt equals the node's contentUpdatedAt.
@Document(collection = "ai_summaries")
public class AiSummary {

    @Id
    private String id;
    @Indexed(unique = true)
    private String nodeId;
    private String summary;
    private boolean trimmed;        // only the start of a long page was sent
    private Date contentUpdatedAt;
    private Date createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public boolean isTrimmed() { return trimmed; }
    public void setTrimmed(boolean trimmed) { this.trimmed = trimmed; }

    public Date getContentUpdatedAt() { return contentUpdatedAt; }
    public void setContentUpdatedAt(Date contentUpdatedAt) { this.contentUpdatedAt = contentUpdatedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
