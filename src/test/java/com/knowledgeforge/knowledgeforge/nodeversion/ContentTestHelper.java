package com.knowledgeforge.knowledgeforge.nodeversion;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Stands in for version restore until Task 6.1 builds it: replaces the state wholesale, so the revision AND
 * the content epoch go up (D6 epoch rule).
 */
public final class ContentTestHelper {
    private ContentTestHelper() {
    }

    public static void replaceWholesale(MongoTemplate mongo, String nodeId, byte[] state) {
        mongo.getCollection("node_contents").updateOne(new Document("_id", new ObjectId(nodeId)),
                new Document("$set", new Document("state", new Binary(state)))
                        .append("$inc", new Document("revision", 1L).append("contentEpoch", 1)));
    }
}
