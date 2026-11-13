package com.knowledgeforge.knowledgeforge.search;

import com.knowledgeforge.knowledgeforge.node.PublishState;

import java.util.List;

/**
 * One search hit. type is PAGE, SPACE or TEAM; spaces and teams (matched by name) only fill type, teamId/spaceId,
 * spaceName and title. snippetHtml is escaped, with only {@code <mark>} added.
 */
public record SearchResultDTO(String type, String nodeId, String teamId, String spaceId, String spaceName,
                              String title, String path, String snippetHtml, List<String> tags,
                              PublishState publishState, boolean archived) {
}
