package com.knowledgeforge.knowledgeforge.team;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<Team> createTeam(@RequestBody CreateTeamRequest request){
        Team body= teamService.createTeam(request.getTeamName(), request.getTeamDescription());
        return ResponseEntity.status(201).body(body);
    }

    @GetMapping
    public ResponseEntity<List<Team>> getAllTeams(){
        List<Team> teams= teamService.getTeams();
        return ResponseEntity.status(200).body(teams);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDetailsResponseDTO> getTeam(@PathVariable String teamId){
        TeamDetailsResponseDTO response= teamService.getTeamDetails(teamId);
        return ResponseEntity.status(200).body(response);
    }



}
