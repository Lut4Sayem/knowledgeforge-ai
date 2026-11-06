package com.knowledgeforge.knowledgeforge.ai;

import com.knowledgeforge.knowledgeforge.documentPkg.DocumentEntity;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// AI limits and summarize flow. Everything is mocked: no database, no network.
class AiServiceTest {

    static final String EMAIL = "me@test.com", USER_ID = "u1", TEAM_ID = "t1", SPACE_ID = "s1", DOC_ID = "d1";

    AiUsageRepository usage = mock(AiUsageRepository.class);
    AiSummaryRepository summaries = mock(AiSummaryRepository.class);
    AiAnswerRepository answers = mock(AiAnswerRepository.class);
    AiClient client = mock(AiClient.class);
    DocumentService documentService = mock(DocumentService.class);
    UserRepository users = mock(UserRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);

    AiService service = new AiService(usage, summaries, answers, client, documentService, users, spaces, 10, 1000);

    Date docUpdatedAt = new Date(1_000_000L);
    DocumentEntity doc = new DocumentEntity();

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

        doc.setId(DOC_ID);
        doc.setSpaceId(SPACE_ID);
        doc.setTitle("Onboarding");
        doc.setContent("Some real content");
        doc.setUpdatedAt(docUpdatedAt);
        when(documentService.getDocumentByID(DOC_ID)).thenReturn(doc);

