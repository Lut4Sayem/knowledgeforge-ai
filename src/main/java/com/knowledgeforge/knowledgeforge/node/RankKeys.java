package com.knowledgeforge.knowledgeforge.node;

import java.util.ArrayList;
import java.util.List;

/**
 * Fractional rank keys: base-62 digit strings (0-9A-Za-z, which sorts the same as plain string order)
 * read as fractions 0.d1d2d3... A key never ends in '0', so there is always room between two keys.
 */
public final class RankKeys {
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = DIGITS.length();
    public static final int MAX_LENGTH = 64;

    private RankKeys() {}

    /** A key strictly between before and after; null means an open end. Throws IllegalStateException when the key would pass 64 chars (caller rebalances with spread). */
    public static String between(String before, String after) {
        if (before != null && after != null && before.compareTo(after) >= 0) {
            throw new IllegalArgumentException("before must sort before after");
        }
        String key = mid(before == null ? "" : before, after);
        if (key.length() > MAX_LENGTH) throw new IllegalStateException("Rank key too long, rebalance siblings");
        return key;
    }

    /** n sorted keys with gaps between them, for rebalancing a sibling list. */
    public static List<String> spread(int n) {
        List<String> keys = new ArrayList<>(n);
        int width = 1;
        long cap = BASE;
        while (cap <= n + 1) {
            width++;
            cap *= BASE;
        }
        long step = cap / (n + 1);
        for (int i = 1; i <= n; i++) {
            keys.add(encode(i * step, width) + DIGITS.charAt(BASE / 2)); // last digit non-zero
        }
        return keys;
    }

    private static String encode(long value, int width) {
        char[] out = new char[width];
        for (int i = width - 1; i >= 0; i--) {
            out[i] = DIGITS.charAt((int) (value % BASE));
            value /= BASE;
        }
        return new String(out);
    }

    // a is a (possibly empty) key, b a key or null for "infinity"; a < b.
    private static String mid(String a, String b) {
        if (b != null) {
            int n = 0; // shared prefix, a padded with zeros
            while (n < b.length() && (n < a.length() ? a.charAt(n) : '0') == b.charAt(n)) n++;
            if (n > 0) return b.substring(0, n) + mid(a.substring(Math.min(n, a.length())), b.substring(n));
        }
        int digitA = a.isEmpty() ? 0 : DIGITS.indexOf(a.charAt(0));
        int digitB = b != null ? DIGITS.indexOf(b.charAt(0)) : BASE;
        if (digitB - digitA > 1) return String.valueOf(DIGITS.charAt((digitA + digitB) / 2));
        if (b != null && b.length() > 1) return b.substring(0, 1);
        return DIGITS.charAt(digitA) + mid(a.isEmpty() ? "" : a.substring(1), null);
    }
}
