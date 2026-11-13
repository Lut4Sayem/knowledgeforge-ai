package com.knowledgeforge.knowledgeforge.dashboard;

import com.knowledgeforge.knowledgeforge.activity.ActivityLogRepository;
import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.node.PublishState;
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

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Per-team counts and roles on the dashboard. Repositories are mocked; no database needed.
class DashboardServiceTest {

    UserRepository users = mock(UserRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    NodeRepository nodes = mock(NodeRepository.class);
    NodeAccessService access = mock(NodeAccessService.class);
    ActivityLogRepository activity = mock(ActivityLogRepository.class);

    DashboardService service = new DashboardService(users, members, teams, spaces, nodes, access, activity);

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
        when(nodes.countBySpaceIdAndStatus("s1", NodeStatus.ACTIVE)).thenReturn(2L);
        when(nodes.countBySpaceIdAndStatus("s2", NodeStatus.ACTIVE)).thenReturn(1L);
        when(nodes.findTop30BySpaceIdInAndStatusOrderByContentUpdatedAtDesc(anyCollection(), eq(NodeStatus.ACTIVE))).thenReturn(List.of());
        when(activity.findTop20ByTeamIdInOrderByCreatedAtDesc(anyList())).thenReturn(List.of());

        List<TeamSummaryDTO> out = service.getDashboard().getTeams();

        assertThat(out).containsExactly(
                new TeamSummaryDTO("t1", "Platform", null, "ADMIN", 2, 3),
                new TeamSummaryDTO("t2", "Empty", null, "VIEWER", 0, 0));
    }

    @Test
    void recentPagesComeFromNodesNewestFirstAndSkipPagesTheUserCannotRead() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("me@test.com", null));
        User me = new User("Me", "me@test.com", "hash");
        me.setId("u1");
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(me));
        when(members.findByUserId("u1")).thenReturn(List.of(member("t1", "EDITOR")));
        when(teams.findByIdIn(List.of("t1"))).thenReturn(List.of(team("t1", "Platform")));
        when(spaces.findByTeamIdIn(List.of("t1"))).thenReturn(List.of(space("s1", "t1")));
        when(activity.findTop20ByTeamIdInOrderByCreatedAtDesc(anyList())).thenReturn(List.of());
        Node fresh = page("p1", "s1", "Fresh", 200);
        Node secret = page("p2", "s1", "Secret", 150);
        Node old = page("p3", "s1", "Old", 100);
        when(nodes.findTop30BySpaceIdInAndStatusOrderByContentUpdatedAtDesc(eq(List.of("s1")), eq(NodeStatus.ACTIVE)))
                .thenReturn(List.of(fresh, secret, old));
        when(access.resolve(eq("u1"), any(Node.class))).thenReturn(Access.VIEW);
        when(access.resolve("u1", secret)).thenReturn(Access.TITLE_ONLY);

        List<RecentDocumentDTO> out = service.getDashboard().getRecentDocuments();

        assertThat(out).extracting(RecentDocumentDTO::getTitle).containsExactly("Fresh", "Old");
        assertThat(out.get(0).getDocumentId()).isEqualTo("p1");
        assertThat(out.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(out.get(0).getUpdatedAt()).isEqualTo(new Date(200));
        assertThat(out.get(0).getSpaceId()).isEqualTo("s1");
    }

    static Node page(String id, String spaceId, String title, long contentUpdatedAt) {
        Node n = new Node();
        n.setId(id);
        n.setSpaceId(spaceId);
        n.setTitle(title);
        n.setPublishState(PublishState.DRAFT);
        n.setContentUpdatedAt(new Date(contentUpdatedAt));
        return n;
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
}
