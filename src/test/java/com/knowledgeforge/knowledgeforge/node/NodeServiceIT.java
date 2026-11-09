package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.auth.AuthController;
import com.knowledgeforge.knowledgeforge.auth.AuthService;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.support.ExplainAssert;
import com.knowledgeforge.knowledgeforge.support.IntegrationTest;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class NodeServiceIT extends IntegrationTest {

    @Autowired AuthService authService;
    @Autowired NodeService nodeService;
    @Autowired NodeRepository nodes;
    @Autowired SpaceRepository spaces;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired MongoTemplate mongo;

    User admin;
    Space space;

    @BeforeEach
    void setUp() {
        admin = newUser();
        Team team = new Team();
        team.setName("Team " + UUID.randomUUID());
        team = teams.save(team);
        join(team, admin, "ADMIN");
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

    private User newUser() {
        String email = UUID.randomUUID() + "@example.test";
        AuthController.RegisterRequest r = new AuthController.RegisterRequest();
        r.setFullName("Test User");
        r.setEmail(email);
        r.setPassword("password123");
        authService.register(r);
        return users.findByEmail(email).orElseThrow();
    }

    private void join(Team team, User u, String role) {
        TeamMember m = new TeamMember();
        m.setTeamId(team.getId());
        m.setUserId(u.getId());
        m.setRole(role);
        members.save(m);
    }

    private void loginAs(User u) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    private NodeDTO create(String parentId, String title) {
        return nodeService.create(space.getId(), new CreateNodeRequest(parentId, title, null));
    }

    private int statusOf(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    private List<String> treeTitles() {
        return nodeService.tree(space.getId()).stream().map(NodeSummaryDTO::title).toList();
    }

    @Test
    void treeUsesIndex() {
        Query q = Query.query(Criteria.where("spaceId").is(new ObjectId(space.getId())).and("status").is("ACTIVE"))
                .with(Sort.by("parentId", "rank"));
        ExplainAssert.usesIndex(mongo, "nodes", q, "spaceId_status_parentId_rank");
    }

    @Test
    void createUnderParentSetsAncestors() {
        NodeDTO a = create(null, "A");
        NodeDTO b = create(a.id(), "B");
        NodeDTO c = create(b.id(), "C");
        assertEquals(List.of(a.id(), b.id()), nodes.findById(c.id()).orElseThrow().getAncestors());
        assertEquals(List.of("A", "B"), c.breadcrumbs().stream().map(NodeDTO.Crumb::title).toList());
        assertEquals(PublishState.DRAFT, c.publishState());
        assertEquals("EDIT", c.access().name());
    }


    @Test
    void createAlsoCreatesEmptyContent() {
        NodeDTO a = create(null, "A");
        var content = mongo.findById(a.id(), com.knowledgeforge.knowledgeforge.nodecontent.NodeContent.class);
        assertNotNull(content);
        assertEquals(0, content.getRevision());
        assertEquals(a.spaceId(), content.getSpaceId());
    }

    @Test
    void createAfterPlacesBetweenSiblings() {
        NodeDTO a = create(null, "A");
        create(null, "B");
        nodeService.create(space.getId(), new CreateNodeRequest(null, "C", a.id()));
        assertEquals(List.of("A", "C", "B"), treeTitles());
    }

    @Test
    void moveRewritesDescendantAncestors() {
        NodeDTO a = create(null, "A");
        NodeDTO b = create(a.id(), "B");
        NodeDTO c = create(b.id(), "C");
        NodeDTO d = create(null, "D");

        nodeService.move(b.id(), new MoveNodeRequest(d.id(), null, null));
        assertEquals(List.of(d.id()), nodes.findById(b.id()).orElseThrow().getAncestors());
        assertEquals(List.of(d.id(), b.id()), nodes.findById(c.id()).orElseThrow().getAncestors());
        assertEquals(d.id(), nodes.findById(b.id()).orElseThrow().getParentId());

        nodeService.move(b.id(), new MoveNodeRequest(null, null, null));
        assertEquals(List.of(), nodes.findById(b.id()).orElseThrow().getAncestors());
        assertEquals(List.of(b.id()), nodes.findById(c.id()).orElseThrow().getAncestors());
        assertNull(nodes.findById(b.id()).orElseThrow().getParentId());
    }

    @Test
    void moveReordersWithinSameParent() {
        NodeDTO a = create(null, "A");
        NodeDTO b = create(null, "B");
        NodeDTO c = create(null, "C");
        nodeService.move(c.id(), new MoveNodeRequest(null, a.id(), null));
        assertEquals(List.of("C", "A", "B"), treeTitles());
        nodeService.move(c.id(), new MoveNodeRequest(null, null, b.id()));
        assertEquals(List.of("A", "B", "C"), treeTitles());
    }

    @Test
    void moveUnderOwnDescendantRejected() {
        NodeDTO a = create(null, "A");
        NodeDTO b = create(a.id(), "B");
        NodeDTO c = create(b.id(), "C");
        assertEquals(400, statusOf(() -> nodeService.move(a.id(), new MoveNodeRequest(c.id(), null, null))));
        assertEquals(400, statusOf(() -> nodeService.move(a.id(), new MoveNodeRequest(a.id(), null, null))));
        assertTrue(nodes.findById(a.id()).orElseThrow().getAncestors().isEmpty());
        assertEquals(List.of(a.id(), b.id()), nodes.findById(c.id()).orElseThrow().getAncestors());
    }

    @Test
    void moveDeeperThan50Rejected() {
        // a parent that already sits 49 levels deep (ancestors may point at pages that no longer exist)
        Node deep = new Node();
        deep.setSpaceId(space.getId());
        deep.setRank("V");
        deep.setAncestors(new ArrayList<>(IntStream.range(0, 49).mapToObj(i -> new ObjectId().toHexString()).toList()));
        deep.setCreatedBy(new com.knowledgeforge.knowledgeforge.common.UserRef(admin.getId(), "T", null));
        deep.setUpdatedBy(deep.getCreatedBy());
        deep.setContentUpdatedAt(new java.util.Date());
        deep.setCreatedAt(new java.util.Date());
        deep.setUpdatedAt(new java.util.Date());
        deep = nodes.save(deep);

        NodeDTO x = create(null, "X");
        create(x.id(), "child of X");
        String deepId = deep.getId();
        assertEquals(400, statusOf(() -> nodeService.move(x.id(), new MoveNodeRequest(deepId, null, null))));
        assertEquals(400, statusOf(() -> {
            NodeDTO leaf = create(deepId, "level 50");
            create(leaf.id(), "level 51");
        }));
        assertTrue(nodes.findById(x.id()).orElseThrow().getAncestors().isEmpty());
    }

    @Test
    void viewerCannotCreateOrMove() {
        NodeDTO a = create(null, "A");
        NodeDTO b = create(null, "B");
        User viewer = newUser();
        join(teams.findById(space.getTeamId()).orElseThrow(), viewer, "VIEWER");
        loginAs(viewer);
        assertEquals(403, statusOf(() -> create(null, "nope")));
        assertEquals(403, statusOf(() -> create(a.id(), "nope")));
        assertEquals(403, statusOf(() -> nodeService.move(a.id(), new MoveNodeRequest(b.id(), null, null))));
        assertEquals(403, statusOf(() -> nodeService.update(a.id(), new UpdateNodeRequest("x", null, null))));
        assertEquals(List.of("A", "B"), treeTitles()); // can still read
        assertEquals("A", nodeService.get(a.id()).title());
    }

    @Test
    void outsiderCannotSeeTreeOrPage() {
        NodeDTO a = create(null, "A");
        loginAs(newUser());
        assertEquals(403, statusOf(() -> nodeService.tree(space.getId())));
        assertEquals(403, statusOf(() -> nodeService.get(a.id())));
    }

    @Test
    void restrictedPageShowsTitleOnlyToUnlistedAdmin() {
        NodeDTO a = create(null, "Secret");
        NodeDTO child = create(a.id(), "Child");
        Node secret = nodes.findById(a.id()).orElseThrow();
        Node.Restriction r = new Node.Restriction();
        r.setViewers(List.of(new ObjectId().toHexString()));
        secret.setRestriction(r);
        nodes.save(secret);

        var tree = nodeService.tree(space.getId());
        assertEquals(2, tree.size());
        assertTrue(tree.stream().allMatch(NodeSummaryDTO::titleOnly));
        assertEquals(403, statusOf(() -> nodeService.get(child.id())));
    }

    @Test
    void tagsAreNormalized() {
        NodeDTO a = create(null, "A");
        NodeDTO updated = nodeService.update(a.id(), new UpdateNodeRequest(null, null, List.of(" Java ", "JAVA", "", "Spring")));
        assertEquals(List.of("java", "spring"), updated.tags());
        assertEquals(400, statusOf(() -> nodeService.update(a.id(), new UpdateNodeRequest(null, null, List.of("x".repeat(41))))));
        List<String> twentyOne = IntStream.range(0, 21).mapToObj(i -> "t" + i).toList();
        assertEquals(400, statusOf(() -> nodeService.update(a.id(), new UpdateNodeRequest(null, null, twentyOne))));
        assertEquals(400, statusOf(() -> nodeService.update(a.id(), new UpdateNodeRequest("t".repeat(201), null, null))));
        assertEquals(List.of("java", "spring"), nodes.findById(a.id()).orElseThrow().getTags());
    }

    @Test
    void renameAndIconPersist() {
        NodeDTO a = create(null, "A");
        NodeDTO u = nodeService.update(a.id(), new UpdateNodeRequest("  New name ", "📘", null));
        assertEquals("New name", u.title());
        assertEquals("📘", u.icon());
        assertNull(nodeService.update(a.id(), new UpdateNodeRequest(null, "", null)).icon());
    }

    @Test
    void recentNodesCappedAt20() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) ids.add(create(null, "P" + i).id());
        ids.forEach(nodeService::get);
        List<User.RecentNode> recent = users.findById(admin.getId()).orElseThrow().getRecentNodes();
        assertEquals(20, recent.size());
        assertEquals(ids.get(24), recent.get(19).getNodeId());
        assertEquals(ids.get(5), recent.get(0).getNodeId());

        nodeService.get(ids.get(10)); // re-opening moves it to the end, no duplicate
        recent = users.findById(admin.getId()).orElseThrow().getRecentNodes();
        assertEquals(20, recent.size());
        assertEquals(ids.get(10), recent.get(19).getNodeId());
        assertEquals(1, recent.stream().filter(r -> r.getNodeId().equals(ids.get(10))).count());
    }

    @Test
    void manyInsertsAtFrontRebalanceInsteadOfFailing() {
        NodeDTO first = create(null, "first");
        for (int i = 0; i < 330; i++) {
            nodeService.create(space.getId(), new CreateNodeRequest(null, "n" + i, null));
            nodeService.move(nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(space.getId(), NodeStatus.ACTIVE, null)
                    .get(i + 1).getId(), new MoveNodeRequest(null, first.id(), null));
        }
        List<String> ranks = nodes.findBySpaceIdAndStatusAndParentIdOrderByRankAsc(space.getId(), NodeStatus.ACTIVE, null)
                .stream().map(Node::getRank).toList();
        assertEquals(331, ranks.size());
        assertEquals(331, ranks.stream().distinct().count());
        assertTrue(ranks.stream().allMatch(r -> r.length() <= 64));
    }
}
