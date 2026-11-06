package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

// Cached Ask answer, one per (space, normalized question). Valid while the space's fingerprint is unchanged.
@Document(collection = "ai_answers")
@CompoundIndex(def = "{'spaceId': 1, 'questionKey': 1}", unique = true)
public class AiAnswer {
    @Id
    private String id;
    private String spaceId;
    private String questionKey;
    private String fingerprint;     // changes when any document or the space name/description changes
    private String answer;
    private List<SourceDTO> sources;
    private int usedDocuments;
    private boolean trimmed;
    @Indexed(expireAfter = "30d")   // keeps the free Atlas tier clean
    private Date createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getQuestionKey() { return questionKey; }
    public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<SourceDTO> getSources() { return sources; }
    public void setSources(List<SourceDTO> sources) { this.sources = sources; }
    public int getUsedDocuments() { return usedDocuments; }
    public void setUsedDocuments(int usedDocuments) { this.usedDocuments = usedDocuments; }
    public boolean isTrimmed() { return trimmed; }
    public void setTrimmed(boolean trimmed) { this.trimmed = trimmed; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
