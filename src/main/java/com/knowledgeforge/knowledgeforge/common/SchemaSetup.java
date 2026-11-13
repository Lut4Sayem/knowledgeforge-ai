package com.knowledgeforge.knowledgeforge.common;

import org.bson.Document;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates the indexes and $jsonSchema validators of the new collections (physical schema 3.3-3.7, 3.14).
 * Explicit on purpose: auto-index-creation stays off for these entities. Safe to run on every start.
 */
@Component
public class SchemaSetup implements ApplicationRunner {

    private static final int MB4 = 4 * 1024 * 1024;
    private static final String OID = "objectId";

    private final MongoTemplate mongo;

    public SchemaSetup(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        setupNodes();
        setupContents();
        setupVersions();
        setupLocks();
        setupMigrations();
        setupSpaces();
    }

    // ---------- collections ----------

    private void setupNodes() {
        validator("nodes", new Document("$jsonSchema", schema(
                req("spaceId", "type", "ancestors", "rank", "title", "tags", "status", "publishState", "locked",
                        "createdBy", "updatedBy", "contentUpdatedAt", "createdAt", "updatedAt", "schemaVersion"),
                new Document("spaceId", t(OID))
                        .append("type", en("PAGE", "BOARD"))
                        .append("parentId", t(OID, "null"))
                        .append("ancestors", arr(t(OID), 50))
                        .append("rank", str(1, 64))
                        .append("title", str(0, 200))
                        .append("icon", new Document("bsonType", java.util.List.of("string", "null")).append("maxLength", 16))
                        .append("tags", arr(str(1, 40), 20))
                        .append("status", en("ACTIVE", "ARCHIVED", "TRASHED"))
                        .append("publishState", en("DRAFT", "PUBLISHED"))
                        .append("trashedAt", t("date", "null"))
                        .append("trashedBy", t(OID, "null"))
                        .append("trashRootId", t(OID, "null"))
                        .append("locked", t("bool"))
                        .append("lockedBy", t(OID, "null"))
                        .append("restriction", new Document("bsonType", java.util.List.of("null", "object"))
                                .append("properties", new Document("viewers", arr(t(OID), 100))
                                        .append("editors", arr(t(OID), 100))))
                        .append("createdBy", userRef())
                        .append("updatedBy", userRef())
                        .append("contentUpdatedAt", t("date"))
                        .append("createdAt", t("date"))
                        .append("updatedAt", t("date"))
                        .append("schemaVersion", t("int", "long")))));
        var ops = mongo.indexOps("nodes");
        ops.createIndex(new Index().named("spaceId_status_parentId_rank").on("spaceId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC).on("parentId", Sort.Direction.ASC).on("rank", Sort.Direction.ASC));
        ops.createIndex(new Index().named("ancestors").on("ancestors", Sort.Direction.ASC));
        ops.createIndex(new Index().named("spaceId_status_contentUpdatedAt").on("spaceId", Sort.Direction.ASC)
                .on("status", Sort.Direction.ASC).on("contentUpdatedAt", Sort.Direction.DESC));
        ops.createIndex(new Index().named("trashedAt_trashed").on("trashedAt", Sort.Direction.ASC)
                .partial(PartialIndexFilter.of(Criteria.where("status").is("TRASHED"))));
        ops.createIndex(new Index().named("tags").on("tags", Sort.Direction.ASC));
        ops.createIndex(new Index().named("restriction_viewers").on("restriction.viewers", Sort.Direction.ASC).sparse());
        ops.createIndex(new Index().named("restriction_editors").on("restriction.editors", Sort.Direction.ASC).sparse());
    }

    private void setupContents() {
        Document schema = schema(
                req("spaceId", "format", "state", "revision", "text", "contentHash", "updatedAt", "schemaVersion"),
                new Document("spaceId", t(OID))
                        .append("format", en("YJS_BLOCKNOTE_V1", "YJS_BOARD_V1"))
                        .append("state", t("binData"))
                        .append("revision", t("int", "long"))
                        .append("contentEpoch", t("int", "long")) // absent = 0
                        .append("text", new Document("bsonType", "string").append("maxLength", 1_000_000))
                        .append("search", new Document("bsonType", "object")
                                .append("properties", new Document("title", str(0, 200))
                                        .append("tags", arr(str(1, 40), 20))
                                        .append("status", en("ACTIVE", "ARCHIVED", "TRASHED"))
                                        .append("publishState", en("DRAFT", "PUBLISHED"))))
                        .append("editSession", new Document("bsonType", java.util.List.of("null", "object"))
                                .append("properties", new Document("startedAt", t("date")).append("lastEditAt", t("date"))
                                        .append("authors", arr(t(OID), 100))))
                        .append("contentHash", t("string"))
                        .append("updatedAt", t("date"))
                        .append("schemaVersion", t("int", "long")));
        // jsonSchema cannot size a binData field, so the 4 MB cap is an $expr next to it.
        validator("node_contents", new Document("$jsonSchema", schema).append("$expr",
                new Document("$lte", java.util.List.of(new Document("$binarySize", "$state"), MB4))));
        mongo.indexOps("node_contents").createIndex(new Index().named("editSession_lastEditAt")
                .on("editSession.lastEditAt", Sort.Direction.ASC).sparse());
        setupSearchIndex();
    }

    /** Atlas Search index (ADR 0005). Needs Atlas or Atlas-local; plain mongod cannot, so a failure only logs. */
    private void setupSearchIndex() {
        try {
            Document def;
            try (var in = SchemaSetup.class.getResourceAsStream("/search/nodes_search.json")) {
                def = Document.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            boolean exists = mongo.getCollection("node_contents").aggregate(java.util.List.of(
                    new Document("$listSearchIndexes", new Document("name", def.getString("name"))))).iterator().hasNext();
            if (!exists) {
                mongo.getDb().runCommand(new Document("createSearchIndexes", "node_contents")
                        .append("indexes", java.util.List.of(def)));
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(SchemaSetup.class).warn("Atlas Search index not created: {}", e.getMessage());
        }
    }

    private void setupVersions() {
        Document schema = schema(
                req("nodeId", "spaceId", "kind", "title", "state", "text", "contentHash", "authors", "createdAt",
                        "updatedAt", "schemaVersion"),
                new Document("nodeId", t(OID)).append("spaceId", t(OID))
                        .append("kind", en("AUTO", "NAMED", "PUBLISHED", "PUBLISHED_TO_TEAM", "RESTORED", "MIGRATED", "RECOVERED"))
                        .append("name", new Document("bsonType", java.util.List.of("string", "null")).append("maxLength", 100))
                        .append("title", str(0, 200))
                        .append("state", t("binData"))
                        .append("text", t("string"))
                        .append("contentHash", t("string"))
                        .append("authors", arr(userRef(), 100))
                        .append("privateTo", t(OID, "null"))
                        .append("baseEpoch", t("int", "long", "null"))
                        .append("createdAt", t("date")).append("updatedAt", t("date"))
                        .append("schemaVersion", t("int", "long")));
        validator("node_versions", new Document("$jsonSchema", schema).append("$expr",
                new Document("$lte", java.util.List.of(new Document("$binarySize", "$state"), MB4))));
        var ops = mongo.indexOps("node_versions");
        ops.createIndex(new Index().named("nodeId_createdAt").on("nodeId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC));
        ops.createIndex(new Index().named("nodeId_privateTo_createdAt").on("nodeId", Sort.Direction.ASC)
                .on("privateTo", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
    }

    private void setupLocks() {
        validator("edit_locks", new Document("$jsonSchema", schema(req("holder", "expiresAt"),
                new Document("holder", userRef()).append("expiresAt", t("date")))));
        mongo.indexOps("edit_locks").createIndex(new Index().named("expiresAt_ttl")
                .on("expiresAt", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS));
    }

    private void setupMigrations() {
        validator("migrations", new Document("$jsonSchema", schema(req("startedAt", "status"),
                new Document("startedAt", t("date")).append("finishedAt", t("date", "null"))
                        .append("status", en("RUNNING", "DONE", "FAILED")).append("counts", t("object", "null")))));
    }

    /** One personal space per user (physical schema 3.3). Existing spaces have no ownerUserId, so they are skipped. */
    private void setupSpaces() {
        mongo.indexOps("spaces").createIndex(new Index().named("ownerUserId_unique")
                .on("ownerUserId", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("ownerType").is("USER"))));
    }

    // ---------- helpers ----------

    /** Creates the collection with a strict validator, or updates the validator if it already exists. */
    private void validator(String collection, Document validator) {
        if (!mongo.collectionExists(collection)) {
            mongo.getDb().createCollection(collection);
        }
        mongo.getDb().runCommand(new Document("collMod", collection).append("validator", validator)
                .append("validationLevel", "strict").append("validationAction", "error"));
    }

    private static Document schema(String[] required, Document properties) {
        return new Document("bsonType", "object").append("required", java.util.List.of(required))
                .append("properties", properties);
    }

    private static String[] req(String... names) {
        return names;
    }

    private static Document t(String... types) {
        return new Document("bsonType", types.length == 1 ? types[0] : java.util.List.of(types));
    }

    private static Document str(int min, int max) {
        return new Document("bsonType", "string").append("minLength", min).append("maxLength", max);
    }

    private static Document en(String... values) {
        return new Document("bsonType", "string").append("enum", java.util.List.of(values));
    }

    private static Document arr(Document items, int max) {
        return new Document("bsonType", "array").append("maxItems", max).append("items", items);
    }

    private static Document userRef() {
        return new Document("bsonType", "object").append("required", java.util.List.of("id", "name"))
                .append("properties", new Document("id", t(OID)).append("name", str(1, 100))
                        .append("avatarUrl", new Document("bsonType", java.util.List.of("string", "null")).append("maxLength", 500)));
    }
}
