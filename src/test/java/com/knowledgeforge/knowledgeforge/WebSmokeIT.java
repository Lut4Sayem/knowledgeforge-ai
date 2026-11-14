package com.knowledgeforge.knowledgeforge;

import com.knowledgeforge.knowledgeforge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Phase 0 end to end: real server, real Mongo, real built frontend (needs the Maven build to have run it).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSmokeIT extends IntegrationTest {

    @LocalServerPort
    int port;

    final CookieManager cookies = new CookieManager();
    final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    HttpResponse<String> send(String method, String path, String json, String csrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (csrf != null) b.header("X-XSRF-TOKEN", csrf);
        b.method(method, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json));
        return http.send(b.build(), BodyHandlers.ofString());
    }

    String csrfCookie() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse(null);
    }

    @Test
    void healthIsPublicWithoutDetails() throws Exception {
        HttpResponse<String> h = send("GET", "/actuator/health", null, null);
        assertEquals(200, h.statusCode());
        assertEquals("{\"status\":\"UP\"}", h.body());
        assertEquals(401, send("GET", "/actuator/env", null, null).statusCode());
    }

    @Test
    void deepLinksAndAssetsAreServed() throws Exception {
        HttpResponse<String> deep = send("GET", "/app/p/123", null, null);
        assertEquals(200, deep.statusCode());
        assertTrue(deep.body().contains("<div id=\"root\">"));
        Matcher m = Pattern.compile("/app/assets/[^\"]+\\.js").matcher(deep.body());
        assertTrue(m.find());
        HttpResponse<String> js = send("GET", m.group(), null, null);
        assertEquals(200, js.statusCode());
        assertTrue(js.headers().firstValue("Content-Type").orElse("").contains("javascript"));
        assertEquals(404, send("GET", "/app/missing.js", null, null).statusCode());
    }

    @Test
    void csrfProtectsWritesAndLoginFlowWorks() throws Exception {
        send("GET", "/app/login", null, null);
        String token = csrfCookie();
        assertNotNull(token, "XSRF-TOKEN cookie must be set");

        // no session + token -> 401 (not 403); no token -> 403
        assertEquals(401, send("PUT", "/api/me", "{\"fullName\":\"x\"}", token).statusCode());
        assertEquals(403, send("PUT", "/api/me", "{\"fullName\":\"x\"}", null).statusCode());

        String email = "smoke-" + UUID.randomUUID() + "@example.test";
        assertTrue(send("POST", "/api/auth/register", "{\"fullName\":\"Smoke\",\"email\":\"" + email + "\",\"password\":\"password123\"}", null).statusCode() < 300);
        assertEquals(200, send("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"password123\"}", null).statusCode());

        token = csrfCookie();
        assertEquals(403, send("PUT", "/api/me", "{\"fullName\":\"Renamed\"}", null).statusCode());
        assertEquals(200, send("PUT", "/api/me", "{\"fullName\":\"Renamed\"}", token).statusCode());
        assertEquals(403, send("POST", "/logout", null, null).statusCode());
        assertTrue(send("POST", "/logout", null, token).statusCode() < 400);
        assertEquals(401, send("GET", "/api/me", null, null).statusCode());
    }
}
