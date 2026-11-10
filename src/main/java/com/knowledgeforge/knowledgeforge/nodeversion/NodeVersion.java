package com.knowledgeforge.knowledgeforge.nodeversion;

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

/** Snapshot of a page (physical schema 3.6). */
@Getter @Setter
@Document(collection = "node_versions")
public class NodeVersion {

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String nodeId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String spaceId;
    private VersionKind kind;
    private String name;
    private String title = "";
    private byte[] state = new byte[0];
    private String text = "";
    private String contentHash = "";
    private List<UserRef> authors = new ArrayList<>();
    @Field(targetType = FieldType.OBJECT_ID)
    private String privateTo;
    /** RECOVERED only: the content epoch the lost copy was based on. */
    private Integer baseEpoch;
    private Date createdAt;
    private Date updatedAt;
    private int schemaVersion = 1;
}
