package com.knowledgeforge.knowledgeforge.attachment;

public class CreateAttachmentRequest {
    private String originalName;
    private String url;

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}