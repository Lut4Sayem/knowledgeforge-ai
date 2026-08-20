package com.knowledgeforge.knowledgeforge.search;

import com.knowledgeforge.knowledgeforge.documentPkg.DocumentEntity;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentRepository;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Service
public class SearchService {

    private final TeamMemberRepository teamMemberRepository;
    private final SpaceRepository spaceRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public SearchService(
            TeamMemberRepository teamMemberRepository,
            SpaceRepository spaceRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.spaceRepository = spaceRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    public List<SearchResultDTO> search(String q) {
        if (q == null || q.trim().isEmpty()) {
            return List.of();
        }

        String query = q.trim().toLowerCase();

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // teams user belongs to
        List<TeamMember> memberships = teamMemberRepository.findByUserId(user.getId());
        List<String> teamIds = new ArrayList<>();
        for (TeamMember tm : memberships) {
            teamIds.add(tm.getTeamId());
        }

        if (teamIds.isEmpty()) {
            return List.of();
        }

        // spaces inside those teams
        List<Space> spaces = spaceRepository.findByTeamIdIn(teamIds);

        List<SearchResultDTO> results = new ArrayList<>();

        // for each space, load docs and filter
        for (Space space : spaces) {
            List<DocumentEntity> docs = documentRepository.findBySpaceId(space.getId());

            for (DocumentEntity d : docs) {
                if (matches(d, query)) {
                    results.add(toDTO(d, space, query));
                }
            }
        }

        // newest first
        results.sort(Comparator.comparing(SearchResultDTO::getUpdatedAt,
                Comparator.nullsLast(Date::compareTo)).reversed());

        return results;
    }

    private boolean matches(DocumentEntity d, String queryLower) {
        String title = safeLower(d.getTitle());
        String content = safeLower(d.getContent());

        if (title.contains(queryLower)) return true;
        if (content.contains(queryLower)) return true;

        if (d.getTags() != null) {
            for (String tag : d.getTags()) {
                if (safeLower(tag).contains(queryLower)) return true;
            }
        }

        return false;
    }

    private SearchResultDTO toDTO(DocumentEntity d, Space space, String queryLower) {
        SearchResultDTO dto = new SearchResultDTO();
        dto.setDocumentId(d.getId());
        dto.setTitle(d.getTitle());
        dto.setStatus(d.getStatus());

        dto.setSpaceId(space.getId());
        dto.setSpaceName(space.getName());
        dto.setTeamId(space.getTeamId());

        dto.setUpdatedAt(d.getUpdatedAt());
        dto.setTags(d.getTags());

        dto.setExcerpt(makeExcerpt(d.getContent(), queryLower));

        return dto;
    }

    private String makeExcerpt(String content, String queryLower) {
        if (content == null) return "";
        String clean = content.replace("\n", " ").trim();
        if (clean.length() <= 160) return clean;

        // simple excerpt: just first 160 chars (MVP)
        return clean.substring(0, 160) + "...";
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}