package com.knowledgeforge.knowledgeforge.space;

import com.knowledgeforge.knowledgeforge.auth.AuthController;
import com.knowledgeforge.knowledgeforge.auth.AuthService;
import com.knowledgeforge.knowledgeforge.support.IntegrationTest;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonalSpaceIT extends IntegrationTest {

    @Autowired AuthService authService;
    @Autowired SpaceService spaceService;
    @Autowired SpaceRepository spaces;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private User newUser() {
        String email = UUID.randomUUID() + "@example.test";
        AuthController.RegisterRequest r = new AuthController.RegisterRequest();
        r.setFullName("Test User");
        r.setEmail(email);
        r.setPassword("password123");
        authService.register(r);
        return users.findByEmail(email).orElseThrow();
    }

    private void loginAs(User u) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    private Team teamWithSpace(User member, String role, String spaceName) {
        Team t = new Team();
        t.setName("Team " + spaceName);
        t = teams.save(t);
        TeamMember m = new TeamMember();
        m.setTeamId(t.getId());
        m.setUserId(member.getId());
        m.setRole(role);
        members.save(m);
        Space s = new Space();
        s.setName(spaceName);
        s.setTeamId(t.getId());
        s.setOwnerType(Space.OwnerType.TEAM);
        spaces.save(s);
        return t;
    }

    @Test
    void registerCreatesPersonalSpace() {
        User u = newUser();
        Space s = spaces.findByOwnerUserId(u.getId()).orElseThrow();
        assertEquals(Space.OwnerType.USER, s.getOwnerType());
        assertEquals("Private", s.getName());
        assertNull(s.getTeamId());
    }

    @Test
    void createPersonalSpaceTwiceKeepsOne() {
        User u = newUser();
        Space again = spaceService.createPersonalSpace(u.getId());
        assertEquals(spaces.findByOwnerUserId(u.getId()).orElseThrow().getId(), again.getId());
        assertEquals(1, spaces.findAll().stream().filter(s -> u.getId().equals(s.getOwnerUserId())).count());
    }

    @Test
    void meSpacesListsTeamAndPersonal() {
        User u = newUser();
        Team t = teamWithSpace(u, "EDITOR", "Docs");
        loginAs(u);
        List<MySpaceDTO> mine = spaceService.getMySpaces();
        assertEquals(2, mine.size());
        MySpaceDTO team = mine.stream().filter(s -> s.ownerType() == Space.OwnerType.TEAM).findFirst().orElseThrow();
        assertEquals("Docs", team.name());
        assertEquals(t.getId(), team.teamId());
        assertEquals(t.getName(), team.teamName());
        assertEquals("EDITOR", team.myRole());
        MySpaceDTO personal = mine.stream().filter(s -> s.ownerType() == Space.OwnerType.USER).findFirst().orElseThrow();
        assertEquals("Private", personal.name());
        assertNull(personal.teamId());
    }

    @Test
    void meSpacesExcludesOtherTeams() {
        User me = newUser();
        User other = newUser();
        teamWithSpace(other, "ADMIN", "Secret");
        loginAs(me);
        List<MySpaceDTO> mine = spaceService.getMySpaces();
        assertEquals(1, mine.size());
        assertEquals(Space.OwnerType.USER, mine.get(0).ownerType());
    }
}
