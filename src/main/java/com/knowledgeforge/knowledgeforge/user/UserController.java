package com.knowledgeforge.knowledgeforge.user;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;
    static final int MAX_NAME_LENGTH = 100; // same as the profile form's maxLength

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final ProfilePictureService pictureService;

    public UserController(UserRepository userRepository,
                          TeamMemberRepository teamMemberRepository,
                          TeamRepository teamRepository,
                          ProfilePictureService pictureService) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.pictureService = pictureService;
    }

    @PostMapping(value = "/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPicture(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileBody(pictureService.upload(file)));
    }

    @DeleteMapping("/me/picture")
    public ResponseEntity<Map<String, Object>> removePicture() {
        return ResponseEntity.ok(profileBody(pictureService.remove()));
    }

    @GetMapping("/users/{userId}/picture")
    public ResponseEntity<Resource> picture(@PathVariable String userId) {
        ProfilePictureService.Picture pic = pictureService.load(userId);
        // Our own image type + nosniff + a locked-down CSP, so an uploaded file can only ever render as an image.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pic.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(pic.resource());
    }

    private static Map<String, Object> profileBody(User user) {
        // HashMap allows null values; Map.of() does not.
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("fullName", user.getFullName());
        body.put("profilePicture", user.getProfilePicture());
        return body;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return ResponseEntity.ok(profileBody(user));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(Authentication authentication,
                                      @RequestBody UpdateProfileRequest request) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (request.getFullName() != null) {
            String name = request.getFullName().trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 1 to " + MAX_NAME_LENGTH + " characters");
            }
            user.setFullName(name);
        }

        user.setUpdatedAt(new Date());
        User saved = userRepository.save(user);

        return ResponseEntity.ok(profileBody(saved));
    }

    @GetMapping("/me/teams")
    public ResponseEntity<List<MyTeamDTO>> myTeams(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

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