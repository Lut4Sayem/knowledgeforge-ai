package com.knowledgeforge.knowledgeforge.ai;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentRepository;
import com.knowledgeforge.knowledgeforge.search.SearchResultDTO;
import com.knowledgeforge.knowledgeforge.search.SearchService;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

// AI limits, summarize and ask. Everything is mocked: no database, no network.
class AiServiceTest {

    static final String USER_ID = "u1", TEAM_ID = "t1", SPACE_ID = "s1", NODE_ID = "n1";

    AiUsageRepository usage = mock(AiUsageRepository.class);
    AiSummaryRepository summaries = mock(AiSummaryRepository.class);
    AiAnswerRepository answers = mock(AiAnswerRepository.class);
    AiClient client = mock(AiClient.class);
    NodeAccessService access = mock(NodeAccessService.class);
    NodeRepository nodes = mock(NodeRepository.class);
    NodeContentRepository contents = mock(NodeContentRepository.class);
    SearchService search = mock(SearchService.class);
    SpaceRepository spaces = mock(SpaceRepository.class);

    AiService service = new AiService(usage, summaries, answers, client, access, nodes, contents, search, spaces, 10, 1000);

    Date contentUpdatedAt = new Date(1_000_000L);
    Node node = new Node();
    NodeContent content = new NodeContent();
    Space space = new Space();
    Map<String, Access> levels = new HashMap<>(); // per node id; missing = VIEW

