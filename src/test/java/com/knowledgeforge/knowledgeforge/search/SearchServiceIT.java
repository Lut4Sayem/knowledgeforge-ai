package com.knowledgeforge.knowledgeforge.search;

import com.knowledgeforge.knowledgeforge.node.CreateNodeRequest;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.TrashService;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Needs Atlas-local (the Docker container); Atlas Search indexes new documents a moment after the write. */
class SearchServiceIT extends NodeIntegrationTest {
    @Autowired SearchService search;
    @Autowired NodeContentService contentService;
    @Autowired NodeRepository nodes;
    @Autowired TrashService trash;

    private NodeDTO page(String title, String text) {
        NodeDTO p = createPage(title);
        if (text != null) contentService.save(p.id(), new SaveContentRequest(0L, "AQID", text));
        return p;
    }

    private List<SearchResultDTO> find(String q) {
        return search.search(q, null, null, null, null);
    }

    /** Retries until the search returns something (index lag), up to 60 s. */
    private List<SearchResultDTO> awaitHits(Supplier<List<SearchResultDTO>> s) throws InterruptedException {
        List<SearchResultDTO> r = s.get();
        for (int i = 0; i < 120 && r.isEmpty(); i++) {
            Thread.sleep(500);
            r = s.get();
        }
        return r;
    }

    private static List<SearchResultDTO> pages(List<SearchResultDTO> r) {
        return r.stream().filter(x -> "PAGE".equals(x.type())).toList();
    }

    @Test
    void findsByTitleWithTypo() throws Exception {
        NodeDTO p = page("Onboarding checklist", null);
        List<SearchResultDTO> r = pages(awaitHits(() -> find("onboardng"))); // one missing letter
        assertEquals(p.id(), r.get(0).nodeId());
        assertEquals("PAGE", r.get(0).type());
    }

    @Test
    void findsByContent() throws Exception {
        NodeDTO p = page("Plain title", "The deployment runbook lives here");
        List<SearchResultDTO> r = pages(awaitHits(() -> find("runbook")));
        assertEquals(p.id(), r.get(0).nodeId());
        assertTrue(r.get(0).snippetHtml().contains("<mark>runbook</mark>"), r.get(0).snippetHtml());
        assertEquals(space.getName(), r.get(0).spaceName());
    }

    @Test
    void neverReturnsOtherTeamsPages() throws Exception {
        String word = "zebra" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        NodeDTO mine = page(word + " mine", null);

        Team team2 = new Team();
        team2.setName("Other " + UUID.randomUUID());
        team2 = teams.save(team2);
        Space space2 = new Space();
        space2.setName("Theirs");
        space2.setTeamId(team2.getId());
        space2.setOwnerType(Space.OwnerType.TEAM);
        space2 = spaces.save(space2);
        User other = newUser();
        TeamMember m = new TeamMember();
        m.setTeamId(team2.getId());
        m.setUserId(other.getId());
        m.setRole("ADMIN");
        members.save(m);
        loginAs(other);
        NodeDTO theirs = nodeService.create(space2.getId(), new CreateNodeRequest(null, word + " theirs", null));
        List<SearchResultDTO> theirHits = pages(awaitHits(() -> find(word)));
        assertEquals(List.of(theirs.id()), theirHits.stream().map(SearchResultDTO::nodeId).toList());

        loginAs(admin);
        List<SearchResultDTO> r = pages(awaitHits(() -> find(word)));
        assertEquals(List.of(mine.id()), r.stream().map(SearchResultDTO::nodeId).toList());
    }

    @Test
    void restrictedPageNotReturnedToUnlisted() throws Exception {
        String word = "secret" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        NodeDTO open = page(word + " open", null);
        NodeDTO hidden = page(word + " hidden", null);
        Node n = nodes.findById(hidden.id()).orElseThrow();
        Node.Restriction r = new Node.Restriction();
        r.setViewers(List.of());
        r.setEditors(List.of(admin.getId()));
        n.setRestriction(r);
        nodes.save(n);
        User unlisted = newMember("EDITOR");

        // the admin sees both (control: the index is ready)
        assertEquals(2, pages(awaitHits(() -> find(word))).size());
        loginAs(unlisted);
        List<SearchResultDTO> got = pages(find(word));
        assertEquals(List.of(open.id()), got.stream().map(SearchResultDTO::nodeId).toList());
    }

    @Test
    void snippetIsEscaped() throws Exception {
        page("Escaping", "alpha <script>alert(1)</script> bravo");
        List<SearchResultDTO> r = pages(awaitHits(() -> find("alpha")));
        String s = r.get(0).snippetHtml();
        assertFalse(s.contains("<script>"), s);
        assertTrue(s.contains("&lt;script&gt;"), s);
        assertTrue(s.contains("<mark>alpha</mark>"), s);
    }

    @Test
    void archivedIsReturnedAndMarked() throws Exception {
        NodeDTO p = page("Retired handbook", null);
        trash.archive(p.id());
        List<SearchResultDTO> r = pages(awaitHits(() -> find("handbook")));
        assertEquals(p.id(), r.get(0).nodeId());
        assertTrue(r.get(0).archived());
    }
}
