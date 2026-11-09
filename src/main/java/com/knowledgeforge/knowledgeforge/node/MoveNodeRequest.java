package com.knowledgeforge.knowledgeforge.node;

/** parentId null = top level. With neither beforeId nor afterId the node goes to the end. */
public record MoveNodeRequest(String parentId, String beforeId, String afterId) {
}
