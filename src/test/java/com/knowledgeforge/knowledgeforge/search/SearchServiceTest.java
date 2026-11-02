package com.knowledgeforge.knowledgeforge.search;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

// Search filters and scoping. Repositories are mocked; no database needed.
class SearchServiceTest {

    UserRepository users = mock(UserRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    TeamRepository teams = mock(TeamRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    DocumentRepository documents = mock(DocumentRepository.class);

    SearchService service = new SearchService(members, teams, spaces, documents, users);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("me@test.com", null));
        User me = new User("Me", "me@test.com", "hash");
        me.setId("u1");
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(me));
        when(users.findAllById(any())).thenReturn(List.of(me));

        // I'm only in team t1. Team t2 exists but must never show up.
        TeamMember m = new TeamMember();
        m.setTeamId("t1");
        m.setUserId("u1");
        when(members.findByUserId("u1")).thenReturn(List.of(m));

        Team t1 = new Team();
        t1.setId("t1");
        t1.setName("Architecture Guild");
        when(teams.findByIdIn(List.of("t1"))).thenReturn(List.of(t1));

        Space s1 = new Space();
        s1.setId("s1");
        s1.setTeamId("t1");
        s1.setName("Backend Notes");
        when(spaces.findByTeamIdIn(List.of("t1"))).thenReturn(List.of(s1));

        when(documents.findBySpaceIdIn(anyList())).thenReturn(List.of(
                doc("d1", "Architecture guidelines", "PUBLISHED", List.of("backend"), 2),
                doc("d2", "Release checklist", "DRAFT", List.of("ops", "Backend"), 1),
                doc("d3", "Lunch menu", "PUBLISHED", List.of(), 3)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static DocumentEntity doc(String id, String title, String status, List<String> tags, long ageDays) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setSpaceId("s1");
        d.setTitle(title);
        d.setContent("Body of " + title);
        d.setStatus(status);
        d.setTags(tags);
        d.setUpdatedBy("u1");
        d.setUpdatedAt(new Date(System.currentTimeMillis() - ageDays * 86_400_000L));
        return d;
    }

    static List<String> titles(List<SearchResultDTO> results) {
        return results.stream().map(SearchResultDTO::getTitle).toList();
    }

    @Test
    void allTypesMatchTeamsSpacesAndDocuments() {
        List<SearchResultDTO> r = service.search("architecture", null, null, null);
        assertThat(r).extracting(SearchResultDTO::getType).containsExactlyInAnyOrder("TEAM", "DOCUMENT");

        assertThat(service.search("backend", null, null, null))
                .extracting(SearchResultDTO::getType).contains("SPACE", "DOCUMENT");
    }

    @Test
    void typeFilterLimitsResults() {
        assertThat(service.search("architecture", "teams", null, null))
                .extracting(SearchResultDTO::getType).containsExactly("TEAM");
        assertThat(service.search("architecture", "documents", null, null))
                .extracting(SearchResultDTO::getType).containsExactly("DOCUMENT");
    }

    @Test
    void tagFilterIsExactAndCaseInsensitiveAndWorksWithoutQuery() {
        // "backend" tag on d1, "Backend" on d2; no team/space results for a tag search
        assertThat(titles(service.search("", null, "BACKEND", null)))
                .containsExactly("Release checklist", "Architecture guidelines"); // newest first
        assertThat(service.search(null, null, "back", null)).isEmpty();
    }

    @Test
    void statusFilter() {
        assertThat(titles(service.search("e", "documents", null, "draft"))).containsExactly("Release checklist");
    }

    @Test
    void documentResultsCarryNamesForTheUi() {
        SearchResultDTO r = service.search("lunch", null, null, null).get(0);
        assertThat(r.getTeamName()).isEqualTo("Architecture Guild");
        assertThat(r.getSpaceName()).isEqualTo("Backend Notes");
        assertThat(r.getUpdatedByName()).isEqualTo("Me");
        assertThat(r.getDocumentId()).isEqualTo("d3");
    }

    @Test
    void onlyMyTeamsAreSearched() {
        service.search("anything", null, null, null);
        verify(spaces).findByTeamIdIn(List.of("t1"));
        verify(teams).findByIdIn(List.of("t1"));
    }

    @Test
    void emptySearchReturnsNothingAndBadParamsAre400() {
        assertThat(service.search("  ", null, null, null)).isEmpty();
        verifyNoInteractions(documents);

        assertThatThrownBy(() -> service.search("x", "people", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.search("x", null, null, "ARCHIVED"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void excerptIsCentredOnTheMatch() {
        String text = "a".repeat(300) + " NEEDLE " + "b".repeat(300);
        String ex = SearchService.makeExcerpt(text, "needle");
        assertThat(ex).contains("NEEDLE").startsWith("...").endsWith("...");
        assertThat(ex.length()).isLessThanOrEqualTo(160 + 6);

        assertThat(SearchService.makeExcerpt("short text", "zzz")).isEqualTo("short text");
    }
}
