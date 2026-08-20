package com.knowledgeforge.knowledgeforge.dashboard;

import java.util.List;

public class DashboardResponseDTO {
    private int teamCount;
    private int spaceCount;
    private int documentCount;

    private List<RecentDocumentDTO> recentDocuments;
    private List<RecentActivityDTO> recentActivity;

    public int getTeamCount() { return teamCount; }
    public void setTeamCount(int teamCount) { this.teamCount = teamCount; }

    public int getSpaceCount() { return spaceCount; }
    public void setSpaceCount(int spaceCount) { this.spaceCount = spaceCount; }

    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }

    public List<RecentDocumentDTO> getRecentDocuments() { return recentDocuments; }
    public void setRecentDocuments(List<RecentDocumentDTO> recentDocuments) { this.recentDocuments = recentDocuments; }

    public List<RecentActivityDTO> getRecentActivity() { return recentActivity; }
    public void setRecentActivity(List<RecentActivityDTO> recentActivity) { this.recentActivity = recentActivity; }
}