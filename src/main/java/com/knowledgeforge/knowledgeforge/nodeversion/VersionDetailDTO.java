package com.knowledgeforge.knowledgeforge.nodeversion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.util.Date;
import java.util.List;

public record VersionDetailDTO(String id, VersionKind kind, String name, List<UserRef> authors,
                               Date createdAt, Date updatedAt, @JsonProperty("private") boolean isPrivate, String state, String text) {
}
