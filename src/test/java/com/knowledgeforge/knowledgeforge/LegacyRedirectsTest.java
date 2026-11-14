package com.knowledgeforge.knowledgeforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyRedirects.class)
@Import(SecurityConfig.class)
class LegacyRedirectsTest {

    @Autowired
    MockMvc mvc;

    private void redirects(String from, String to) throws Exception {
        // a raw URI: get(String) would encode the % again
        mvc.perform(get(java.net.URI.create(from))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(to));
    }

    @Test
    void oldUrlsRedirect() throws Exception {
        redirects("/", "/app");
        redirects("/dashboard.html", "/app");
        redirects("/login.html", "/app/login");
        redirects("/login.html?next=%2Fapp%2Fp%2F1", "/app/login?next=%2Fapp%2Fp%2F1");
        redirects("/register.html", "/app/register");
        redirects("/join.html?code=ABC", "/app/join/ABC");
        redirects("/document.html?id=n1", "/app/p/n1");
        redirects("/space.html?id=s1", "/app/s/s1");
        redirects("/team.html?id=t1", "/app/teams/t1");
        redirects("/profile.html", "/app/profile");
        redirects("/search.html", "/app/search");
        redirects("/search.html?q=runbook&tag=ops", "/app/search?q=runbook&tag=ops");
    }

    @Test
    void missingIdGoesToTheDashboardAndOddIdsStayOneSegment() throws Exception {
        redirects("/document.html", "/app");
        redirects("/join.html?code=", "/app");
        redirects("/document.html?id=a%2F..%2Fb", "/app/p/a%2F..%2Fb");
    }
}
