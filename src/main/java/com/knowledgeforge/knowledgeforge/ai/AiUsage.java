package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

// One row per real AI call (cached summaries and no-match answers write nothing). Counts toward the daily limits.
// Holds numbers only, never prompts or answers. Rows expire after 30 days to keep the Atlas free tier small.
@Document(collection = "ai_usage")
@CompoundIndex(def = "{'userId': 1, 'createdAt': 1}")
public class AiUsage {

    @Id
    private String id;
    private String userId;
    private String teamId;
    private String feature; // SUMMARY / ASK
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    @Indexed(expireAfter = "30d")
    private Date createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
