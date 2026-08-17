package com.knowledgeforge.knowledgeforge.space;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SpaceController {
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping("/api/teams/{teamId}/spaces")
    public ResponseEntity<Space> createSpace(@PathVariable String teamId, @RequestBody CreateSpaceRequest request){
        Space body = spaceService.createSpace(teamId, request);
        return ResponseEntity.status(201).body(body);
    }

    @GetMapping("/api/teams/{teamId}/spaces")
    public ResponseEntity<List<Space>> getSpace(@PathVariable String teamId){
        return ResponseEntity.status(200).body(spaceService.getSpace(teamId));
    }

    @GetMapping("/api/spaces/{spaceId}")
    public ResponseEntity<Space> getSpaceById(@PathVariable String spaceId){
        return ResponseEntity.status(200).body(spaceService.getSpaceById(spaceId));
    }
}
