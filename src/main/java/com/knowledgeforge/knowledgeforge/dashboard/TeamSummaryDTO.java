package com.knowledgeforge.knowledgeforge.dashboard;

/** One row of the dashboard's team list: the team plus my role and how much it holds. */
public record TeamSummaryDTO(String teamId, String name, String description, String role,
                             int spaceCount, int documentCount) {
}
