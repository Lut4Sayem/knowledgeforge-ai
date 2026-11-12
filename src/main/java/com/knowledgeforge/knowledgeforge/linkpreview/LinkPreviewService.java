package com.knowledgeforge.knowledgeforge.linkpreview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches title / description / image of a URL for bookmark blocks, without ever touching private networks. */
@Service
public class LinkPreviewService {
    static final int MAX_BYTES = 512 * 1024;
    static final int MAX_REDIRECTS = 3;
    static final int LIMIT_PER_MINUTE = 30;
    private static final long CACHE_MS = 24L * 3600 * 1000;
    private static final int CACHE_MAX = 1000;

    private static final Pattern META = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Clock clock;
    private final Predicate<InetAddress> blocked;
    // shortcut: in-memory cache, move to Mongo TTL if multiple instances
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    // shortcut: in-memory fixed-window limiter (per instance), entries are never evicted; fine for a handful of users
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    private record Cached(LinkPreview preview, long expiresAt) {}

    @Autowired
    public LinkPreviewService(Clock clock) {
        this(clock, LinkPreviewService::isBlocked);
    }

    LinkPreviewService(Clock clock, Predicate<InetAddress> blocked) {
        this.clock = clock;
        this.blocked = blocked;
    }

    public LinkPreview preview(String userId, String rawUrl) {
        long now = clock.millis();
        rateLimit(userId, now);
        URI uri = parse(rawUrl);
        Cached hit = cache.get(uri.toString());
        if (hit != null && hit.expiresAt > now) return hit.preview;

        LinkPreview p = fetch(uri);
        if (p == null) return new LinkPreview(rawUrl, null, null, null); // blocked or unreachable: the bare URL
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(uri.toString(), new Cached(p, now + CACHE_MS));
        return p;
    }

    private void rateLimit(String userId, long now) {
        long[] w = windows.compute(userId, (k, old) -> {
            if (old == null || now - old[0] >= 60_000) return new long[]{now, 1};
            old[1]++;
            return old;
        });
        if (w[1] > LIMIT_PER_MINUTE) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many previews, wait a minute");
    }

    private static URI parse(String raw) {
        try {
            URI u = new URI(raw == null ? "" : raw.trim());
            String s = u.getScheme();
            if (u.getHost() == null || s == null || !(s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https"))) throw new IllegalArgumentException();
            return u;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only http and https links are supported");
        }
    }

    /** Follows up to 3 redirects, checking every hop. Null when blocked, unreachable or not a page. */
    private LinkPreview fetch(URI start) {
        URI uri = start;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!allowed(uri)) return null;
                // shortcut: the connection resolves the name again (DNS rebinding window); pin the IP if this ever faces untrusted DNS
                HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(3000);
                c.setReadTimeout(5000);
                c.setRequestProperty("User-Agent", "KnowledgeForgeLinkPreview/1.0");
                c.setRequestProperty("Accept", "text/html");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) return null;
                    uri = parse(uri.resolve(loc).toString());
                    continue;
                }
                if (code != 200) return null;
                try (InputStream in = c.getInputStream()) {
                    return parseHtml(start, uri, new String(readLimited(in), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return null; // too many redirects
    }

    private boolean allowed(URI uri) throws IOException {
        for (InetAddress a : InetAddress.getAllByName(uri.getHost())) if (blocked.test(a)) return false;
        return true;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (out.size() < MAX_BYTES && (n = in.read(buf, 0, Math.min(buf.length, MAX_BYTES - out.size()))) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static LinkPreview parseHtml(URI requested, URI finalUri, String html) {
        String ogTitle = null, ogDesc = null, ogImage = null;
        Matcher m = META.matcher(html);
        while (m.find()) {
            String key = null, content = null;
            Matcher a = ATTR.matcher(m.group());
            while (a.find()) {
                String v = a.group(2) != null ? a.group(2) : a.group(3);
                String name = a.group(1).toLowerCase();
                if (name.equals("property") || name.equals("name")) key = v.toLowerCase();
                else if (name.equals("content")) content = v;
            }
            if (key == null || content == null) continue;
            switch (key) {
                case "og:title" -> ogTitle = ogTitle == null ? content : ogTitle;
                case "og:description" -> ogDesc = ogDesc == null ? content : ogDesc;
                case "og:image" -> ogImage = ogImage == null ? content : ogImage;
                default -> { }
            }
        }
        String title = ogTitle;
        if (title == null) {
            Matcher t = TITLE.matcher(html);
            if (t.find()) title = t.group(1);
        }
        return new LinkPreview(requested.toString(), clean(title, 200), clean(ogDesc, 500), image(finalUri, ogImage));
    }

    private static String image(URI base, String img) {
        if (img == null || img.isBlank()) return null;
        try {
            URI u = base.resolve(unescape(img.trim()));
            String s = u.getScheme();
            return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s) ? u.toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        s = unescape(s).replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /** Loopback, private, link-local, multicast, "this network", CGNAT (100.64/10) and IPv6 unique-local (fc00::/7). */
    static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) return b[0] == 0 || ((b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64);
        return (b[0] & 0xfe) == 0xfc;
    }
}
