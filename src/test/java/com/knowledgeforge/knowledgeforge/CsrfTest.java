package com.knowledgeforge.knowledgeforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CsrfTest.Probe.class)
@Import({SecurityConfig.class, CsrfTest.Probe.class})
class CsrfTest {

    @RestController
    static class Probe {
        @GetMapping("/api/probe") String read() { return "ok"; }
        @PostMapping("/api/probe") String write() { return "ok"; }
        @PostMapping("/api/auth/login") String login() { return "ok"; }
    }

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser
    void postWithoutTokenIs403() throws Exception {
        mvc.perform(post("/api/probe")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postWithTokenSucceeds() throws Exception {
        mvc.perform(post("/api/probe").with(csrf())).andExpect(status().isOk());
    }

    @Test
    void loginIsExemptAndGetSetsReadableCookie() throws Exception {
        mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mvc.perform(get("/app/login")).andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
