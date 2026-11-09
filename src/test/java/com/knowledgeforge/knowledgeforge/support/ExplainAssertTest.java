package com.knowledgeforge.knowledgeforge.support;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ExplainAssertTest extends IntegrationTest {

    static final String COLL = "explain_assert_probe";

    @Autowired
    MongoTemplate template;

    @Test
    void detectsCollectionScan() {
        template.dropCollection(COLL);
        template.insert(new Document("k", 1), COLL);
        Query q = Query.query(Criteria.where("k").is(1));

        assertThrows(AssertionError.class, () -> ExplainAssert.usesIndex(template, COLL, q, "k_1"));

        template.indexOps(COLL).createIndex(new Index().on("k", org.springframework.data.domain.Sort.Direction.ASC));
        ExplainAssert.usesIndex(template, COLL, q, "k_1");
        template.dropCollection(COLL);
    }
}
