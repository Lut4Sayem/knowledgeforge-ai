package com.knowledgeforge.knowledgeforge.node;

/** parentId null = top level of the team space. With neither beforeId nor afterId the page goes to the end. */
public record MoveToTeamRequest(String spaceId, String parentId, String beforeId, String afterId) {
}
