package com.knowledgeforge.knowledgeforge.node;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class NodeController {
    private final NodeService nodeService;
    private final MoveToTeamService moveToTeam;

    public NodeController(NodeService nodeService, MoveToTeamService moveToTeam) {
        this.nodeService = nodeService;
        this.moveToTeam = moveToTeam;
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

    @PostMapping("/api/nodes/{id}/publish")
    public NodeDTO publish(@PathVariable String id) {
        return nodeService.publish(id);
    }

    @PostMapping("/api/nodes/{id}/unpublish")
    public NodeDTO unpublish(@PathVariable String id) {
        return nodeService.unpublish(id);
    }

    @PostMapping("/api/nodes/{id}/lock")
    public NodeDTO lock(@PathVariable String id) {
        return nodeService.lock(id);
    }

    @PostMapping("/api/nodes/{id}/unlock")
    public NodeDTO unlock(@PathVariable String id) {
        return nodeService.unlock(id);
    }

    @GetMapping("/api/tags/{tag}/nodes")
    public List<TaggedNodeDTO> tagged(@PathVariable String tag) {
        return nodeService.taggedPages(tag);
    }

    @PostMapping("/api/nodes/{id}/move")
    public NodeDTO move(@PathVariable String id, @RequestBody MoveNodeRequest request) {
        return nodeService.move(id, request);
    }

    @PostMapping("/api/nodes/{id}/move-to-team")
    public NodeDTO moveToTeam(@PathVariable String id, @RequestBody MoveToTeamRequest request) {
        return moveToTeam.moveToTeam(id, request);
    }
}
