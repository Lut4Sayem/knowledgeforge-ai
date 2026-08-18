package com.knowledgeforge.knowledgeforge.team;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TeamJoinRequestController {
    private final TeamJoinRequestService teamJoinRequestService;

    public TeamJoinRequestController(TeamJoinRequestService teamJoinRequestService) {
        this.teamJoinRequestService = teamJoinRequestService;
    }

    @PostMapping("/api/team-invites/{inviteCode}/join")
    public ResponseEntity<TeamJoinRequest>joinRequest(@PathVariable String inviteCode){
        TeamJoinRequest teamJoinRequest = teamJoinRequestService.requestToJoin(inviteCode);
        return ResponseEntity.status(201).body(teamJoinRequest);
    }

    @GetMapping("/api/teams/{teamId}/join-requests")
    public ResponseEntity<List<TeamJoinRequestDTO>> getTeamJoinRequests(@PathVariable String teamId) {
        return ResponseEntity.status(200).body(teamJoinRequestService.getPendingRequests(teamId));
    }

    @PostMapping("/api/teams/{teamId}/join-requests/{requestId}/accept")
    public ResponseEntity<TeamJoinRequest> acceptRequest(@PathVariable String teamId, @PathVariable String requestId){
        TeamJoinRequest teamJoinRequest = teamJoinRequestService.acceptRequest(teamId, requestId);
        return ResponseEntity.status(200).body(teamJoinRequest);
    }

    @PostMapping("/api/teams/{teamId}/join-requests/{requestId}/reject")
    public ResponseEntity<TeamJoinRequest> rejectRequest(@PathVariable String teamId, @PathVariable String requestId){
        TeamJoinRequest teamJoinRequest = teamJoinRequestService.rejectRequest(teamId, requestId);
        return ResponseEntity.status(200).body(teamJoinRequest);
    }

}
