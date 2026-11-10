package com.knowledgeforge.knowledgeforge.nodeversion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class NodeVersionController {
    private final NodeVersionService service;

    public NodeVersionController(NodeVersionService service) {
        this.service = service;
    }

    @PostMapping("/api/nodes/{id}/versions/recovered")
    @ResponseStatus(HttpStatus.CREATED)
    public RecoveredVersionDTO saveRecovered(@PathVariable String id, @RequestBody RecoveredVersionRequest request) {
        return service.saveRecovered(id, request);
    }
}
