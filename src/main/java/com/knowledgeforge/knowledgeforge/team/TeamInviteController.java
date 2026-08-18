package com.knowledgeforge.knowledgeforge.team;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamInviteController {
    private final TeamInviteService teamInviteService;
    public TeamInviteController(TeamInviteService teamInviteService){
        this.teamInviteService = teamInviteService;
    }

    @PostMapping("/api/teams/{teamId}/invite")
    public ResponseEntity<TeamInvite> createTeamInvite(@PathVariable String teamId){
        TeamInvite body= teamInviteService.createTeamInvite(teamId);
        return ResponseEntity.status(201).body(body);
    }
}
