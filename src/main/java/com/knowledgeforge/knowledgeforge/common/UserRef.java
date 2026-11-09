package com.knowledgeforge.knowledgeforge.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Display-only copy of a user. Never used for permission decisions. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserRef {
    // Named userId in Java because Spring Data stores a property called "id" as "_id"; the stored and JSON name stay "id".
    @Field(name = "id", targetType = FieldType.OBJECT_ID)
    @JsonProperty("id")
    private String userId;
    private String name;
    private String avatarUrl;
}
