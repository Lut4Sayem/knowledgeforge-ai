package com.knowledgeforge.knowledgeforge.nodeversion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/api/nodes/{id}/versions")
    public List<VersionSummaryDTO> list(@PathVariable String id) {
        return service.list(id);
    }

    @PostMapping("/api/nodes/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionSummaryDTO name(@PathVariable String id, @RequestBody NameVersionRequest request) {
        return service.name(id, request.name());
    }

    /** Called when the page is left (sendBeacon-friendly: no body needed). */
    @PostMapping("/api/nodes/{id}/session/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endSession(@PathVariable String id) {
        service.endSession(id);
    }

    @GetMapping("/api/versions/{vid}")
    public VersionDetailDTO get(@PathVariable String vid) {
        return service.get(vid);
    }

    @PostMapping("/api/versions/{vid}/restore")
    public VersionSummaryDTO restore(@PathVariable String vid) {
        return service.restore(vid);
    }
}
