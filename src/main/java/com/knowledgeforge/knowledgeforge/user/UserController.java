package com.knowledgeforge.knowledgeforge.user;

import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public UserController(UserRepository userRepository,
                          TeamMemberRepository teamMemberRepository,
                          TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // IMPORTANT: HashMap allows null values; Map.of() does NOT.
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("fullName", user.getFullName());
        body.put("profilePicture", user.getProfilePicture());

        return ResponseEntity.ok(body);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(Authentication authentication,
                                      @RequestBody UpdateProfileRequest request) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getProfilePicture() != null) {
            String pic = request.getProfilePicture().trim();
            // allow clearing by sending empty string
            user.setProfilePicture(pic.isEmpty() ? null : pic);
        }

        user.setUpdatedAt(new Date());
        User saved = userRepository.save(user);

        Map<String, Object> body = new HashMap<>();
        body.put("id", saved.getId());
        body.put("email", saved.getEmail());
        body.put("fullName", saved.getFullName());
        body.put("profilePicture", saved.getProfilePicture());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/me/teams")
    public ResponseEntity<List<MyTeamDTO>> myTeams(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<TeamMember> memberships = teamMemberRepository.findByUserId(user.getId());

        List<String> teamIds = new ArrayList<>();
        for (TeamMember tm : memberships) {
            teamIds.add(tm.getTeamId());
        }

        List<Team> teams = teamIds.isEmpty() ? List.of() : teamRepository.findByIdIn(teamIds);

        Map<String, String> teamIdToName = new HashMap<>();
        for (Team t : teams) {
            teamIdToName.put(t.getId(), t.getName());
        }

        List<MyTeamDTO> out = new ArrayList<>();
        for (TeamMember tm : memberships) {
            MyTeamDTO dto = new MyTeamDTO();
            dto.setTeamId(tm.getTeamId());
            dto.setTeamName(teamIdToName.getOrDefault(tm.getTeamId(), "(Unknown team)"));
            dto.setRole(tm.getRole());
            out.add(dto);
        }

        return ResponseEntity.ok(out);
    }
}