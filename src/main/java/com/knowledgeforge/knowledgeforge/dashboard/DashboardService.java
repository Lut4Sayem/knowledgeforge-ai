package com.knowledgeforge.knowledgeforge.dashboard;

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
import com.knowledgeforge.knowledgeforge.activity.ActivityLog;
import com.knowledgeforge.knowledgeforge.activity.ActivityLogRepository;

import java.util.*;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final SpaceRepository spaceRepository;
    private final DocumentRepository documentRepository;
    private final ActivityLogRepository activityLogRepository;

    public DashboardService(UserRepository userRepository, TeamMemberRepository teamMemberRepository, TeamRepository teamRepository,
            SpaceRepository spaceRepository,
            DocumentRepository documentRepository,
                            ActivityLogRepository activityLogRepository
    ) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.spaceRepository = spaceRepository;
        this.documentRepository = documentRepository;
        this.activityLogRepository = activityLogRepository;
    }

    public DashboardResponseDTO getDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<TeamMember> memberships = teamMemberRepository.findByUserId(user.getId());
        List<String> teamIds = new ArrayList<>();
        for (TeamMember tm : memberships) {
            teamIds.add(tm.getTeamId());
        }

        List<Team> teams = teamIds.isEmpty() ? List.of() : teamRepository.findByIdIn(teamIds);
        List<Space> spaces = teamIds.isEmpty() ? List.of() : spaceRepository.findByTeamIdIn(teamIds);

        Map<String, Team> teamMap = new HashMap<>();
        for (Team t : teams) teamMap.put(t.getId(), t);
        List<ActivityLog> logs = teamIds.isEmpty()
                ? List.of()
                : activityLogRepository.findTop20ByTeamIdInOrderByCreatedAtDesc(teamIds);

        List<RecentActivityDTO> activityOut = new ArrayList<>();
        for (ActivityLog log : logs) {
            RecentActivityDTO dto = new RecentActivityDTO();
            dto.setId(log.getId());
            dto.setTeamId(log.getTeamId());
            dto.setAction(log.getAction());
            dto.setTargetId(log.getTargetId());
            dto.setTargetTitle(log.getTargetTitle());
            dto.setCreatedAt(log.getCreatedAt());
            dto.setActorUserId(log.getUserId());

            Team t = teamMap.get(log.getTeamId());
            if (t != null) dto.setTeamName(t.getName());

            userRepository.findById(log.getUserId()).ifPresent(u -> {
                dto.setActorFullName(u.getFullName());
                dto.setActorEmail(u.getEmail());
            });

            activityOut.add(dto);
        }

        Map<String, Space> spaceMap = new HashMap<>();
        List<String> spaceIds = new ArrayList<>();
        for (Space s : spaces) {
            spaceMap.put(s.getId(), s);
            spaceIds.add(s.getId());
        }

        List<DocumentEntity> docs = spaceIds.isEmpty()
                ? List.of()
                : documentRepository.findBySpaceIdIn(spaceIds);

        // sort newest first
        List<DocumentEntity> sorted = new ArrayList<>(docs);
        sorted.sort((a, b) -> {
            Date da = a.getUpdatedAt();
            Date db = b.getUpdatedAt();
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        List<RecentDocumentDTO> recent = new ArrayList<>();
        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            DocumentEntity d = sorted.get(i);
            Space s = spaceMap.get(d.getSpaceId());
            Team t = (s != null) ? teamMap.get(s.getTeamId()) : null;

            RecentDocumentDTO dto = new RecentDocumentDTO();
            dto.setDocumentId(d.getId());
            dto.setTitle(d.getTitle());
            dto.setStatus(d.getStatus());
            dto.setUpdatedAt(d.getUpdatedAt());
            dto.setTags(d.getTags());

            if (s != null) {
                dto.setSpaceId(s.getId());
                dto.setSpaceName(s.getName());
                dto.setTeamId(s.getTeamId());
            }
            if (t != null) {
                dto.setTeamName(t.getName());
            }

            recent.add(dto);
        }

        DashboardResponseDTO resp = new DashboardResponseDTO();
        resp.setTeamCount(teams.size());
        resp.setSpaceCount(spaces.size());
        resp.setDocumentCount(docs.size());
        resp.setRecentDocuments(recent);
        resp.setRecentActivity(activityOut);
        return resp;
    }
}