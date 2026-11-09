package com.knowledgeforge.knowledgeforge.node;

/** parentId null = top level; afterId null = at the end of the siblings. */
public record CreateNodeRequest(String parentId, String title, String afterId) {
}
