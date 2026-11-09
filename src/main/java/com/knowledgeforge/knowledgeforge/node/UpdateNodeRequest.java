package com.knowledgeforge.knowledgeforge.node;

import java.util.List;

/** Null field = unchanged. An empty icon clears it. */
public record UpdateNodeRequest(String title, String icon, List<String> tags) {
}
