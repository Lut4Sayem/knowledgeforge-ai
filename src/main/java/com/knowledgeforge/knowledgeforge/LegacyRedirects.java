package com.knowledgeforge.knowledgeforge;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/** Old bookmarks and emailed links keep working: every old page URL redirects to its place in the React app. */
@Controller
public class LegacyRedirects {

    @GetMapping({"/", "/dashboard.html"})
    public ResponseEntity<Void> dashboard() {
        return found("/app");
    }

    @GetMapping("/login.html")
    public ResponseEntity<Void> login(HttpServletRequest req) {
        return found("/app/login" + query(req));
    }

    @GetMapping("/register.html")
    public ResponseEntity<Void> register(HttpServletRequest req) {
        return found("/app/register" + query(req));
    }

    @GetMapping("/search.html")
    public ResponseEntity<Void> search(HttpServletRequest req) {
        return found("/app/search" + query(req));
    }

    @GetMapping("/profile.html")
    public ResponseEntity<Void> profile() {
        return found("/app/profile");
    }

    @GetMapping("/join.html")
    public ResponseEntity<Void> join(@RequestParam(required = false) String code) {
        return to("/app/join/{v}", code);
    }

    @GetMapping("/document.html")
    public ResponseEntity<Void> document(@RequestParam(required = false) String id) {
        return to("/app/p/{v}", id);
    }

    @GetMapping("/space.html")
    public ResponseEntity<Void> space(@RequestParam(required = false) String id) {
        return to("/app/s/{v}", id);
    }

    @GetMapping("/team.html")
    public ResponseEntity<Void> team(@RequestParam(required = false) String id) {
        return to("/app/teams/{v}", id);
    }

    /** The value is a path segment: encoded, so it cannot add path parts or send the user off site. */
    private static ResponseEntity<Void> to(String template, String value) {
        if (value == null || value.isBlank()) return found("/app");
        return "redirect:" + UriComponentsBuilder.fromPath(template).buildAndExpand(value).encode().toUriString();
    }

    // Location header set directly: a "redirect:" view would encode the already encoded query a second time.
    private static ResponseEntity<Void> found(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    private static String query(HttpServletRequest req) {
        return req.getQueryString() == null ? "" : "?" + req.getQueryString();
    }
}
