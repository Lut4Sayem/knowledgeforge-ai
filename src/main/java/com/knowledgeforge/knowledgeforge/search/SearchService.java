package com.knowledgeforge.knowledgeforge.search;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

// shortcut: filters in memory over all docs in my teams; fine at current data size,
// move to a Mongo text index / query if search gets slow.
@Service
public class SearchService {

    private static final Set<String> TYPES = Set.of("all", "documents", "teams", "spaces");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");
    private static final int EXCERPT_LENGTH = 160;

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final SpaceRepository spaceRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public SearchService(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            SpaceRepository spaceRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.spaceRepository = spaceRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param q      text to find (title/name, content/description, tags); optional if tag is given
     * @param type   all | documents | teams | spaces (default all)
     * @param tag    exact tag (case-insensitive); documents only
     * @param status DRAFT | PUBLISHED; documents only
     */
    public List<SearchResultDTO> search(String q, String type, String tag, String status) {
        String query = q == null ? "" : q.trim().toLowerCase();
        String tagFilter = tag == null || tag.isBlank() ? null : tag.trim();
        String typeFilter = type == null || type.isBlank() ? "all" : type.trim().toLowerCase();
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();

        if (!TYPES.contains(typeFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type: " + type);
        }
        if (statusFilter != null && !STATUSES.contains(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
        if (query.isEmpty() && tagFilter == null) {
            return List.of();
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Only content from teams I belong to
        List<String> teamIds = teamMemberRepository.findByUserId(user.getId()).stream()
                .map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }

        List<Team> teams = teamRepository.findByIdIn(teamIds);
        Map<String, String> teamNames = new HashMap<>();
        teams.forEach(t -> teamNames.put(t.getId(), t.getName()));

        List<Space> spaces = spaceRepository.findByTeamIdIn(teamIds);
        Map<String, Space> spacesById = new HashMap<>();
        spaces.forEach(s -> spacesById.put(s.getId(), s));

        // Tag/status only exist on documents, so they rule out team and space results
        boolean docFiltersOnly = tagFilter != null || statusFilter != null;
        List<SearchResultDTO> results = new ArrayList<>();

        if (!docFiltersOnly && (typeFilter.equals("all") || typeFilter.equals("teams"))) {
            for (Team t : teams) {
                if (contains(t.getName(), query) || contains(t.getDescription(), query)) {
                    SearchResultDTO dto = base("TEAM", t.getName(), t.getUpdatedAt());
                    dto.setTeamId(t.getId());
                    dto.setTeamName(t.getName());
                    dto.setExcerpt(makeExcerpt(t.getDescription(), query));
                    results.add(dto);
                }
            }
        }

        if (!docFiltersOnly && (typeFilter.equals("all") || typeFilter.equals("spaces"))) {
            for (Space s : spaces) {
                if (contains(s.getName(), query) || contains(s.getDescription(), query)) {
                    SearchResultDTO dto = base("SPACE", s.getName(), s.getUpdatedAt());
                    dto.setSpaceId(s.getId());
                    dto.setSpaceName(s.getName());
                    dto.setTeamId(s.getTeamId());
                    dto.setTeamName(teamNames.get(s.getTeamId()));
                    dto.setExcerpt(makeExcerpt(s.getDescription(), query));
                    results.add(dto);
                }
            }
        }

        if (typeFilter.equals("all") || typeFilter.equals("documents")) {
            List<DocumentEntity> docs = spaces.isEmpty()
                    ? List.of()
                    : documentRepository.findBySpaceIdIn(new ArrayList<>(spacesById.keySet()));

            List<DocumentEntity> matched = docs.stream()
                    .filter(d -> statusFilter == null || statusFilter.equalsIgnoreCase(d.getStatus()))
                    .filter(d -> tagFilter == null || hasTag(d, tagFilter))
                    .filter(d -> query.isEmpty() || matchesDocument(d, query))
                    .toList();

            Map<String, String> userNames = userNames(matched);
            for (DocumentEntity d : matched) {
                Space space = spacesById.get(d.getSpaceId());
                SearchResultDTO dto = base("DOCUMENT", d.getTitle(), d.getUpdatedAt());
                dto.setDocumentId(d.getId());
                dto.setStatus(d.getStatus());
                dto.setTags(d.getTags());
                dto.setSpaceId(space.getId());
                dto.setSpaceName(space.getName());
                dto.setTeamId(space.getTeamId());
                dto.setTeamName(teamNames.get(space.getTeamId()));
                dto.setUpdatedByName(userNames.get(d.getUpdatedBy()));
                dto.setExcerpt(makeExcerpt(d.getContent(), query));
                results.add(dto);
            }
        }

        // newest first
        results.sort(Comparator.comparing(SearchResultDTO::getUpdatedAt,
                Comparator.nullsLast(Date::compareTo)).reversed());
        return results;
    }

    private SearchResultDTO base(String type, String title, Date updatedAt) {
        SearchResultDTO dto = new SearchResultDTO();
        dto.setType(type);
        dto.setTitle(title);
        dto.setUpdatedAt(updatedAt);
        return dto;
    }

    private boolean matchesDocument(DocumentEntity d, String queryLower) {
        if (contains(d.getTitle(), queryLower) || contains(d.getContent(), queryLower)) return true;
        return d.getTags() != null && d.getTags().stream().anyMatch(t -> contains(t, queryLower));
    }

    private boolean hasTag(DocumentEntity d, String tag) {
        return d.getTags() != null && d.getTags().stream().anyMatch(t -> t != null && t.equalsIgnoreCase(tag));
    }

    private Map<String, String> userNames(List<DocumentEntity> docs) {
        Set<String> ids = new HashSet<>();
        docs.forEach(d -> { if (d.getUpdatedBy() != null) ids.add(d.getUpdatedBy()); });
        Map<String, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }

    /** ~160 chars of text, centred on the first match when there is one. */
    static String makeExcerpt(String text, String queryLower) {
        if (text == null) return "";
        String clean = text.replace("\n", " ").trim();
        if (clean.length() <= EXCERPT_LENGTH) return clean;

        int hit = queryLower.isEmpty() ? -1 : clean.toLowerCase().indexOf(queryLower);
        int start = hit < 0 ? 0 : Math.max(0, hit - EXCERPT_LENGTH / 3);
        int end = Math.min(clean.length(), start + EXCERPT_LENGTH);
        start = Math.max(0, end - EXCERPT_LENGTH);

        return (start > 0 ? "..." : "") + clean.substring(start, end) + (end < clean.length() ? "..." : "");
    }

    private static boolean contains(String text, String queryLower) {
        return text != null && text.toLowerCase().contains(queryLower);
    }
}
