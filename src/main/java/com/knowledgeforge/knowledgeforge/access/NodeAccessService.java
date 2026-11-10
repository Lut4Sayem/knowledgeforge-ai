package com.knowledgeforge.knowledgeforge.access;

import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
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

    /**
     * Same as resolve, but TRASHED and ARCHIVED are judged like ACTIVE (trash list, restore, unarchive): the
     * caller already knows the node is in that state and wants to know what the user's role would allow.
     */
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

    /** Delete forever: team ADMIN, or the owner of a personal space. */
    public boolean canPurge(String userId, Space space) {
        if (space.getOwnerType() == Space.OwnerType.USER) return userId.equals(space.getOwnerUserId());
        return teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), userId)
                .map(m -> "ADMIN".equals(m.getRole())).orElse(false);
    }

    public boolean canEditSpace(String userId, Space space) {
        if (space.getOwnerType() == Space.OwnerType.USER) {
            return userId.equals(space.getOwnerUserId());
        }
        return teamMemberRepository.canEdit(space.getTeamId(), userId);
    }

    /** Logged-in user; 401 when missing. */
    public User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /**
     * Access to many nodes of one space with no extra queries: ancestors are looked up in {@code nodes} itself.
     * Throws 403 when the user cannot enter the space at all.
     */
    public Map<String, Access> resolveAll(String userId, Space space, Collection<Node> nodes) {
        return resolveAll(userId, space, nodes, false);
    }

    /** {@code ignoreStatus}: judge TRASHED/ARCHIVED nodes like ACTIVE ones (trash and archive lists). */
    public Map<String, Access> resolveAll(String userId, Space space, Collection<Node> nodes, boolean ignoreStatus) {
        Base base = base(userId, space);
        if (base == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this space");
        Map<String, Node> byId = nodes.stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        Map<String, Access> out = new HashMap<>();
        for (Node n : nodes) {
            out.put(n.getId(), finish(base, userId, n, byId, ignoreStatus, false));
        }
        return out;
    }

    private Node require(String nodeId, Access needed, boolean ignoreLock) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
        String userId = currentUser().getId();
        if (compute(userId, node, false, ignoreLock).compareTo(needed) < 0) {
            throw denied(userId, node, needed, ignoreLock);
        }
        return node;
    }

    /**
     * Says WHY, but only to a user who would have had enough access without the page's state; everybody else
     * gets the plain message so nothing leaks about pages they cannot see. The editor shows this text.
     */
    private ResponseStatusException denied(String userId, Node node, Access needed, boolean ignoreLock) {
        if (compute(userId, node, true, ignoreLock).compareTo(needed) >= 0) {
            if (node.getStatus() == NodeStatus.TRASHED) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "This page is in the trash");
            }
            if (node.getStatus() == NodeStatus.ARCHIVED) {
                return new ResponseStatusException(HttpStatus.FORBIDDEN, "This page is archived and read-only");
            }
        }
        if (!ignoreLock && node.isLocked() && compute(userId, node, false, true).compareTo(needed) >= 0) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN, "This page is locked and read-only");
        }
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page");
    }

    private record Base(Access access, boolean admin, boolean team) {}

    /** Space step (domain 4.1 step 2); null = not allowed in at all. */
    private Base base(String userId, Space space) {
        if (space.getOwnerType() == Space.OwnerType.USER) {
            // Personal space: owner only, restrictions do not apply.
            return userId.equals(space.getOwnerUserId()) ? new Base(Access.EDIT, false, false) : null;
        }
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), userId).orElse(null);
        if (member == null) return null;
        boolean admin = "ADMIN".equals(member.getRole());
        return new Base((admin || "EDITOR".equals(member.getRole())) ? Access.EDIT : Access.VIEW, admin, true);
    }

    private Access compute(String userId, Node node, boolean ignoreStatus, boolean ignoreLock) {
        Space space = spaceRepository.findById(node.getSpaceId()).orElse(null);
        if (space == null) return Access.NONE;
        Base base = base(userId, space);
        if (base == null) return Access.NONE;
        Map<String, Node> ancestors = !base.team() || node.getAncestors().isEmpty() ? Map.of()
                : nodeRepository.findAllById(node.getAncestors()).stream()
                        .collect(Collectors.toMap(Node::getId, Function.identity()));
        return finish(base, userId, node, ancestors, ignoreStatus, ignoreLock);
    }

    private Access finish(Base base, String userId, Node node, Map<String, Node> ancestors,
                          boolean ignoreStatus, boolean ignoreLock) {
        Access result = base.team() ? walkRestrictions(userId, node, base.access(), base.admin(), ancestors) : base.access();
        if (node.getStatus() == NodeStatus.TRASHED && !ignoreStatus) return Access.NONE;
        if (node.getStatus() == NodeStatus.ARCHIVED && !ignoreStatus) result = min(result, Access.VIEW);
        if (node.isLocked() && !ignoreLock) result = min(result, Access.VIEW);
        return result;
    }

    /** Rights only go down: each restriction from the top ancestor to the node can lower the result, never raise it. */
    private Access walkRestrictions(String userId, Node node, Access result, boolean admin, Map<String, Node> ancestors) {
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
