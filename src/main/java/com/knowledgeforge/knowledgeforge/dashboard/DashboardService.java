package com.knowledgeforge.knowledgeforge.dashboard;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
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
    private final NodeRepository nodeRepository;
    private final NodeAccessService access;
    private final ActivityLogRepository activityLogRepository;

    public DashboardService(UserRepository userRepository, TeamMemberRepository teamMemberRepository, TeamRepository teamRepository,
            SpaceRepository spaceRepository,
            NodeRepository nodeRepository, NodeAccessService access,
                            ActivityLogRepository activityLogRepository
    ) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.spaceRepository = spaceRepository;
        this.nodeRepository = nodeRepository;
        this.access = access;
        this.activityLogRepository = activityLogRepository;
    }

    public DashboardResponseDTO getDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

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

        // Newest pages first. Pages the user may not read (restricted) are dropped; the over-fetch covers a few of them.
        // shortcut: if more than 20 of the newest 30 are hidden the list is shorter than 10.
        List<Node> newest = spaceIds.isEmpty() ? List.of()
                : nodeRepository.findTop30BySpaceIdInAndStatusOrderByContentUpdatedAtDesc(spaceIds, NodeStatus.ACTIVE);

        List<RecentDocumentDTO> recent = new ArrayList<>();
        for (Node d : newest) {
            if (recent.size() == 10) break;
            if (access.resolve(user.getId(), d).compareTo(Access.VIEW) < 0) continue;
            Space s = spaceMap.get(d.getSpaceId());
            Team t = (s != null) ? teamMap.get(s.getTeamId()) : null;

            RecentDocumentDTO dto = new RecentDocumentDTO();
            dto.setDocumentId(d.getId());
            dto.setTitle(d.getTitle());
            dto.setStatus(d.getPublishState().name());
            dto.setUpdatedAt(d.getContentUpdatedAt() != null ? d.getContentUpdatedAt() : d.getUpdatedAt());
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

        Map<String, Integer> spacesPerTeam = new HashMap<>();
        for (Space s : spaces) spacesPerTeam.merge(s.getTeamId(), 1, Integer::sum);
        Map<String, Integer> docsPerTeam = new HashMap<>();
        int pageCount = 0;
        for (Space s : spaces) {
            int n = (int) nodeRepository.countBySpaceIdAndStatus(s.getId(), NodeStatus.ACTIVE);
            pageCount += n;
            docsPerTeam.merge(s.getTeamId(), n, Integer::sum);
        }
        List<TeamSummaryDTO> teamSummaries = new ArrayList<>();
        for (TeamMember tm : memberships) {
            Team t = teamMap.get(tm.getTeamId());
            if (t == null) continue;
            teamSummaries.add(new TeamSummaryDTO(t.getId(), t.getName(), t.getDescription(), tm.getRole(),
                    spacesPerTeam.getOrDefault(t.getId(), 0), docsPerTeam.getOrDefault(t.getId(), 0)));
        }

        DashboardResponseDTO resp = new DashboardResponseDTO();
        resp.setTeams(teamSummaries);
        resp.setTeamCount(teams.size());
        resp.setSpaceCount(spaces.size());
        resp.setDocumentCount(pageCount);
        resp.setRecentDocuments(recent);
        resp.setRecentActivity(activityOut);
        return resp;
    }
}