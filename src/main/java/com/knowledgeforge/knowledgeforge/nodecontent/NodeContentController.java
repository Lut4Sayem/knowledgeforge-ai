package com.knowledgeforge.knowledgeforge.nodecontent;

import org.springframework.web.bind.annotation.*;

@RestController
public class NodeContentController {
    private final NodeContentService service;

    public NodeContentController(NodeContentService service) {
        this.service = service;
    }

    @GetMapping("/api/nodes/{id}/content")
    public ContentDTO get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/api/nodes/{id}/content")
    public SavedDTO save(@PathVariable String id, @RequestBody SaveContentRequest request) {
        return service.save(id, request);
    }
}
