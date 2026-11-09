package com.knowledgeforge.knowledgeforge.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Display-only copy of a user. Never used for permission decisions. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserRef {
    @Field(targetType = FieldType.OBJECT_ID)
    private String id;
    private String name;
    private String avatarUrl;
}
