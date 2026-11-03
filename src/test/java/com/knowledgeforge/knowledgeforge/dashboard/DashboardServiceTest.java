package com.knowledgeforge.knowledgeforge.dashboard;

import com.knowledgeforge.knowledgeforge.activity.ActivityLogRepository;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentEntity;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentRepository;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

// Per-team counts and roles on the dashboard. Repositories are mocked; no database needed.
class DashboardServiceTest {

    UserRepository users = mock(UserRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    DocumentRepository documents = mock(DocumentRepository.class);
    ActivityLogRepository activity = mock(ActivityLogRepository.class);

    DashboardService service = new DashboardService(users, members, teams, spaces, documents, activity);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teamSummariesCountSpacesAndDocumentsPerTeamWithMyRole() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("me@test.com", null));
        User me = new User("Me", "me@test.com", "hash");
        me.setId("u1");
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(me));

        when(members.findByUserId("u1")).thenReturn(List.of(member("t1", "ADMIN"), member("t2", "VIEWER")));
        when(teams.findByIdIn(List.of("t1", "t2"))).thenReturn(List.of(team("t1", "Platform"), team("t2", "Empty")));
        when(spaces.findByTeamIdIn(List.of("t1", "t2"))).thenReturn(List.of(space("s1", "t1"), space("s2", "t1")));
        when(documents.findBySpaceIdIn(anyList())).thenReturn(List.of(doc("d1", "s1"), doc("d2", "s1"), doc("d3", "s2")));
        when(activity.findTop20ByTeamIdInOrderByCreatedAtDesc(anyList())).thenReturn(List.of());

        List<TeamSummaryDTO> out = service.getDashboard().getTeams();

        assertThat(out).containsExactly(
                new TeamSummaryDTO("t1", "Platform", null, "ADMIN", 2, 3),
                new TeamSummaryDTO("t2", "Empty", null, "VIEWER", 0, 0));
    }

    static TeamMember member(String teamId, String role) {
        TeamMember m = new TeamMember();
        m.setTeamId(teamId);
        m.setUserId("u1");
        m.setRole(role);
        return m;
    }

    static Team team(String id, String name) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        return t;
    }

    static Space space(String id, String teamId) {
        Space s = new Space();
        s.setId(id);
        s.setTeamId(teamId);
        return s;
    }

    static DocumentEntity doc(String id, String spaceId) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setSpaceId(spaceId);
        return d;
    }
}
