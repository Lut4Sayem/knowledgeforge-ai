package com.knowledgeforge.knowledgeforge.access;

import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The one permission resolver (domain spec 4.1). Everything else asks this class. */
@Service
public class NodeAccessService {
    private final NodeRepository nodeRepository;
    private final SpaceRepository spaceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public NodeAccessService(NodeRepository nodeRepository, SpaceRepository spaceRepository,
                             TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
        this.nodeRepository = nodeRepository;
        this.spaceRepository = spaceRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    // shortcut: no per-request cache, add a short (user,node) cache if the permission check shows up in profiling
    public Access resolve(String userId, Node node) {
        return compute(userId, node, false, false);
    }

    /** Same as resolve, but a TRASHED node is judged like an active one (trash list and restore). */
    public Access resolveForTrash(String userId, Node node) {
        return compute(userId, node, true, false);
    }

    public Node requireView(String nodeId) {
        return require(nodeId, Access.VIEW, false);
    }

    public Node requireEdit(String nodeId) {
        return require(nodeId, Access.EDIT, false);
    }

    /** For unlock: a locked page is still editable by its editors here. */
    public Node requireEditIgnoringLock(String nodeId) {
        return require(nodeId, Access.EDIT, true);
    }

    public boolean canEditSpace(String userId, Space space) {
        if (space.getOwnerType() == Space.OwnerType.USER) {
            return userId.equals(space.getOwnerUserId());
        }
        return teamMemberRepository.canEdit(space.getTeamId(), userId);
    }

    private Node require(String nodeId, Access needed, boolean ignoreLock) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")).getId();
        if (compute(userId, node, false, ignoreLock).compareTo(needed) < 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page");
        }
        return node;
    }

    private Access compute(String userId, Node node, boolean ignoreTrashed, boolean ignoreLock) {
        Space space = spaceRepository.findById(node.getSpaceId()).orElse(null);
        if (space == null) return Access.NONE;

        Access result;
        if (space.getOwnerType() == Space.OwnerType.USER) {
            // Personal space: owner only, restrictions do not apply.
            if (!userId.equals(space.getOwnerUserId())) return Access.NONE;
            result = Access.EDIT;
        } else {
            TeamMember member = teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), userId).orElse(null);
            if (member == null) return Access.NONE;
            boolean admin = "ADMIN".equals(member.getRole());
            result = (admin || "EDITOR".equals(member.getRole())) ? Access.EDIT : Access.VIEW;
            result = walkRestrictions(userId, node, result, admin);
        }

        if (node.getStatus() == NodeStatus.TRASHED && !ignoreTrashed) return Access.NONE;
        if (node.getStatus() == NodeStatus.ARCHIVED) result = min(result, Access.VIEW);
        if (node.isLocked() && !ignoreLock) result = min(result, Access.VIEW);
        return result;
    }

    /** Rights only go down: each restriction from the top ancestor to the node can lower the result, never raise it. */
    private Access walkRestrictions(String userId, Node node, Access result, boolean admin) {
        Map<String, Node> ancestors = node.getAncestors().isEmpty() ? Map.of()
                : nodeRepository.findAllById(node.getAncestors()).stream()
                        .collect(Collectors.toMap(Node::getId, Function.identity()));
        for (String id : node.getAncestors()) {
            Node a = ancestors.get(id);
            if (a != null) result = applyRestriction(userId, a.getRestriction(), result, admin);
        }
        return applyRestriction(userId, node.getRestriction(), result, admin);
    }

    private Access applyRestriction(String userId, Node.Restriction r, Access result, boolean admin) {
        if (r == null) return result;
        if (r.getEditors().contains(userId)) return result;
        if (r.getViewers().contains(userId)) return min(result, Access.VIEW);
        return min(result, admin ? Access.TITLE_ONLY : Access.NONE);
    }

    private static Access min(Access a, Access b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