    @BeforeEach
    void setUp() {
        User me = new User("Me", "me@test.com", "hash");
        me.setId(USER_ID);
        when(access.currentUser()).thenReturn(me);

        space.setId(SPACE_ID);
        space.setTeamId(TEAM_ID);
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space));

        node.setId(NODE_ID);
        node.setSpaceId(SPACE_ID);
        node.setTitle("Onboarding");
        node.setContentUpdatedAt(contentUpdatedAt);
        content.setId(NODE_ID);
        content.setText("Some real content");
        when(access.requireView(NODE_ID)).thenReturn(node);
        when(contents.findById(NODE_ID)).thenAnswer(i -> Optional.of(content));

        when(summaries.findByNodeId(NODE_ID)).thenReturn(Optional.empty());
        when(answers.findBySpaceIdAndQuestionKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(0L);
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class))).thenReturn(List.of());
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.Result("- point one", 100, 20, 120));

        when(access.resolveAll(eq(USER_ID), any(Space.class), any())).thenAnswer(i -> {
            Map<String, Access> out = new HashMap<>();
            for (Node n : i.<java.util.Collection<Node>>getArgument(2)) out.put(n.getId(), levels.getOrDefault(n.getId(), Access.VIEW));
            return out;
        });
        when(search.search(anyString(), isNull(), isNull(), eq(SPACE_ID), any())).thenReturn(List.of());
        when(contents.findAllById(any())).thenReturn(List.of());
    }

    private static AiUsage usageWithTokens(int total) {
        AiUsage u = new AiUsage();
        u.setTotalTokens(total);
        return u;
    }

    private static AiSummary cachedFor(Date updatedAt) {
        AiSummary s = new AiSummary();
        s.setNodeId(NODE_ID);
        s.setSummary("- old summary");
        s.setContentUpdatedAt(updatedAt);
        s.setCreatedAt(new Date(5));
        return s;
    }

    private ResponseStatusException summarizeFails() {
        return assertThrows(ResponseStatusException.class, () -> service.summarize(NODE_ID));
    }

    // ---- limits ----

    @Test
    void underBothCapsPasses() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq("u1"), any(Date.class))).thenReturn(9L);
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class))).thenReturn(List.of(usageWithTokens(999)));
        assertDoesNotThrow(() -> service.checkLimits("u1"));
    }

    @Test
    void userCapRefusedWith429() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq("u1"), any(Date.class))).thenReturn(10L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.checkLimits("u1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals("You've used today's 10 AI requests. Resets at 00:00 UTC.", e.getReason());
    }

    @Test
    void siteTokenCapRefusedWith429() {
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class)))
                .thenReturn(List.of(usageWithTokens(600), usageWithTokens(400)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.checkLimits("u1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals("Today's AI limit for the site is used up. Try again tomorrow.", e.getReason());
    }

    @Test
    void recordUsageSavesNumbersOnly() {
        service.recordUsage("u1", "t1", "ASK", new AiClient.Result("answer text", 10, 5, 15));
        ArgumentCaptor<AiUsage> saved = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(saved.capture());
        AiUsage u = saved.getValue();
        assertEquals("u1", u.getUserId());
        assertEquals("t1", u.getTeamId());
        assertEquals("ASK", u.getFeature());
        assertEquals(15, u.getTotalTokens());
        assertNotNull(u.getCreatedAt());
    }

    // ---- summarize ----

    @Test
    void noAccessGets403AndNoAiCall() {
        when(access.requireView(NODE_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page"));
        assertEquals(HttpStatus.FORBIDDEN, summarizeFails().getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void viewerCanSummarizeBecauseOnlyViewAccessIsChecked() {
        SummaryResponse r = service.summarize(NODE_ID);
        assertEquals("- point one", r.summary());
        verify(access, never()).requireEdit(any());
    }

    @Test
    void generatesSavesAndRecordsUsage() {
        SummaryResponse r = service.summarize(NODE_ID);

        assertFalse(r.cached());
        assertFalse(r.trimmed());
        ArgumentCaptor<AiSummary> saved = ArgumentCaptor.forClass(AiSummary.class);
        verify(summaries).save(saved.capture());
        assertEquals(NODE_ID, saved.getValue().getNodeId());
        assertEquals(contentUpdatedAt, saved.getValue().getContentUpdatedAt());
        ArgumentCaptor<AiUsage> used = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(used.capture());
        assertEquals("SUMMARY", used.getValue().getFeature());
        assertEquals(TEAM_ID, used.getValue().getTeamId());
        assertEquals(120, used.getValue().getTotalTokens());
    }

    @Test
    void summaryReadsTheStoredPlainText() {
        service.summarize(NODE_ID);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertEquals("Title: Onboarding\n\nSome real content", prompt.getValue());
    }

    @Test
    void cachedSummaryReusedWhenPageUnchangedAndCostsNothing() {
        when(summaries.findByNodeId(NODE_ID)).thenReturn(Optional.of(cachedFor(new Date(contentUpdatedAt.getTime()))));
        // even a user who is over the cap still gets the cached summary
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(99L);

        SummaryResponse r = service.summarize(NODE_ID);

        assertTrue(r.cached());
        assertEquals("- old summary", r.summary());
        verifyNoInteractions(client);
        verify(usage, never()).save(any());
    }

    @Test
    void summaryRegeneratedWhenPageWasEdited() {
        when(summaries.findByNodeId(NODE_ID)).thenReturn(Optional.of(cachedFor(new Date(1))));

        SummaryResponse r = service.summarize(NODE_ID);

        assertFalse(r.cached());
        assertEquals("- point one", r.summary());
        verify(client).chat(anyString(), anyString(), anyInt());
        verify(usage).save(any());
    }

    @Test
    void emptyPageIs400AndNoAiCall() {
        content.setText("   ");
        ResponseStatusException e = summarizeFails();
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Nothing to summarize yet", e.getReason());
        verifyNoInteractions(client);
    }

    @Test
    void pageWithNoStoredContentIs400() {
        when(contents.findById(NODE_ID)).thenReturn(Optional.empty());
        assertEquals("Nothing to summarize yet", summarizeFails().getReason());
        verifyNoInteractions(client);
    }

    @Test
    void userOverCapGets429AndNoAiCall() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(10L);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, summarizeFails().getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void aiFailureGives503AndSavesNothing() {
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI is not configured on this server"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, summarizeFails().getStatusCode());
        verify(summaries, never()).save(any());
        verify(usage, never()).save(any());
    }

    @Test
    void longPageIsTrimmedAndFlagged() {
        content.setText("x".repeat(7000));

        SummaryResponse r = service.summarize(NODE_ID);

        assertTrue(r.trimmed());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().length() < 6100, "prompt should hold at most 6000 chars of content");
    }

    // ---- ask ----

    private Node page(String id, String title, String text, long updated) {
        Node n = new Node();
        n.setId(id);
        n.setSpaceId(SPACE_ID);
        n.setTitle(title);
        n.setContentUpdatedAt(new Date(updated));
        NodeContent c = new NodeContent();
        c.setId(id);
        c.setText(text);
        stored.add(c);
        return n;
    }

    List<NodeContent> stored = new ArrayList<>();

    /** The space holds these pages; search returns hits for {@code hitIds} in that order. */
    private void spaceHas(List<Node> pages, String... hitIds) {
        when(nodes.findBySpaceIdAndStatusOrderByParentIdAscRankAsc(SPACE_ID, NodeStatus.ACTIVE)).thenReturn(pages);
        when(contents.findAllById(any())).thenAnswer(i -> {
            List<String> want = new ArrayList<>();
            i.<Iterable<String>>getArgument(0).forEach(want::add);
            return stored.stream().filter(c -> want.contains(c.getId())).toList();
        });
        List<SearchResultDTO> hits = new ArrayList<>();
        for (String id : hitIds) {
            hits.add(new SearchResultDTO("PAGE", id, TEAM_ID, SPACE_ID, "S", "t", "", "", List.of(), null, false));
        }
        when(search.search(anyString(), isNull(), isNull(), eq(SPACE_ID), any())).thenReturn(hits);
    }

    @Test
    void askRejectsEmptyOrTooLongQuestion() {
        for (String bad : new String[]{null, "   ", "x".repeat(501)}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, bad));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertEquals("Question must be 1-500 characters", e.getReason());
        }
        verifyNoInteractions(client);
    }

    @Test
    void askByNonMemberGets403AndNoAiCall() {
        when(nodes.findBySpaceIdAndStatusOrderByParentIdAscRankAsc(SPACE_ID, NodeStatus.ACTIVE)).thenReturn(List.of());
        when(access.resolveAll(eq(USER_ID), any(Space.class), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this space"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void askInMissingSpaceIs404() {
        when(spaces.findById("nope")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.ask("nope", "anything")).getStatusCode());
    }

    @Test
    void askWithNoTextAnywhereSkipsTheAi() {
        spaceHas(List.of(page("a", "Empty", "   ", 1)));
        AskResponse r = service.ask(SPACE_ID, "vacation policy");
        assertEquals(0, r.usedDocuments());
        assertTrue(r.sources().isEmpty());
        verifyNoInteractions(client);
        verify(usage, never()).save(any());
    }

    @Test
    void askWithNoSearchHitFallsBackToNewestPagesAndSendsSpaceName() {
        space.setName("Handbook");
        space.setDescription("How we work");
        spaceHas(List.of(page("a", "Onboarding", "Welcome", 1), page("b", "Deploys", "Run the script", 2)));

        AskResponse r = service.ask(SPACE_ID, "What is this space about?");

        assertEquals(2, r.usedDocuments());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().startsWith("Space: Handbook - How we work"));
        assertTrue(prompt.getValue().indexOf("[1] Title: Deploys") > 0, "newest page comes first");
    }

    @Test
    void askUsesAtlasSearchHitsInTheirOrder() {
        spaceHas(List.of(page("a", "Vacation", "x", 1), page("b", "Vacation policy", "20 days", 2), page("c", "Other", "y", 3)),
                "b", "a");

        AskResponse r = service.ask(SPACE_ID, "Which vacations policies exist?");

        assertEquals(2, r.usedDocuments());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().contains("[1] Title: Vacation policy"));
        assertTrue(prompt.getValue().contains("[2] Title: Vacation\n"));
        assertFalse(prompt.getValue().contains("Other"));
        verify(search).search(eq("Which vacations policies exist?"), isNull(), isNull(), eq(SPACE_ID), any());
    }

    @Test
    void askFallsBackToNewestPagesWhenSearchFails() {
        spaceHas(List.of(page("a", "Vacation policy", "20 days", 1)));
        when(search.search(anyString(), isNull(), isNull(), eq(SPACE_ID), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("no index"));
        assertEquals(1, service.ask(SPACE_ID, "vacation policy").usedDocuments());
    }

    @Test
    void askNeverSendsAPageTheUserCannotRead() {
        spaceHas(List.of(page("a", "Salaries", "secret", 5), page("b", "Vacation policy", "20 days", 1)), "a", "b");
        levels.put("a", Access.TITLE_ONLY);

        AskResponse r = service.ask(SPACE_ID, "policy");

        assertEquals(1, r.usedDocuments());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertFalse(prompt.getValue().contains("secret"));
        assertFalse(prompt.getValue().contains("Salaries"));
    }

    @Test
    void sameQuestionOnUnchangedPagesIsServedFromCacheForFree() {
        Node p = page("a", "Vacation policy", "20 days", 5);
        spaceHas(List.of(p));
        AiAnswer saved = new AiAnswer();
        saved.setFingerprint(AiService.fingerprint(space, List.of(p)));
        saved.setAnswer("20 days [1]");
        saved.setSources(List.of(new SourceDTO(1, "a", "Vacation policy")));
        saved.setUsedDocuments(1);
        // wording, case and punctuation differ, but the normalized key is the same
        when(answers.findBySpaceIdAndQuestionKey(SPACE_ID, "how many vacation days")).thenReturn(Optional.of(saved));
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(99L); // over cap

        AskResponse r = service.ask(SPACE_ID, "  How many VACATION days?? ");

        assertTrue(r.cached());
        assertEquals("20 days [1]", r.answer());
        verifyNoInteractions(client);
        verify(usage, never()).save(any());
    }

    @Test
    void cachedAnswerIsNotSharedWithSomeoneWhoSeesDifferentPages() {
        Node open = page("a", "Vacation policy", "20 days", 5);
        Node hidden = page("b", "Salaries", "secret", 6);
        spaceHas(List.of(open, hidden), "a");
        AiAnswer forAdmin = new AiAnswer(); // saved while both pages were visible
        forAdmin.setFingerprint(AiService.fingerprint(space, List.of(open, hidden)));
        forAdmin.setAnswer("secret salary [1]");
        when(answers.findBySpaceIdAndQuestionKey(SPACE_ID, "policy")).thenReturn(Optional.of(forAdmin));
        levels.put("b", Access.NONE);

        AskResponse r = service.ask(SPACE_ID, "policy");

        assertFalse(r.cached());
        assertNotEquals("secret salary [1]", r.answer());
    }

    @Test
    void cachedAnswerIsDiscardedWhenAPageChanged() {
        spaceHas(List.of(page("a", "Vacation policy", "20 days", 5)), "a");
        AiAnswer stale = new AiAnswer();
        stale.setFingerprint("stale");
        stale.setAnswer("old");
        when(answers.findBySpaceIdAndQuestionKey(SPACE_ID, "vacation policy")).thenReturn(Optional.of(stale));

        AskResponse r = service.ask(SPACE_ID, "vacation policy");

        assertFalse(r.cached());
        verify(client).chat(anyString(), anyString(), anyInt());
        ArgumentCaptor<AiAnswer> saved = ArgumentCaptor.forClass(AiAnswer.class);
        verify(answers).save(saved.capture());
        assertEquals("vacation policy", saved.getValue().getQuestionKey());
        assertNotEquals("stale", saved.getValue().getFingerprint());
    }

    @Test
    void failedAiCallSavesNoAnswer() {
        spaceHas(List.of(page("a", "Vacation policy", "20 days", 5)), "a");
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI is busy"));
        assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        verify(answers, never()).save(any());
    }

    @Test
    void askOverUserCapGets429() {
        spaceHas(List.of(page("a", "Vacation policy", "20 days", 5)), "a");
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(10L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void askSendsOnlyTop3TrimmedAndRecordsUsage() {
        spaceHas(List.of(page("a", "Vacation", "x", 1), page("b", "Vacation policy", "y", 2),
                        page("c", "Notes", "vacation " + "z".repeat(4000), 3), page("d", "Other", "vacation", 4),
                        page("e", "Unrelated", "nothing", 5)),
                "a", "b", "c", "d");
        when(client.chat(anyString(), anyString(), anyInt())).thenReturn(new AiClient.Result("See [1].", 10, 5, 15));

        AskResponse r = service.ask(SPACE_ID, "What is the vacation policy?");

        assertEquals(3, r.usedDocuments());
        assertTrue(r.trimmed());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().contains("[3] Title:"));
        assertFalse(prompt.getValue().contains("[4]"));
        assertFalse(prompt.getValue().contains("Unrelated"));
        assertTrue(prompt.getValue().length() < 3 * 3300 + 200);
        ArgumentCaptor<AiUsage> used = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(used.capture());
        assertEquals("ASK", used.getValue().getFeature());
    }

    @Test
    void citationsMapToSentPagesIgnoreUnknownAndDeduplicate() {
        spaceHas(List.of(page("a", "Vacation", "x", 1), page("b", "Vacation policy", "y", 2)), "b", "a");
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.Result("Two [2] then [1, 2] and [9] and [1].", 10, 5, 15));

        AskResponse r = service.ask(SPACE_ID, "vacation policy");

        // [2] is cited first, so its page comes first; [9] is dropped; repeats are removed
        assertEquals(List.of(new SourceDTO(2, "a", "Vacation"), new SourceDTO(1, "b", "Vacation policy")), r.sources());
    }
}
