package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.time.Duration;
import java.time.Instant;

/** The time and merge rules of domain spec 5.1 as pure functions (time is passed in, never read here). */
final class VersionRules {
    static final Duration IDLE = Duration.ofMinutes(5), LONG_SESSION = Duration.ofMinutes(15),
            PAUSE = Duration.ofSeconds(30), MERGE_WINDOW = Duration.ofHours(1);
    static final int MERGE_MAX_CHANGED_CHARS = 200;

    enum Action { SKIP, MERGE, INSERT }

    private VersionRules() {
    }

    /** No edits for 5 minutes, or the session has run 15 minutes and there is a pause of 30 s. */
    static boolean shouldClose(Instant startedAt, Instant lastEditAt, Instant now) {
        Duration idle = Duration.between(lastEditAt, now);
        if (idle.compareTo(IDLE) >= 0) return true;
        return Duration.between(startedAt, now).compareTo(LONG_SESSION) >= 0 && idle.compareTo(PAUSE) >= 0;
    }

    /**
     * What to do with a new AUTO version {@code next} given the newest non-recovered version {@code latest}:
     * same content = SKIP; same single author, latest is an AUTO less than an hour old and the change is small = MERGE
     * into it; anything else INSERT. Only AUTO is ever merged (NAMED, PUBLISHED, RESTORED... stay as they are).
     */
    static Action decide(NodeVersion latest, NodeVersion next, Instant now) {
        if (latest == null) return Action.INSERT;
        if (latest.getContentHash().equals(next.getContentHash())) return Action.SKIP;
        if (latest.getKind() != VersionKind.AUTO) return Action.INSERT;
        if (!sameSingleAuthor(latest, next)) return Action.INSERT;
        // measured from creation: merging moves updatedAt, so measuring from it would let one version grow forever
        if (Duration.between(latest.getCreatedAt().toInstant(), now).compareTo(MERGE_WINDOW) >= 0) return Action.INSERT;
        return changedChars(latest.getText(), next.getText()) < MERGE_MAX_CHANGED_CHARS ? Action.MERGE : Action.INSERT;
    }

    private static boolean sameSingleAuthor(NodeVersion a, NodeVersion b) {
        if (a.getAuthors().size() != 1 || b.getAuthors().size() != 1) return false;
        UserRef x = a.getAuthors().get(0), y = b.getAuthors().get(0);
        return x.getUserId() != null && x.getUserId().equals(y.getUserId());
    }

    /** Size of the differing middle part once the common start and end are removed (the larger of the two sides). */
    static int changedChars(String a, String b) {
        int max = Math.min(a.length(), b.length()), p = 0;
        while (p < max && a.charAt(p) == b.charAt(p)) p++;
        int s = 0;
        while (s < max - p && a.charAt(a.length() - 1 - s) == b.charAt(b.length() - 1 - s)) s++;
        return Math.max(a.length(), b.length()) - p - s;
    }
}
