package com.knowledgeforge.knowledgeforge.space;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<Space>> getSpace(@PathVariable String teamId){
        return ResponseEntity.status(200).body(spaceService.getSpace(teamId));
    }
}
