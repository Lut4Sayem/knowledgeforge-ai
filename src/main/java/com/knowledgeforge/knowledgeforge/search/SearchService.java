package com.knowledgeforge.knowledgeforge.search;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeRepository;
import com.knowledgeforge.knowledgeforge.node.NodeStatus;
import com.knowledgeforge.knowledgeforge.node.PublishState;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.Team;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.team.TeamRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Page search on the Atlas Search index nodes_search, then a permission check on every hit (task 7.1). */
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int HEAD_CHARS = 160;
    private static final String CONTENTS = "node_contents";

    private final MongoTemplate mongo;
    private final NodeAccessService access;
    private final NodeRepository nodes;
    private final SpaceRepository spaces;
    private final TeamRepository teams;
    private final TeamMemberRepository members;

    public SearchService(MongoTemplate mongo, NodeAccessService access, NodeRepository nodes,
                         SpaceRepository spaces, TeamRepository teams, TeamMemberRepository members) {
        this.mongo = mongo;
        this.access = access;
        this.nodes = nodes;
        this.spaces = spaces;
        this.teams = teams;
        this.members = members;
    }

    public List<SearchResultDTO> search(String q, String tag, String publishState, String spaceId, Integer limitParam) {
        String query = q == null ? "" : q.trim();
        String tagFilter = tag == null || tag.isBlank() ? null : tag.trim().toLowerCase();
        String stateFilter = publishState == null || publishState.isBlank() ? null : publishState.trim().toUpperCase();
        if (stateFilter != null && Arrays.stream(PublishState.values()).noneMatch(p -> p.name().equals(stateFilter))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid publishState: " + publishState);
        }
        int limit = limitParam == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limitParam, MAX_LIMIT));
        if (query.isEmpty() && tagFilter == null) return List.of();

        User user = access.currentUser();
        List<String> teamIds = members.findByUserId(user.getId()).stream().map(TeamMember::getTeamId).toList();
        List<Team> myTeams = teamIds.isEmpty() ? List.of() : teams.findByIdIn(teamIds);
        List<Space> mySpaces = new ArrayList<>(teamIds.isEmpty() ? List.of() : spaces.findByTeamIdIn(teamIds));
        spaces.findByOwnerUserId(user.getId()).ifPresent(mySpaces::add);

        List<Space> scope = spaceId == null || spaceId.isBlank() ? mySpaces
                : mySpaces.stream().filter(s -> s.getId().equals(spaceId)).toList();
        List<SearchResultDTO> out = new ArrayList<>();

        // Teams and spaces are matched by name only, so any page filter rules them out.
        if (!query.isEmpty() && tagFilter == null && stateFilter == null && (spaceId == null || spaceId.isBlank())) {
            String needle = query.toLowerCase();
            for (Team t : myTeams) {
                if (t.getName() != null && t.getName().toLowerCase().contains(needle)) {
                    out.add(new SearchResultDTO("TEAM", null, t.getId(), null, null, t.getName(), "", "", List.of(), null, false));
                }
            }
            for (Space s : mySpaces) {
                if (s.getName() != null && s.getName().toLowerCase().contains(needle)) {
                    out.add(new SearchResultDTO("SPACE", null, s.getTeamId(), s.getId(), s.getName(), s.getName(), "", "", List.of(), null, false));
                }
            }
        }
        if (!scope.isEmpty()) out.addAll(searchPages(query, tagFilter, stateFilter, scope, limit));
        return out;
    }

    private List<SearchResultDTO> searchPages(String query, String tag, String state, List<Space> scope, int limit) {
        List<Document> filter = new ArrayList<>();
        filter.add(new Document("in", new Document("path", "spaceId")
                .append("value", scope.stream().map(s -> new ObjectId(s.getId())).toList())));
        filter.add(new Document("in", new Document("path", "search.status").append("value", List.of("ACTIVE", "ARCHIVED"))));
        if (tag != null) filter.add(new Document("equals", new Document("path", "search.tags").append("value", tag)));
        if (state != null) filter.add(new Document("equals", new Document("path", "search.publishState").append("value", state)));

        Document compound = new Document("filter", filter);
        Document stage = new Document("index", "nodes_search");
        if (!query.isEmpty()) {
            Document fuzzy = new Document("maxEdits", 1);
            Document title = new Document("text", new Document("query", query).append("path", "search.title")
                    .append("fuzzy", fuzzy).append("score", new Document("boost", new Document("value", 3))));
            Document text = new Document("text", new Document("query", query).append("path", "text").append("fuzzy", fuzzy));
            compound.append("must", List.of(new Document("compound",
                    new Document("should", List.of(title, text)).append("minimumShouldMatch", 1))));
            stage.append("highlight", new Document("path", List.of("text", "search.title")));
        }
        stage.append("compound", compound);

        // Over-fetch: hits the user may not see are dropped afterwards.
        List<Document> pipeline = List.of(
                new Document("$search", stage),
                new Document("$limit", limit * 3),
                new Document("$project", new Document("head", new Document("$substrCP", List.of("$text", 0, HEAD_CHARS)))
                        .append("hl", new Document("$meta", "searchHighlights"))));
        List<Document> hits = mongo.getCollection(CONTENTS).aggregate(pipeline).into(new ArrayList<>());
        if (hits.isEmpty()) return List.of();

        Map<String, Node> found = nodes.findAllById(hits.stream().map(h -> h.getObjectId("_id").toHexString()).toList())
                .stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        Map<String, Space> spaceById = scope.stream().collect(Collectors.toMap(Space::getId, Function.identity()));
        Map<String, Map<String, Access>> bySpace = new HashMap<>();
        String userId = access.currentUser().getId();
        Lookup lookup = lookup(found.values());

        List<SearchResultDTO> out = new ArrayList<>();
        for (Document h : hits) {
            Node n = found.get(h.getObjectId("_id").toHexString());
            if (n == null) continue;
            Access a = bySpace.computeIfAbsent(n.getSpaceId(), sid -> access.resolveAll(userId, spaceById.get(sid),
                    found.values().stream().filter(x -> x.getSpaceId().equals(sid)).toList())).get(n.getId());
            if (a.compareTo(Access.TITLE_ONLY) < 0) continue;
            List<Document> hl = h.getList("hl", Document.class, List.of());
            if (a == Access.TITLE_ONLY) {
                // Title-only users must not learn what is inside: keep the hit only if the title matched.
                if (hl.stream().noneMatch(d -> "search.title".equals(d.getString("path")))) continue;
                out.add(dto(n, lookup, "", List.of()));
            } else {
                out.add(dto(n, lookup, snippet(hl, h.getString("head")), n.getTags()));
            }
            if (out.size() == limit) break;
        }
        return out;
    }

    /** Recently opened pages the user can still view, newest first (recentNodes holds newest last). */
    public List<SearchResultDTO> recent() {
        User user = access.currentUser();
        List<User.RecentNode> recent = new ArrayList<>(user.getRecentNodes());
        Collections.reverse(recent);
        Map<String, Node> found = nodes.findAllById(recent.stream().map(User.RecentNode::getNodeId).toList())
                .stream().collect(Collectors.toMap(Node::getId, Function.identity()));
        Lookup lookup = lookup(found.values());
        List<SearchResultDTO> out = new ArrayList<>();
        for (User.RecentNode r : recent) {
            Node n = found.get(r.getNodeId());
            if (n != null && access.resolve(user.getId(), n).compareTo(Access.VIEW) >= 0) {
                out.add(dto(n, lookup, "", n.getTags()));
            }
        }
        return out;
    }

    private record Lookup(Map<String, String> spaceNames, Map<String, String> titles) {}

    /** Space names and ancestor titles for a set of nodes, in two queries. */
    private Lookup lookup(Collection<Node> ns) {
        Set<String> spaceIds = ns.stream().map(Node::getSpaceId).collect(Collectors.toSet());
        Set<String> ancestorIds = ns.stream().flatMap(n -> n.getAncestors().stream()).collect(Collectors.toSet());
        Map<String, String> spaceNames = new HashMap<>();
        spaces.findAllById(spaceIds).forEach(s -> spaceNames.put(s.getId(), s.getName()));
        Map<String, String> titles = new HashMap<>();
        nodes.findAllById(ancestorIds).forEach(a -> titles.put(a.getId(), a.getTitle()));
        return new Lookup(spaceNames, titles);
    }

    private static SearchResultDTO dto(Node n, Lookup l, String snippet, List<String> tags) {
        String path = n.getAncestors().stream().map(id -> l.titles().getOrDefault(id, "")).collect(Collectors.joining(" / "));
        return new SearchResultDTO("PAGE", n.getId(), null, n.getSpaceId(), l.spaceNames().get(n.getSpaceId()),
                n.getTitle(), path, snippet, tags, n.getPublishState(), n.getStatus() == NodeStatus.ARCHIVED);
    }

    /** First text highlight with hits wrapped in mark, else the plain start of the text. Everything is HTML-escaped. */
    static String snippet(List<Document> highlights, String head) {
        for (Document h : highlights) {
            if (!"text".equals(h.getString("path"))) continue;
            StringBuilder sb = new StringBuilder();
            for (Document t : h.getList("texts", Document.class, List.of())) {
                String v = HtmlUtils.htmlEscape(t.getString("value"));
                sb.append("hit".equals(t.getString("type")) ? "<mark>" + v + "</mark>" : v);
            }
            return sb.toString();
        }
        return head == null ? "" : HtmlUtils.htmlEscape(head);
    }
}
