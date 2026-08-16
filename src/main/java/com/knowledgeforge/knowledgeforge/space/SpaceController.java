package com.knowledgeforge.knowledgeforge.space;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams/{teamId}/spaces")
public class SpaceController {
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping
    public ResponseEntity<Space> createSpace(@PathVariable String teamId, @RequestBody CreateSpaceRequest request){
        Space body = spaceService.createSpace(teamId, request);
        return ResponseEntity.status(201).body(body);
    }
}
