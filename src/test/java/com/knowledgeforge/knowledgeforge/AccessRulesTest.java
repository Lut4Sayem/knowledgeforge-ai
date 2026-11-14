package com.knowledgeforge.knowledgeforge;

import com.knowledgeforge.knowledgeforge.space.CreateSpaceRequest;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.space.SpaceService;
import com.knowledgeforge.knowledgeforge.team.*;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Unit tests for who-can-do-what. Repositories are mocked, so no database is needed.
class AccessRulesTest {

    static final String EMAIL = "me@test.com", USER_ID = "u1", TEAM_ID = "t1", SPACE_ID = "s1";

    UserRepository users = mock(UserRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    TeamJoinRequestRepository joinRequests = mock(TeamJoinRequestRepository.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, null));
        User me = new User("Me", EMAIL, "hash");
        me.setId(USER_ID);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(me));

        Space space = new Space();
        space.setId(SPACE_ID);
        space.setTeamId(TEAM_ID);
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space));

        when(members.canEdit(any(), any())).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    void myRole(String role) {
        boolean member = role != null;
        when(members.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(member);
        if (!member) {
            when(members.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.empty());
            return;
        }
        TeamMember m = new TeamMember();
        m.setTeamId(TEAM_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        when(members.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.of(m));
    }

    static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    @Test
    void canEditOnlyForAdminAndEditor() {
        myRole("ADMIN");
        assertThat(members.canEdit(TEAM_ID, USER_ID)).isTrue();
        myRole("EDITOR");
        assertThat(members.canEdit(TEAM_ID, USER_ID)).isTrue();
        myRole("VIEWER");
        assertThat(members.canEdit(TEAM_ID, USER_ID)).isFalse();
        myRole(null);
        assertThat(members.canEdit(TEAM_ID, USER_ID)).isFalse();
    }

    @Test
    void viewerCannotCreateSpaces() {
        myRole("VIEWER");
        SpaceService spaceService = new SpaceService(members, spaces, users, teams);
        assertStatus(() -> spaceService.createSpace(TEAM_ID, new CreateSpaceRequest()), HttpStatus.FORBIDDEN);
        verify(spaces, never()).save(any());
    }

    @Test
    void nonMemberCannotSeeTeamDetails() {
        myRole(null);
        when(teams.findById(TEAM_ID)).thenReturn(Optional.of(new Team()));
        TeamService teamService = new TeamService(teams, users, members);
        assertStatus(() -> teamService.getTeamDetails(TEAM_ID), HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCannotAcceptJoinRequestOfAnotherTeam() {
        myRole("ADMIN");
        TeamJoinRequest other = new TeamJoinRequest();
        other.setTeamId("other-team");
        other.setStatus("PENDING");
        when(joinRequests.findById("r1")).thenReturn(Optional.of(other));
        TeamJoinRequestService service = new TeamJoinRequestService(joinRequests, members, users,
                mock(TeamInviteRepository.class), teams);

        assertStatus(() -> service.acceptRequest(TEAM_ID, "r1"), HttpStatus.NOT_FOUND);
        verify(members, never()).save(any());
    }

    @Test
    void nonMemberCannotAcceptJoinRequests() {
        myRole(null);
        TeamJoinRequestService service = new TeamJoinRequestService(joinRequests, members, users,
                mock(TeamInviteRepository.class), teams);
        assertStatus(() -> service.acceptRequest(TEAM_ID, "r1"), HttpStatus.FORBIDDEN);
    }
}
