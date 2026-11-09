package com.knowledgeforge.knowledgeforge.node;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class NodeController {
    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping("/api/spaces/{spaceId}/tree")
    public List<NodeSummaryDTO> tree(@PathVariable String spaceId) {
        return nodeService.tree(spaceId);
    }

    @PostMapping("/api/spaces/{spaceId}/nodes")
    public ResponseEntity<NodeDTO> create(@PathVariable String spaceId, @RequestBody CreateNodeRequest request) {
        return ResponseEntity.status(201).body(nodeService.create(spaceId, request));
    }

    @GetMapping("/api/nodes/{id}")
    public NodeDTO get(@PathVariable String id) {
        return nodeService.get(id);
    }

    @PatchMapping("/api/nodes/{id}")
    public NodeDTO update(@PathVariable String id, @RequestBody UpdateNodeRequest request) {
        return nodeService.update(id, request);
    }

    @PostMapping("/api/nodes/{id}/move")
    public NodeDTO move(@PathVariable String id, @RequestBody MoveNodeRequest request) {
        return nodeService.move(id, request);
    }
}
