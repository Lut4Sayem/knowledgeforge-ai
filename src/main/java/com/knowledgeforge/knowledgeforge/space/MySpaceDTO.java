package com.knowledgeforge.knowledgeforge.space;

public record MySpaceDTO(String id, String name, String icon, Space.OwnerType ownerType,
                         String teamId, String teamName, String myRole) {
}
