package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContent;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.support.ExplainAssert;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeVersionIT extends com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest {
    @Autowired NodeContentService contentService;
    @Autowired NodeVersionService versionService;

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private void save(NodeDTO p, long baseRevision, String text) {
        contentService.save(p.id(), new SaveContentRequest(baseRevision, b64(text), text));
    }

    private NodeContent content(NodeDTO p) {
        return mongo.findById(p.id(), NodeContent.class);
    }

    @Test
    void publishCreatesPublishedVersion() {
        NodeDTO p = createPage("A");
        save(p, 0, "hello");
        nodeService.publish(p.id());
        List<VersionSummaryDTO> list = versionService.list(p.id());
        assertEquals(1, list.size());
        assertEquals(VersionKind.PUBLISHED, list.get(0).kind());
        assertEquals("hello", versionService.get(list.get(0).id()).text());
        assertNull(content(p).getEditSession()); // the session ended with the snapshot
        nodeService.unpublish(p.id());
        assertEquals(1, versionService.list(p.id()).size()); // unpublishing adds nothing
    }

    @Test
    void saveDuringRestoreIsKeptAsVersion() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        String v1 = versionService.name(p.id(), "first").id();
        save(p, 1, "two");
        boolean[] fired = {false};
        versionService.beforeRestoreWrite = () -> { // a save lands between closing the session and writing the old state
            if (fired[0]) return;
            fired[0] = true;
            save(p, 2, "three");
        };
        try {
            versionService.restore(v1);
        } finally {
            versionService.beforeRestoreWrite = () -> { };
        }
        assertEquals(b64("one"), contentService.get(p.id()).state());
        assertTrue(versionService.list(p.id()).stream().anyMatch(v -> "three".equals(versionService.get(v.id()).text())),
                "the save made during the restore must survive as a version");
    }

    @Test
    void publishWithoutContentChangesNothing() {
        NodeDTO p = createPage("A");
        mongo.remove(Query.query(Criteria.where("_id").is(p.id())), NodeContent.class);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> nodeService.publish(p.id()));
        assertEquals(com.knowledgeforge.knowledgeforge.node.PublishState.DRAFT, nodeService.get(p.id()).publishState());
    }

    @Test
    void restoreCreatesNewVersionAndKeepsOld() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        String v1 = versionService.name(p.id(), "first").id();
        save(p, 1, "two");
        VersionSummaryDTO restored = versionService.restore(v1);
        assertEquals(VersionKind.RESTORED, restored.kind());
        assertEquals("one", versionService.get(restored.id()).text());
        List<VersionSummaryDTO> list = versionService.list(p.id());
        // NAMED "first", AUTO "two" (closed by the restore, nothing lost), RESTORED; the old ones are untouched
        assertEquals(List.of(VersionKind.RESTORED, VersionKind.AUTO, VersionKind.NAMED), list.stream().map(VersionSummaryDTO::kind).toList());
        assertEquals("two", versionService.get(list.get(1).id()).text());
        assertEquals("one", versionService.get(v1).text());
        assertEquals(b64("one"), contentService.get(p.id()).state());
    }

    @Test
    void restoreIncrementsContentEpoch() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        String v1 = versionService.name(p.id(), "first").id();
        save(p, 1, "two");
        versionService.restore(v1);
        assertEquals(1, contentService.get(p.id()).contentEpoch());
        assertEquals(3, contentService.get(p.id()).revision());
        versionService.restore(v1);
        assertEquals(2, contentService.get(p.id()).contentEpoch());
    }

    @Test
    void namingUnchangedContentNamesTheLatestVersion() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        versionService.closeSession(content(p));
        VersionSummaryDTO named = versionService.name(p.id(), "  Draft 1 ");
        assertEquals(1, versionService.list(p.id()).size()); // renamed the AUTO, no second copy
        assertEquals(VersionKind.NAMED, named.kind());
        assertEquals("Draft 1", named.name());
        save(p, 1, "one and more");
        versionService.name(p.id(), "Draft 2"); // changed: a new version
        assertEquals(2, versionService.list(p.id()).size());
        assertEquals(400, statusOf(() -> versionService.name(p.id(), " ")));
        assertEquals(400, statusOf(() -> versionService.name(p.id(), "x".repeat(101))));
    }

    @Test
    void endSessionMakesAnAutoVersionAndClosesTheSession() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        versionService.endSession(p.id());
        assertNull(content(p).getEditSession());
        assertEquals(List.of(VersionKind.AUTO), versionService.list(p.id()).stream().map(VersionSummaryDTO::kind).toList());
        versionService.endSession(p.id()); // nothing open: no-op
        assertEquals(1, versionService.list(p.id()).size());
    }

    @Test
    void privateVersionsHiddenFromOthers() {
        NodeDTO p = createPage("A");
        String mine = versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("mine"), "mine", 0)).id();
        assertEquals(1, versionService.list(p.id()).size());
        assertTrue(versionService.list(p.id()).get(0).isPrivate());

        User other = newMember("EDITOR");
        loginAs(other);
        assertEquals(0, versionService.list(p.id()).size());
        assertEquals(404, statusOf(() -> versionService.get(mine)));
        assertEquals(404, statusOf(() -> versionService.restore(mine)));
    }

    @Test
    void viewersCannotNameOrRestore() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        String v1 = versionService.name(p.id(), "first").id();
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> versionService.name(p.id(), "x")));
        assertEquals(403, statusOf(() -> versionService.restore(v1)));
        assertEquals("one", versionService.get(v1).text()); // reading is fine
    }

    @Test
    void versionListUsesIndex() {
        NodeDTO p = createPage("A");
        Query q = Query.query(Criteria.where("nodeId").is(new org.bson.types.ObjectId(p.id()))
                .and("privateTo").in(null, new org.bson.types.ObjectId(admin.getId()))).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        ExplainAssert.usesIndex(mongo, "node_versions", q, "nodeId_createdAt"); // privateTo is a filter on the index range, no in-memory sort
        Query latest = Query.query(Criteria.where("nodeId").is(new org.bson.types.ObjectId(p.id())).and("kind").ne("RECOVERED"))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        ExplainAssert.usesIndex(mongo, "node_versions", latest, "nodeId_createdAt");
    }

    @Test
    void dueSessionsAreClosedByTheJob() {
        NodeDTO p = createPage("A");
        save(p, 0, "one");
        // fresh session: not due yet
        versionService.closeDueSessions();
        assertEquals(0, versionService.list(p.id()).size());
        // pretend the last edit was 6 minutes ago
        mongo.updateFirst(Query.query(Criteria.where("id").is(p.id())),
                new org.springframework.data.mongodb.core.query.Update().set("editSession.lastEditAt", new java.util.Date(System.currentTimeMillis() - 6 * 60_000))
                        .set("editSession.startedAt", new java.util.Date(System.currentTimeMillis() - 7 * 60_000)), NodeContent.class);
        versionService.closeDueSessions();
        assertEquals(1, versionService.list(p.id()).size());
        assertNull(content(p).getEditSession());
    }
}
