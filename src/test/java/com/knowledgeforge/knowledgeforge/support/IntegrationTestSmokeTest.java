package com.knowledgeforge.knowledgeforge.support;

import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationTestSmokeTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Test
    void containerStartsAndAppConnects() {
        User saved = users.save(new User("Smoke", "smoke@example.test", "x"));
        assertEquals("smoke@example.test", users.findById(saved.getId()).orElseThrow().getEmail());
        users.deleteById(saved.getId());
    }
}
