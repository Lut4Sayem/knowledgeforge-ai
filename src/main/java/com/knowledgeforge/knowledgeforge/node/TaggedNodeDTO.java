package com.knowledgeforge.knowledgeforge.node;

import java.util.List;

public record TaggedNodeDTO(String id, String spaceId, String spaceName, String title, String icon, List<String> tags,
                            PublishState publishState) {
}
