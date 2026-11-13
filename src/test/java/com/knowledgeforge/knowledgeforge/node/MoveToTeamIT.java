package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.comment.Comment;
import com.knowledgeforge.knowledgeforge.comment.CommentRepository;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersion;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersionRepository;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersionService;
import com.knowledgeforge.knowledgeforge.nodeversion.VersionKind;
import com.knowledgeforge.knowledgeforge.nodeversion.VersionSummaryDTO;
import com.knowledgeforge.knowledgeforge.search.SearchResultDTO;
import com.knowledgeforge.knowledgeforge.search.SearchService;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceService;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class MoveToTeamIT extends NodeIntegrationTest {
    @Autowired SpaceService spaceService;
    @Autowired MoveToTeamService moveToTeam;
    @Autowired NodeContentService contentService;
    @Autowired NodeVersionService versionService;
    @Autowired NodeVersionRepository versionRepo;
    @Autowired CommentRepository commentRepo;
    @Autowired NodeRepository nodes;
    @Autowired SearchService search;
    @Autowired TrashService trash;

    Space mine; // admin's personal space

    @BeforeEach
    void personalSpace() {
        mine = spaceService.createPersonalSpace(admin.getId());
    }

    private NodeDTO privatePage(String title, String parentId) {
        NodeDTO p = nodeService.create(mine.getId(), new CreateNodeRequest(parentId, title, null));
        contentService.save(p.id(), new SaveContentRequest(0L, "AQID", "text of " + title));
        return p;
    }

    private MoveToTeamRequest into(String parentId) {
        return new MoveToTeamRequest(space.getId(), parentId, null, null);
    }

    private void comment(String nodeId) {
        Comment c = new Comment();
        c.setDocumentId(nodeId);
        c.setContent("private note");
        c.setCreatedBy(admin.getId());
        c.setCreatedAt(new Date());
        commentRepo.save(c);
    }

    @Test
    void moveToTeamMakesOldVersionsPrivate() {
        NodeDTO a = privatePage("Notes", null);
        NodeDTO b = privatePage("Sub", a.id());
        versionService.createVersion(a.id(), VersionKind.NAMED, "draft 1");
        comment(a.id());
        comment(b.id());

        NodeDTO moved = moveToTeam.moveToTeam(a.id(), into(null));

        assertEquals(space.getId(), moved.spaceId());
        Node sub = nodes.findById(b.id()).orElseThrow();
        assertEquals(space.getId(), sub.getSpaceId());
        assertEquals(List.of(a.id()), sub.getAncestors());
        assertNull(nodes.findById(a.id()).orElseThrow().getRestriction());
        for (String id : List.of(a.id(), b.id())) {
            assertEquals(space.getId(), mongo.findById(id, NodeContent.class).getSpaceId());
            List<NodeVersion> vs = versionRepo.findAll().stream().filter(v -> v.getNodeId().equals(id)).toList();
            assertFalse(vs.isEmpty());
            for (NodeVersion v : vs) {
                assertEquals(space.getId(), v.getSpaceId());
                if (v.getKind() == VersionKind.PUBLISHED_TO_TEAM) assertNull(v.getPrivateTo());
                else assertEquals(admin.getId(), v.getPrivateTo());
            }
            assertEquals(1, vs.stream().filter(v -> v.getKind() == VersionKind.PUBLISHED_TO_TEAM).count());
            List<Comment> cs = commentRepo.findByDocumentIdOrderByCreatedAtAsc(id);
            assertEquals(1, cs.size());
            assertEquals(admin.getId(), cs.get(0).getPrivateTo());
        }
        // the mover still sees the old ones
        assertTrue(versionService.list(a.id()).stream().anyMatch(v -> "draft 1".equals(v.name())));
    }

    @Test
    void movesUnderATeamParentAndRewritesDescendantPaths() {
        NodeDTO parent = createPage("Methodology");
        NodeDTO a = privatePage("Notes", null);
        NodeDTO b = privatePage("Sub", a.id());
        moveToTeam.moveToTeam(a.id(), into(parent.id()));
        assertEquals(parent.id(), nodes.findById(a.id()).orElseThrow().getParentId());
        assertEquals(List.of(parent.id(), a.id()), nodes.findById(b.id()).orElseThrow().getAncestors());
        assertEquals(space.getId(), nodes.findById(b.id()).orElseThrow().getSpaceId());
    }

    @Test
    void trashedSubPageBlocksTheMove() {
        NodeDTO a = privatePage("Notes", null);
        NodeDTO b = privatePage("Sub", a.id());
        trash.trash(b.id());
        assertEquals(400, statusOf(() -> moveToTeam.moveToTeam(a.id(), into(null))));
        assertEquals(mine.getId(), nodes.findById(a.id()).orElseThrow().getSpaceId());
    }

    @Test
    void movingTwiceAndTeamToTeamAreRejected() {
        NodeDTO a = privatePage("Notes", null);
        moveToTeam.moveToTeam(a.id(), into(null));
        assertEquals(400, statusOf(() -> moveToTeam.moveToTeam(a.id(), into(null))));
    }

    @Test
    void parentFromAnotherSpaceRejected() {
        NodeDTO a = privatePage("Notes", null);
        NodeDTO other = nodeService.create(mine.getId(), new CreateNodeRequest(null, "Private parent", null));
        assertEquals(400, statusOf(() -> moveToTeam.moveToTeam(a.id(), into(other.id()))));
    }

    @Test
    void teamSeesOnlyPublishedToTeamVersion() {
        NodeDTO a = privatePage("Notes", null);
        versionService.createVersion(a.id(), VersionKind.NAMED, "draft 1");
        moveToTeam.moveToTeam(a.id(), into(null));

        loginAs(newMember("EDITOR"));
        List<VersionSummaryDTO> seen = versionService.list(a.id());
        assertEquals(1, seen.size());
        assertEquals(VersionKind.PUBLISHED_TO_TEAM, seen.get(0).kind());
    }

    @Test
    void viewerCannotMoveIntoTeam() {
        User viewer = newMember("VIEWER");
        loginAs(viewer);
        Space vMine = spaceService.createPersonalSpace(viewer.getId());
        NodeDTO p = nodeService.create(vMine.getId(), new CreateNodeRequest(null, "Mine", null));
        assertEquals(403, statusOf(() -> moveToTeam.moveToTeam(p.id(), into(null))));
        assertEquals(vMine.getId(), nodes.findById(p.id()).orElseThrow().getSpaceId());
    }

    @Test
    void teamToPersonalRejected() {
        NodeDTO t = createPage("Team page");
        assertEquals(400, statusOf(() -> moveToTeam.moveToTeam(t.id(),
                new MoveToTeamRequest(mine.getId(), null, null, null))));
        assertEquals(space.getId(), nodes.findById(t.id()).orElseThrow().getSpaceId());
    }

    @Test
    void movedPageIsSearchableInTeamAndNotInPrivateSpace() throws Exception {
        String word = "quokka" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        NodeDTO a = nodeService.create(mine.getId(), new CreateNodeRequest(null, word + " plan", null));
        assertFalse(awaitHits(() -> hits(word, mine.getId())).isEmpty()); // indexed while private

        moveToTeam.moveToTeam(a.id(), into(null));

        assertFalse(awaitHits(() -> hits(word, space.getId())).isEmpty());
        Thread.sleep(3000); // index lag: a stale private-space entry would show up by now
        assertTrue(hits(word, mine.getId()).isEmpty());
        loginAs(newMember("VIEWER"));
        assertEquals(a.id(), hits(word, space.getId()).get(0).nodeId());
    }

    private List<SearchResultDTO> hits(String q, String spaceId) {
        return search.search(q, null, null, spaceId, null).stream().filter(x -> "PAGE".equals(x.type())).toList();
    }

    private static List<SearchResultDTO> awaitHits(Supplier<List<SearchResultDTO>> s) throws InterruptedException {
        List<SearchResultDTO> r = s.get();
        for (int i = 0; i < 120 && r.isEmpty(); i++) {
            Thread.sleep(500);
            r = s.get();
        }
        return r;
    }
}
