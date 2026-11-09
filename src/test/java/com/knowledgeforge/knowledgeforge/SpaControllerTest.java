package com.knowledgeforge.knowledgeforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaController.class)
@Import(SecurityConfig.class)
class SpaControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void deepLinkServesIndex() throws Exception {
        mvc.perform(get("/app/p/123")).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app/s/1/trash")).andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app")).andExpect(forwardedUrl("/app/index.html"));
    }

    @Test
    void assetsAreNotForwarded() throws Exception {
        mvc.perform(get("/app/assets/index-abc.js")).andExpect(status().isNotFound());
    }
}
