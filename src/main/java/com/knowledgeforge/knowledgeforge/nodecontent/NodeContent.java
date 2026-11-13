package com.knowledgeforge.knowledgeforge.nodecontent;

import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.node.PublishState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Page content (physical schema 3.5). _id equals the node id. */
@Getter @Setter
@Document(collection = "node_contents")
public class NodeContent {

    /** Copy of node fields so search runs on one collection. */
    @Getter @Setter
    public static class Search {
        private String title = "";
        private List<String> tags = new ArrayList<>();
        private NodeStatus status = NodeStatus.ACTIVE;
        private PublishState publishState = PublishState.DRAFT;
    }

    @Getter @Setter
    public static class EditSession {
        private Date startedAt;
        private Date lastEditAt;
        @Field(targetType = FieldType.OBJECT_ID)
        private List<String> authors = new ArrayList<>();
    }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String spaceId;
    private ContentFormat format = ContentFormat.YJS_BLOCKNOTE_V1;
    private byte[] state = new byte[0];
    private long revision;
    /** Goes up only when the state is replaced wholesale (restore), never on a normal save. */
    private int contentEpoch;
    private String text = "";
    private Search search = new Search();
    private EditSession editSession;
    private String contentHash = "";
    private Date updatedAt;
    private int schemaVersion = 1;
}
