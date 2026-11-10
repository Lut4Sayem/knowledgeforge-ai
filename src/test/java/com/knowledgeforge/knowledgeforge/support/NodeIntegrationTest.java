package com.knowledgeforge.knowledgeforge.support;

import com.knowledgeforge.knowledgeforge.auth.AuthController;
import com.knowledgeforge.knowledgeforge.auth.AuthService;
import com.knowledgeforge.knowledgeforge.node.CreateNodeRequest;
import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.node.NodeService;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** A team with an ADMIN logged in and one team space; helpers to add users and pages. */
public abstract class NodeIntegrationTest extends IntegrationTest {
    @Autowired protected AuthService authService;
    @Autowired protected NodeService nodeService;
    @Autowired protected SpaceRepository spaces;
    @Autowired protected UserRepository users;
    @Autowired protected TeamRepository teams;
    @Autowired protected TeamMemberRepository members;
    @Autowired protected MongoTemplate mongo;

    protected User admin;
    protected Team team;
    protected Space space;

    @BeforeEach
    void setUpTeam() {
        admin = newUser();
        team = new Team();
        team.setName("Team " + UUID.randomUUID());
        team = teams.save(team);
        join(admin, "ADMIN");
        space = new Space();
        space.setName("Docs");
        space.setTeamId(team.getId());
        space.setOwnerType(Space.OwnerType.TEAM);
        space = spaces.save(space);
        loginAs(admin);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    protected User newUser() {
        String email = UUID.randomUUID() + "@example.test";
        AuthController.RegisterRequest r = new AuthController.RegisterRequest();
        r.setFullName("Test User");
        r.setEmail(email);
        r.setPassword("password123");
        authService.register(r);
        return users.findByEmail(email).orElseThrow();
    }

    protected User newMember(String role) {
        User u = newUser();
        join(u, role);
        return u;
    }

    private void join(User u, String role) {
        TeamMember m = new TeamMember();
        m.setTeamId(team.getId());
        m.setUserId(u.getId());
        m.setRole(role);
        members.save(m);
    }

    protected void loginAs(User u) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    protected NodeDTO createPage(String title) {
        return nodeService.create(space.getId(), new CreateNodeRequest(null, title, null));
    }

    protected int statusOf(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }
}