        when(summaries.findByDocumentId(DOC_ID)).thenReturn(Optional.empty());
        when(answers.findBySpaceIdAndQuestionKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(0L);
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class))).thenReturn(List.of());
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.Result("- point one", 100, 20, 120));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static AiUsage usageWithTokens(int total) {
        AiUsage u = new AiUsage();
        u.setTotalTokens(total);
        return u;
    }

    private static AiSummary cachedFor(Date updatedAt) {
        AiSummary s = new AiSummary();
        s.setDocumentId(DOC_ID);
        s.setSummary("- old summary");
        s.setDocumentUpdatedAt(updatedAt);
        s.setCreatedAt(new Date(5));
        return s;
    }

    private ResponseStatusException summarizeFails() {
        return assertThrows(ResponseStatusException.class, () -> service.summarize(DOC_ID));
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
    void nonMemberGets403AndNoAiCall() {
        when(documentService.getDocumentByID(DOC_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this team"));
        assertEquals(HttpStatus.FORBIDDEN, summarizeFails().getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void viewerCanSummarizeBecauseOnlyMembershipIsChecked() {
        SummaryResponse r = service.summarize(DOC_ID);
        assertEquals("- point one", r.summary());
        verify(documentService, never()).getDocumentForEdit(any());
    }

    @Test
    void generatesSavesAndRecordsUsage() {
        SummaryResponse r = service.summarize(DOC_ID);

        assertFalse(r.cached());
        assertFalse(r.trimmed());
        ArgumentCaptor<AiSummary> saved = ArgumentCaptor.forClass(AiSummary.class);
        verify(summaries).save(saved.capture());
        assertEquals(DOC_ID, saved.getValue().getDocumentId());
        assertEquals(docUpdatedAt, saved.getValue().getDocumentUpdatedAt());
        ArgumentCaptor<AiUsage> used = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(used.capture());
        assertEquals("SUMMARY", used.getValue().getFeature());
        assertEquals(TEAM_ID, used.getValue().getTeamId());
        assertEquals(120, used.getValue().getTotalTokens());
    }

    @Test
    void cachedSummaryReusedWhenDocumentUnchangedAndCostsNothing() {
        when(summaries.findByDocumentId(DOC_ID)).thenReturn(Optional.of(cachedFor(new Date(docUpdatedAt.getTime()))));
        // even a user who is over the cap still gets the cached summary
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(99L);

        SummaryResponse r = service.summarize(DOC_ID);

        assertTrue(r.cached());
        assertEquals("- old summary", r.summary());
        verifyNoInteractions(client);
        verify(usage, never()).save(any());
    }

    @Test
    void summaryRegeneratedWhenDocumentWasEdited() {
        when(summaries.findByDocumentId(DOC_ID)).thenReturn(Optional.of(cachedFor(new Date(1))));

        SummaryResponse r = service.summarize(DOC_ID);

        assertFalse(r.cached());
        assertEquals("- point one", r.summary());
        verify(client).chat(anyString(), anyString(), anyInt());
        verify(usage).save(any());
    }

    @Test
    void emptyDocumentIs400AndNoAiCall() {
        doc.setContent("   ");
        ResponseStatusException e = summarizeFails();
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Nothing to summarize yet", e.getReason());
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
    void longDocumentIsTrimmedAndFlagged() {
        doc.setContent("x".repeat(7000));

        SummaryResponse r = service.summarize(DOC_ID);

        assertTrue(r.trimmed());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().length() < 6100, "prompt should hold at most 6000 chars of content");
    }

    // ---- ask ----

    private static DocumentEntity docOf(String id, String title, String content) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setTitle(title);
        d.setContent(content);
        return d;
    }

    private void spaceHas(DocumentEntity... docs) {
        when(documentService.getDocuments(SPACE_ID)).thenReturn(List.of(docs));
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
        when(documentService.getDocuments(SPACE_ID)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void askWithNoTextAnywhereSkipsTheAi() {
        spaceHas(docOf("a", "Empty", "   "));
        AskResponse r = service.ask(SPACE_ID, "vacation policy");
        assertEquals(0, r.usedDocuments());
        assertTrue(r.sources().isEmpty());
        verifyNoInteractions(client);
        verify(usage, never()).save(any());
    }

    @Test
    void askWithNoKeywordMatchFallsBackToNewestDocsAndSendsSpaceName() {
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setTeamId(TEAM_ID);
        space.setName("Handbook");
        space.setDescription("How we work");
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space));
        DocumentEntity old = docOf("a", "Onboarding", "Welcome");
        old.setUpdatedAt(new Date(1));
        DocumentEntity fresh = docOf("b", "Deploys", "Run the script");
        fresh.setUpdatedAt(new Date(2));
        spaceHas(old, fresh);

        AskResponse r = service.ask(SPACE_ID, "What is this space about?");

        assertEquals(2, r.usedDocuments());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(anyString(), prompt.capture(), anyInt());
        assertTrue(prompt.getValue().startsWith("Space: Handbook - How we work"));
        assertTrue(prompt.getValue().indexOf("[1] Title: Deploys") > 0, "newest doc comes first");
    }

    @Test
    void askMatchesPluralAndOtherWordForms() {
        spaceHas(docOf("a", "Vacation policy", "20 days"));
        AskResponse r = service.ask(SPACE_ID, "Which vacations policies exist?");
        assertEquals(1, r.usedDocuments());
        assertEquals("vacation", AiService.stem("vacations"));
        assertEquals("policy", AiService.stem("policies"));
    }

    @Test
    void sameQuestionOnUnchangedDocsIsServedFromCacheForFree() {
        DocumentEntity d = docOf("a", "Vacation policy", "20 days");
        d.setUpdatedAt(new Date(5));
        spaceHas(d);
        Space space = spaces.findById(SPACE_ID).get();
        AiAnswer saved = new AiAnswer();
        saved.setFingerprint(AiService.fingerprint(space, List.of(d)));
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
    void cachedAnswerIsDiscardedWhenADocumentChanged() {
        DocumentEntity d = docOf("a", "Vacation policy", "20 days");
        d.setUpdatedAt(new Date(5));
        spaceHas(d);
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
        spaceHas(docOf("a", "Vacation policy", "20 days"));
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI is busy"));
        assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        verify(answers, never()).save(any());
    }

    @Test
    void askOverUserCapGets429() {
        spaceHas(docOf("a", "Vacation policy", "20 days"));
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq(USER_ID), any(Date.class))).thenReturn(10L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.ask(SPACE_ID, "vacation policy"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void askSendsOnlyTop3TrimmedAndRecordsUsage() {
        spaceHas(docOf("a", "Vacation", "x"), docOf("b", "Vacation policy", "y"),
                docOf("c", "Notes", "vacation " + "z".repeat(4000)), docOf("d", "Other", "vacation"),
                docOf("e", "Unrelated", "nothing"));
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
    void citationsMapToSentDocsIgnoreUnknownAndDeduplicate() {
        spaceHas(docOf("a", "Vacation", "x"), docOf("b", "Vacation policy", "y"));
        // best match first: "Vacation policy" (title hits both words) is [1], "Vacation" is [2]
        when(client.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.Result("Two [2] then [1, 2] and [9] and [1].", 10, 5, 15));

        AskResponse r = service.ask(SPACE_ID, "vacation policy");

        // [2] is cited first, so its doc comes first; [9] is dropped; repeats are removed
        assertEquals(List.of(new SourceDTO(2, "a", "Vacation"), new SourceDTO(1, "b", "Vacation policy")), r.sources());
    }
}
