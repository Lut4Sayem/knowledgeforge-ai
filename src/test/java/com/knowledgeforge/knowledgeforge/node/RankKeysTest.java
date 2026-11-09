package com.knowledgeforge.knowledgeforge.node;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RankKeysTest {

    @Test
    void betweenNullsGivesMiddle() {
        String k = RankKeys.between(null, null);
        assertFalse(k.isEmpty());
        assertFalse(k.endsWith("0"));
    }

    @Test
    void betweenIsStrictlyOrdered() {
        Random rnd = new Random(42);
        List<String> keys = new ArrayList<>();
        keys.add(RankKeys.between(null, null));
        for (int i = 0; i < 1000; i++) {
            int pos = rnd.nextInt(keys.size() + 1); // insert at a random slot
            String before = pos == 0 ? null : keys.get(pos - 1);
            String after = pos == keys.size() ? null : keys.get(pos);
            String k = RankKeys.between(before, after);
            if (before != null) assertTrue(before.compareTo(k) < 0, before + " < " + k);
            if (after != null) assertTrue(k.compareTo(after) < 0, k + " < " + after);
            assertFalse(k.endsWith("0"));
            keys.add(pos, k);
        }
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys);
    }

    @Test
    void repeatedInsertAtFrontStaysUnder64() {
        String first = RankKeys.between(null, null);
        String k = first;
        for (int i = 0; i < 200; i++) {
            String next = RankKeys.between(null, k);
            assertTrue(next.compareTo(k) < 0);
            assertTrue(next.length() <= 64);
            k = next;
        }
    }

    @Test
    void repeatedInsertAtEndStaysUnder64() {
        String k = RankKeys.between(null, null);
        for (int i = 0; i < 200; i++) {
            String next = RankKeys.between(k, null);
            assertTrue(next.compareTo(k) > 0);
            assertTrue(next.length() <= 64);
            k = next;
        }
    }

    @Test
    void tooLongKeyAsksForRebalance() {
        // keep squeezing between two neighbours until the 64 char cap is hit
        String lo = "a", hi = "b";
        assertThrows(IllegalStateException.class, () -> {
            String l = lo;
            for (int i = 0; i < 500; i++) l = RankKeys.between(l, hi);
        });
    }

    @Test
    void spreadIsSorted() {
        for (int n : new int[]{0, 1, 2, 10, 100, 1000}) {
            List<String> keys = RankKeys.spread(n);
            assertEquals(n, keys.size());
            for (int i = 1; i < n; i++) assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0);
            keys.forEach(k -> {
                assertFalse(k.endsWith("0"));
                assertTrue(k.length() <= 64);
            });
        }
    }

    @Test
    void invalidOrderRejected() {
        assertThrows(IllegalArgumentException.class, () -> RankKeys.between("b", "a"));
        assertThrows(IllegalArgumentException.class, () -> RankKeys.between("a", "a"));
    }
}
