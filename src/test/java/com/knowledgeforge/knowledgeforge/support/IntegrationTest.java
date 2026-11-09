package com.knowledgeforge.knowledgeforge.support;

import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

// Real MongoDB (Atlas-local, so Atlas Search works too). One container shared by all subclasses.
// Without Docker the tests are skipped. KF_TEST_MONGO_URI points them at an already running MongoDB instead
// (plain mongod is enough for everything except Atlas Search tests).
@SpringBootTest
public abstract class IntegrationTest {

    private static final String EXTERNAL_URI = System.getenv("KF_TEST_MONGO_URI");
    private static MongoDBAtlasLocalContainer mongo;

    @BeforeAll
    static void requireMongo() {
        assumeTrue(EXTERNAL_URI != null || DockerClientFactory.instance().isDockerAvailable(),
                "needs Docker or KF_TEST_MONGO_URI");
    }

    private static synchronized String uri() {
        if (EXTERNAL_URI != null) return EXTERNAL_URI;
        if (mongo == null) {
            mongo = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:8.0");
            mongo.start();
        }
        return mongo.getConnectionString();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", IntegrationTest::uri);
        registry.add("spring.mongodb.database", () -> "knowledgeforge_test");
    }
}
