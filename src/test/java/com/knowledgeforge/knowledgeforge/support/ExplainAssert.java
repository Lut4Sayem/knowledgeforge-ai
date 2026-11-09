package com.knowledgeforge.knowledgeforge.support;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ExplainAssert {

    private ExplainAssert() {
    }

    /** Fails if the query's winning plan is not an index scan on {@code indexName}. */
    public static void usesIndex(MongoTemplate template, String collection, Query query, String indexName) {
        Document explain = template.getDb().runCommand(new Document("explain",
                new Document("find", collection).append("filter", query.getQueryObject())
                        .append("sort", query.getSortObject())).append("verbosity", "queryPlanner"));
        Document winning = ((Document) ((Document) explain.get("queryPlanner")).get("winningPlan"));
        String plan = winning.toJson();
        assertTrue(plan.contains("\"IXSCAN\"") && plan.contains("\"indexName\": \"" + indexName + "\""),
                "Expected index " + indexName + " but plan was: " + plan);
    }
}
