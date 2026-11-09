package com.knowledgeforge.knowledgeforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Needs a real Atlas database, so it only runs when the connection string is set (skipped on a fresh clone).
@EnabledIfEnvironmentVariable(named = "SPRING_DATA_MONGODB_URI", matches = ".+")
@SpringBootTest
class KnowledgeforgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
