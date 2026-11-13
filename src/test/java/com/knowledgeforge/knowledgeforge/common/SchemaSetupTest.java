package com.knowledgeforge.knowledgeforge.common;

import com.knowledgeforge.knowledgeforge.support.IntegrationTest;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaSetupTest extends IntegrationTest {

    @Autowired
    MongoTemplate mongo;

    private Set<String> indexNames(String collection) {
        return mongo.indexOps(collection).getIndexInfo().stream()
                .map(i -> i.getName()).filter(n -> !n.equals("_id_")).collect(Collectors.toSet());
    }

    @Test
    void createsAllIndexes() {
        assertEquals(Set.of("spaceId_status_parentId_rank", "ancestors", "spaceId_status_contentUpdatedAt",
                "trashedAt_trashed", "tags", "restriction_viewers", "restriction_editors"), indexNames("nodes"));
        assertEquals(Set.of("editSession_lastEditAt"), indexNames("node_contents"));
        assertEquals(Set.of("nodeId_createdAt", "nodeId_privateTo_createdAt"), indexNames("node_versions"));
        assertEquals(Set.of("expiresAt_ttl"), indexNames("edit_locks"));
    }

    private Document ref(String name) {
        return new Document("id", new ObjectId()).append("name", name);
    }

    private Document validNode() {
        Date now = new Date();
        return new Document("spaceId", new ObjectId()).append("type", "PAGE").append("ancestors", List.of())
                .append("rank", "V").append("title", "Hello").append("tags", List.of("a")).append("status", "ACTIVE")
                .append("publishState", "DRAFT").append("locked", false).append("createdBy", ref("A"))
                .append("updatedBy", ref("A")).append("contentUpdatedAt", now).append("createdAt", now)
                .append("updatedAt", now).append("schemaVersion", 1);
    }

    private Document validContent(int stateBytes) {
        return new Document("_id", new ObjectId()).append("spaceId", new ObjectId())
                .append("format", "YJS_BLOCKNOTE_V1").append("state", new Binary(new byte[stateBytes]))
                .append("revision", 0L).append("text", "").append("contentHash", "h")
                .append("updatedAt", new Date()).append("schemaVersion", 1);
    }

    @Test
    void acceptsValidDocuments() {
        mongo.getCollection("nodes").insertOne(validNode());
        mongo.getCollection("node_contents").insertOne(validContent(10));
    }

    @Test
    void acceptsContentEpochAndRecoveredVersions() {
        mongo.getCollection("node_contents").insertOne(validContent(10).append("contentEpoch", 1));
        assertThrows(Exception.class, () -> mongo.getCollection("node_contents")
                .insertOne(validContent(10).append("contentEpoch", "one")));

        Date now = new Date();
        Document version = new Document("nodeId", new ObjectId()).append("spaceId", new ObjectId())
                .append("kind", "RECOVERED").append("title", "T").append("state", new Binary(new byte[3]))
                .append("text", "").append("contentHash", "h").append("authors", List.of(ref("A")))
                .append("privateTo", new ObjectId()).append("baseEpoch", 0).append("createdAt", now)
                .append("updatedAt", now).append("schemaVersion", 1);
        mongo.getCollection("node_versions").insertOne(version);
        Document unknownKind = new Document(version).append("_id", new ObjectId()).append("kind", "SOMETHING");
        assertThrows(Exception.class, () -> mongo.getCollection("node_versions").insertOne(unknownKind));
    }

    @Test
    void rejectsTitleOver200() {
        Document d = validNode().append("title", "x".repeat(201));
        assertThrows(Exception.class, () -> mongo.getCollection("nodes").insertOne(d));
    }

    @Test
    void rejectsMoreThan20Tags() {
        Document d = validNode().append("tags",
                IntStream.range(0, 21).mapToObj(i -> "t" + i).collect(Collectors.toList()));
        assertThrows(Exception.class, () -> mongo.getCollection("nodes").insertOne(d));
    }

    @Test
    void rejectsStateOver4MB() {
        Document tooBig = validContent(4 * 1024 * 1024 + 1);
        assertThrows(Exception.class, () -> mongo.getCollection("node_contents").insertOne(tooBig));
        mongo.getCollection("node_contents").insertOne(validContent(4 * 1024 * 1024));
    }
}
