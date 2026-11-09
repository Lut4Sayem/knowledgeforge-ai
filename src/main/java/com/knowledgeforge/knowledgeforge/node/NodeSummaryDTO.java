package com.knowledgeforge.knowledgeforge.node;

public record NodeSummaryDTO(String id, String parentId, String rank, String title, String icon, NodeType type,
                             PublishState publishState, boolean locked, boolean titleOnly) {
}
