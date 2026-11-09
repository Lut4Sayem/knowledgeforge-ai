package com.knowledgeforge.knowledgeforge.access;

import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.knowledgeforge.knowledgeforge.access.Access.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAccessServiceTest {
    static final String ME = "u1", OTHER = "u2", TEAM = "t1";

    NodeRepository nodes = mock(NodeRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    UserRepository users = mock(UserRepository.class);
    NodeAccessService access = new NodeAccessService(nodes, spaces, members, users);

    @BeforeEach
    void setUp() {
        Space team = new Space();
        team.setId("sTeam");
        team.setOwnerType(Space.OwnerType.TEAM);
        team.setTeamId(TEAM);
        Space personal = new Space();
        personal.setId("sMine");
        personal.setOwnerType(Space.OwnerType.USER);
        personal.setOwnerUserId(ME);
        when(spaces.findById("sTeam")).thenReturn(Optional.of(team));
        when(spaces.findById("sMine")).thenReturn(Optional.of(personal));
        when(members.findByTeamIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(nodes.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void role(String userId, String role) {
        TeamMember m = new TeamMember();
        m.setTeamId(TEAM);
        m.setUserId(userId);
        m.setRole(role);
        when(members.findByTeamIdAndUserId(TEAM, userId)).thenReturn(Optional.of(m));
        when(members.canEdit(TEAM, userId)).thenReturn(!"VIEWER".equals(role));
    }

    Node node(String id, String spaceId, Node.Restriction r, String... ancestors) {
        Node n = new Node();
        n.setId(id);
        n.setSpaceId(spaceId);
        n.setRestriction(r);
        n.setAncestors(List.of(ancestors));
        return n;
    }

    Node teamNode() {
        return node("n1", "sTeam", null);
    }

    static Node.Restriction restriction(List<String> viewers, List<String> editors) {
        Node.Restriction r = new Node.Restriction();
        r.setViewers(viewers);
        r.setEditors(editors);
        return r;
    }

    /** Parent "p" carries the restriction, child "c" has none. */
    Node childOf(Node.Restriction parentRestriction) {
        Node parent = node("p", "sTeam", parentRestriction);
        when(nodes.findAllById(List.of("p"))).thenReturn(List.of(parent));
        return node("c", "sTeam", null, "p");
    }

    @Test
    void viewerIsCappedAtView() {
        role(ME, "ADMIN");
        assertEquals(EDIT, access.resolve(ME, teamNode()));
        role(ME, "EDITOR");
        assertEquals(EDIT, access.resolve(ME, teamNode()));
        role(ME, "VIEWER");
        assertEquals(VIEW, access.resolve(ME, teamNode()));
    }

    @Test
    void nonMemberIsNone() {
        role(ME, "ADMIN");
        assertEquals(NONE, access.resolve(OTHER, teamNode()));
    }

    @Test
    void personalSpaceOtherUserIsNone() {
        Node mine = node("n2", "sMine", null);
        assertEquals(EDIT, access.resolve(ME, mine));
        assertEquals(NONE, access.resolve(OTHER, mine));
        role(OTHER, "ADMIN"); // team rank gives no way into someone's private space
        assertEquals(NONE, access.resolve(OTHER, mine));
    }

    @Test
    void missingSpaceIsNone() {
        assertEquals(NONE, access.resolve(ME, node("n3", "gone", null)));
    }

    @Test
    void restrictionOnNodeItself() {
        role(ME, "EDITOR");
        assertEquals(EDIT, access.resolve(ME, node("n", "sTeam", restriction(List.of(), List.of(ME)))));
        assertEquals(VIEW, access.resolve(ME, node("n", "sTeam", restriction(List.of(ME), List.of()))));
        assertEquals(NONE, access.resolve(ME, node("n", "sTeam", restriction(List.of(OTHER), List.of(OTHER)))));
    }

    @Test
    void ancestorRestrictionAppliesToChild() {
        role(ME, "EDITOR");
        assertEquals(VIEW, access.resolve(ME, childOf(restriction(List.of(ME), List.of()))));
        assertEquals(EDIT, access.resolve(ME, childOf(restriction(List.of(), List.of(ME)))));
        assertEquals(NONE, access.resolve(ME, childOf(restriction(List.of(OTHER), List.of()))));
    }

    @Test
    void childCannotWidenBeyondParent() {
        role(ME, "EDITOR");
        Node child = childOf(restriction(List.of(ME), List.of())); // parent: view only
        child.setRestriction(restriction(List.of(), List.of(ME))); // child lists me as editor
        assertEquals(VIEW, access.resolve(ME, child));
    }

    @Test
    void teamViewerListedAsEditorStaysView() {
        role(ME, "VIEWER");
        assertEquals(VIEW, access.resolve(ME, node("n", "sTeam", restriction(List.of(), List.of(ME)))));
    }

    @Test
    void adminNotListedSeesTitleOnly() {
        role(ME, "ADMIN");
        assertEquals(TITLE_ONLY, access.resolve(ME, node("n", "sTeam", restriction(List.of(OTHER), List.of()))));
        assertEquals(TITLE_ONLY, access.resolve(ME, childOf(restriction(List.of(OTHER), List.of()))));
    }

    @Test
    void archivedCapsAtView() {
        role(ME, "EDITOR");
        Node n = teamNode();
        n.setStatus(NodeStatus.ARCHIVED);
        assertEquals(VIEW, access.resolve(ME, n));
    }

    @Test
    void trashedIsNoneExceptForTrashView() {
        role(ME, "EDITOR");
        Node n = teamNode();
        n.setStatus(NodeStatus.TRASHED);
        assertEquals(NONE, access.resolve(ME, n));
        assertEquals(EDIT, access.resolveForTrash(ME, n));
        role(ME, "VIEWER");
        assertEquals(VIEW, access.resolveForTrash(ME, n));
        assertEquals(NONE, access.resolveForTrash(OTHER, n));
    }

    @Test
    void lockedCapsEditorAtView() {
        role(ME, "EDITOR");
        Node n = teamNode();
        n.setLocked(true);
        assertEquals(VIEW, access.resolve(ME, n));
    }

    @Test
    void lockedArchivedAndRestrictedTakesTheLowest() {
        role(ME, "ADMIN");
        Node n = node("n", "sTeam", restriction(List.of(OTHER), List.of()));
        n.setLocked(true);
        n.setStatus(NodeStatus.ARCHIVED);
        assertEquals(TITLE_ONLY, access.resolve(ME, n));
    }

    // --- require* ---

    void loginAs(String userId) {
        User u = new User();
        u.setId(userId);
        when(users.findByEmail("me@x.test")).thenReturn(Optional.of(u));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("me@x.test", null));
    }

    int statusOf(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void requireMethods() {
        loginAs(ME);
        role(ME, "EDITOR");
        Node n = teamNode();
        when(nodes.findById("n1")).thenReturn(Optional.of(n));

        assertEquals(n, access.requireView("n1"));
        assertEquals(n, access.requireEdit("n1"));

        n.setLocked(true);
        assertEquals(n, access.requireView("n1"));
        assertEquals(403, statusOf(() -> access.requireEdit("n1")));
        assertEquals(n, access.requireEditIgnoringLock("n1"));

        role(ME, "VIEWER");
        assertEquals(403, statusOf(() -> access.requireEditIgnoringLock("n1")));
    }

    @Test
    void requireMissingNodeIs404AndNonMemberIs403() {
        loginAs(OTHER);
        when(nodes.findById("nope")).thenReturn(Optional.empty());
        assertEquals(404, statusOf(() -> access.requireView("nope")));
        when(nodes.findById("n1")).thenReturn(Optional.of(teamNode()));
        assertEquals(403, statusOf(() -> access.requireView("n1")));
    }

    @Test
    void canEditSpace() {
        role(ME, "EDITOR");
        role(OTHER, "VIEWER");
        Space team = spaces.findById("sTeam").get();
        Space mine = spaces.findById("sMine").get();
        assertEquals(true, access.canEditSpace(ME, team));
        assertEquals(false, access.canEditSpace(OTHER, team));
        assertEquals(true, access.canEditSpace(ME, mine));
        assertEquals(false, access.canEditSpace(OTHER, mine));
    }
}
