package com.knowledgeforge.knowledgeforge;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Deep links of the React app (/app/p/123) serve index.html. Segments with a dot (assets, favicon) are left to static resources.
// shortcut: routes up to 3 segments deep; add a pattern if the app gets deeper URLs.
@Controller
public class SpaController {

    @GetMapping({"/app", "/app/", "/app/{a:[^.]+}", "/app/{a}/{b:[^.]+}", "/app/{a}/{b}/{c:[^.]+}"})
    public String index() {
        return "forward:/app/index.html";
    }
}
