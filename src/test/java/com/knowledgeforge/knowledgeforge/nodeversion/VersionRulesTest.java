package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.common.UserRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static com.knowledgeforge.knowledgeforge.nodeversion.VersionRules.Action.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure rules of domain spec 5.1; time comes from a fixed Clock. */
class VersionRulesTest {
    private static final Instant T0 = Instant.parse("2026-11-11T10:00:00Z");
    private final Clock clock = Clock.fixed(T0.plus(Duration.ofHours(2)), ZoneOffset.UTC);

    private Instant now() {
        return clock.instant();
    }

    private static Date at(Instant i) {
        return Date.from(i);
    }

    private static NodeVersion version(VersionKind kind, String text, String hash, Instant created, String... authorIds) {
        NodeVersion v = new NodeVersion();
        v.setKind(kind);
        v.setText(text);
        v.setContentHash(hash);
        v.setCreatedAt(at(created));
        v.setUpdatedAt(at(created));
        v.setAuthors(java.util.Arrays.stream(authorIds).map(id -> new UserRef(id, "n", null)).toList());
        return v;
    }

    @Test
    void idleFiveMinutesCloses() {
        Instant start = now().minus(Duration.ofMinutes(6));
        assertTrue(VersionRules.shouldClose(start, now().minus(Duration.ofMinutes(5)), now()));
        assertFalse(VersionRules.shouldClose(start, now().minus(Duration.ofMinutes(4)), now()));
    }

    @Test
    void longSessionClosesOnlyAfterPause() {
        Instant start = now().minus(Duration.ofMinutes(20));
        assertFalse(VersionRules.shouldClose(start, now().minusSeconds(10), now())); // still typing
        assertTrue(VersionRules.shouldClose(start, now().minusSeconds(30), now())); // natural pause
        // a short session with the same pause is not closed
        assertFalse(VersionRules.shouldClose(now().minus(Duration.ofMinutes(10)), now().minusSeconds(60), now()));
    }

    @Test
    void sameAuthorSmallChangeMerges() {
        NodeVersion latest = version(VersionKind.AUTO, "hello world", "a", now().minus(Duration.ofMinutes(30)), "u1");
        NodeVersion next = version(VersionKind.AUTO, "hello brave world", "b", now(), "u1");
        assertEquals(MERGE, VersionRules.decide(latest, next, now()));
        // an hour or more after the previous one, or a big change, adds a new version
        assertEquals(INSERT, VersionRules.decide(version(VersionKind.AUTO, "hello world", "a", now().minus(Duration.ofMinutes(61)), "u1"), next, now()));
        next.setText("hello world" + "x".repeat(200));
        assertEquals(INSERT, VersionRules.decide(latest, next, now()));
    }

    @Test
    void differentAuthorDoesNotMerge() {
        NodeVersion latest = version(VersionKind.AUTO, "hello", "a", now().minusSeconds(60), "u1");
        assertEquals(INSERT, VersionRules.decide(latest, version(VersionKind.AUTO, "hello!", "b", now(), "u2"), now()));
        assertEquals(INSERT, VersionRules.decide(latest, version(VersionKind.AUTO, "hello!", "b", now(), "u1", "u2"), now()));
    }

    @Test
    void neverMergedKindsAreKept() {
        for (VersionKind k : List.of(VersionKind.NAMED, VersionKind.PUBLISHED, VersionKind.PUBLISHED_TO_TEAM,
                VersionKind.RESTORED, VersionKind.RECOVERED)) {
            NodeVersion latest = version(k, "hello", "a", now().minusSeconds(60), "u1");
            assertEquals(INSERT, VersionRules.decide(latest, version(VersionKind.AUTO, "hello!", "b", now(), "u1"), now()), k.name());
        }
    }

    @Test
    void namedNeverMerged() {
        NodeVersion latest = version(VersionKind.NAMED, "hello", "a", now().minusSeconds(60), "u1");
        assertEquals(INSERT, VersionRules.decide(latest, version(VersionKind.AUTO, "hello!", "b", now(), "u1"), now()));
    }

    @Test
    void unchangedHashSkipped() {
        NodeVersion latest = version(VersionKind.NAMED, "hello", "same", now().minusSeconds(60), "u1");
        assertEquals(SKIP, VersionRules.decide(latest, version(VersionKind.AUTO, "hello", "same", now(), "u1"), now()));
        assertEquals(INSERT, VersionRules.decide(null, version(VersionKind.AUTO, "hello", "same", now(), "u1"), now()));
    }
}
