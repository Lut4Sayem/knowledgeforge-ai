package com.knowledgeforge.knowledgeforge.linkpreview;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LinkPreviewController {
    private final LinkPreviewService service;

    public LinkPreviewController(LinkPreviewService service) {
        this.service = service;
    }

    @GetMapping("/link-preview")
    public LinkPreview preview(@RequestParam String url, Authentication auth) {
        return service.preview(auth.getName(), url);
    }
}
