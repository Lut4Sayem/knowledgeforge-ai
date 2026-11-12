package com.knowledgeforge.knowledgeforge.linkpreview;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LinkPreviewServiceTest {
    HttpServer server;
    AtomicInteger hits = new AtomicInteger();
    String base;
    /** Real rules, except that the local test server (loopback) is reachable. */
    LinkPreviewService open = new LinkPreviewService(Clock.systemUTC(), a -> !a.isLoopbackAddress() && LinkPreviewService.isBlocked(a));
    LinkPreviewService strict = new LinkPreviewService(Clock.systemUTC(), LinkPreviewService::isBlocked);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/og", ex -> {
            hits.incrementAndGet();
            byte[] b = ("<html><head><title>Plain &amp; title</title><meta property=\"og:title\" content=\"OG title\">"
                    + "<meta content='A &quot;nice&quot; page' property='og:description'>"
                    + "<meta property=\"og:image\" content=\"/img.png\"></head></html>").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        server.createContext("/big", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(200, 0);
            try (OutputStream o = ex.getResponseBody()) {
                o.write("<title>Early</title>".getBytes(StandardCharsets.UTF_8));
                o.write(new byte[600 * 1024]);
                o.write("<meta property=\"og:description\" content=\"too late\">".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) { } // the client stops reading
        });
        server.createContext("/redir", ex -> {
            hits.incrementAndGet();
            ex.getResponseHeaders().add("Location", "http://10.0.0.1/secret");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void linkPreviewBlocksPrivateAddresses() {
        for (String u : new String[]{"http://localhost:" + server.getAddress().getPort() + "/og", base + "/og",
                "http://10.0.0.1/", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://100.64.0.1/", "http://[fc00::1]/"}) {
            assertNull(strict.preview("u", u).title(), u);
        }
        assertEquals(0, hits.get(), "nothing may reach the local server");
        assertNull(open.preview("u", base + "/redir").title()); // redirect to a private address
        assertEquals(1, hits.get());
        assertThrows(ResponseStatusException.class, () -> strict.preview("u", "ftp://example.com/x"));
        assertThrows(ResponseStatusException.class, () -> strict.preview("u", "javascript:alert(1)"));
    }

    @Test
    void parsesOpenGraph() {
        LinkPreview p = open.preview("u", base + "/og");
        assertEquals("OG title", p.title());
        assertEquals("A \"nice\" page", p.description());
        assertEquals(base + "/img.png", p.imageUrl());
        open.preview("u", base + "/og");
        assertEquals(1, hits.get(), "the second call comes from the cache");
    }

    @Test
    void stopsAfter512KB() {
        LinkPreview p = open.preview("u", base + "/big");
        assertEquals("Early", p.title());
        assertNull(p.description());
    }

    @Test
    void rateLimitedAfter30() {
        for (int i = 0; i < 30; i++) strict.preview("u", "http://10.0.0.1/" + i);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> strict.preview("u", "http://10.0.0.1/x"));
        assertEquals(429, e.getStatusCode().value());
        assertDoesNotThrow(() -> strict.preview("other", "http://10.0.0.1/x")); // per user
    }
}
