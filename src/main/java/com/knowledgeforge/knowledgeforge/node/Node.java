package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.common.UserRef;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** One page in the tree (physical schema 3.4). Indexes are created explicitly by SchemaSetup. */
@Getter @Setter
@Document(collection = "nodes")
public class Node {

    /** Per-page access list; null on the node means "inherit". */
    @Getter @Setter
    public static class Restriction {
        @Field(targetType = FieldType.OBJECT_ID)
        private List<String> viewers = new ArrayList<>();
        @Field(targetType = FieldType.OBJECT_ID)
        private List<String> editors = new ArrayList<>();
    }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String spaceId;
    private NodeType type = NodeType.PAGE;
    @Field(targetType = FieldType.OBJECT_ID)
    private String parentId;
    @Field(targetType = FieldType.OBJECT_ID)
    private List<String> ancestors = new ArrayList<>();
    private String rank;
    private String title = "";
    private String icon;
    private List<String> tags = new ArrayList<>();
    private NodeStatus status = NodeStatus.ACTIVE;
    private PublishState publishState = PublishState.DRAFT;
    private Date trashedAt;
    @Field(targetType = FieldType.OBJECT_ID)
    private String trashedBy;
    @Field(targetType = FieldType.OBJECT_ID)
    private String trashRootId;
    private boolean locked;
    @Field(targetType = FieldType.OBJECT_ID)
    private String lockedBy;
    private Restriction restriction;
    private UserRef createdBy;
    private UserRef updatedBy;
    private Date contentUpdatedAt;
    private Date createdAt;
    private Date updatedAt;
    private int schemaVersion = 1;
}
