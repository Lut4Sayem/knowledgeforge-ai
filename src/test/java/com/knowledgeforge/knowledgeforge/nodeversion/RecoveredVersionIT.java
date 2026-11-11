package com.knowledgeforge.knowledgeforge.nodeversion;

import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.nodecontent.ContentDTO;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.nodecontent.StaleRevisionException;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D6 epoch rule: a client that is stale across a wholesale replacement keeps its copy as a private version. */
class RecoveredVersionIT extends NodeIntegrationTest {
    @Autowired NodeContentService contentService;
    @Autowired NodeVersionService versionService;
    @Autowired NodeVersionRepository versions;

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private List<NodeVersion> versionsOf(String nodeId) {
        return versions.findAll().stream().filter(v -> nodeId.equals(v.getNodeId())).toList();
    }

    @Test
    void normalSavesNeverChangeTheEpoch() {
        NodeDTO p = createPage("A");
        assertEquals(0, contentService.get(p.id()).contentEpoch());
        contentService.save(p.id(), new SaveContentRequest(0L, b64("one"), "one"));
        contentService.save(p.id(), new SaveContentRequest(1L, b64("two"), "two"));
        ContentDTO c = contentService.get(p.id());
        assertEquals(2, c.revision());
        assertEquals(0, c.contentEpoch());
    }

    @Test
    void restoreThenStaleClientDoesNotResurrectContent() {
        NodeDTO p = createPage("A");
        contentService.save(p.id(), new SaveContentRequest(0L, b64("v1"), "v1"));
        String v1 = versionService.name(p.id(), "v1").id();
        contentService.save(p.id(), new SaveContentRequest(1L, b64("v2"), "v2"));
        ContentDTO opened = contentService.get(p.id()); // the client that goes stale: revision 2, epoch 0

        versionService.restore(v1);

        ContentDTO now = contentService.get(p.id());
        assertEquals(opened.contentEpoch() + 1, now.contentEpoch()); // tells the client it must not merge
        assertEquals(opened.revision() + 1, now.revision());
        // its save is refused, so the old lineage cannot overwrite the restore
        assertThrows(StaleRevisionException.class,
                () -> contentService.save(p.id(), new SaveContentRequest(opened.revision(), b64("v1 + offline edits"), "x")));

        // instead its copy is kept as a private version and the page is untouched
        versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("v1 + offline edits"), "x", opened.contentEpoch()));
        ContentDTO after = contentService.get(p.id());
        assertEquals(b64("v1"), after.state());
        assertEquals(now.revision(), after.revision());
        assertEquals(now.contentEpoch(), after.contentEpoch());
    }

    @Test
    void recoveredNeverMerged() {
        NodeDTO p = createPage("A");
        contentService.save(p.id(), new SaveContentRequest(0L, b64("one"), "one"));
        versionService.closeSession(mongo.findById(p.id(), com.knowledgeforge.knowledgeforge.nodecontent.NodeContent.class)); // AUTO "one"
        String recovered = versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("one!"), "one!", 0)).id();
        // a recovered copy is newer than the AUTO but is not "the latest version": the next small change still merges into the AUTO
        contentService.save(p.id(), new SaveContentRequest(1L, b64("one?"), "one?"));
        versionService.closeSession(mongo.findById(p.id(), com.knowledgeforge.knowledgeforge.nodecontent.NodeContent.class));
        assertEquals(2, versionsOf(p.id()).size());
        NodeVersion r = versions.findById(recovered).orElseThrow();
        assertEquals("one!", r.getText());
        assertEquals(VersionKind.RECOVERED, r.getKind());
        assertEquals("one?", versionsOf(p.id()).stream().filter(v -> v.getKind() == VersionKind.AUTO).findFirst().orElseThrow().getText());
    }

    @Test
    void recoveredVersionIsPrivate() {
        NodeDTO p = createPage("Plan");
        User viewer = newMember("VIEWER"); // e.g. lost edit rights while offline: view access is enough
        loginAs(viewer);

        RecoveredVersionDTO saved = versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("mine"), "my text", 0));

        NodeVersion v = versions.findById(saved.id()).orElseThrow();
        assertEquals(VersionKind.RECOVERED, v.getKind());
        assertEquals(viewer.getId(), v.getPrivateTo());
        assertEquals(1, v.getAuthors().size());
        assertEquals(viewer.getId(), v.getAuthors().get(0).getUserId());
        assertEquals(p.id(), v.getNodeId());
        assertEquals(space.getId(), v.getSpaceId());
        assertEquals("Plan", v.getTitle());
        assertEquals("my text", v.getText());
        assertArrayEquals("mine".getBytes(StandardCharsets.UTF_8), v.getState());
        assertEquals(64, v.getContentHash().length());
        assertEquals(0, v.getBaseEpoch());
        assertNotNull(v.getCreatedAt());
    }

    @Test
    void recoveredRetryDoesNotCreateASecondCopy() {
        NodeDTO p = createPage("A");
        RecoveredVersionRequest req = new RecoveredVersionRequest(b64("mine"), "t", 0);
        String first = versionService.saveRecovered(p.id(), req).id();
        assertEquals(first, versionService.saveRecovered(p.id(), req).id()); // the answer was lost, the client asks again
        assertEquals(1, versionsOf(p.id()).size());

        loginAs(newMember("EDITOR")); // an identical copy from someone else is still their own
        assertNotEquals(first, versionService.saveRecovered(p.id(), req).id());
        assertEquals(2, versionsOf(p.id()).size());
    }

    @Test
    void recoveredNeedsViewAccessAndValidInput() {
        NodeDTO p = createPage("A");
        byte[] big = new byte[4 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) 7);
        assertEquals(413, statusOf(() -> versionService.saveRecovered(p.id(),
                new RecoveredVersionRequest(Base64.getEncoder().encodeToString(big), "t", 0))));
        assertEquals(413, statusOf(() -> versionService.saveRecovered(p.id(),
                new RecoveredVersionRequest(b64("x"), "t".repeat(1_000_001), 0))));
        assertEquals(400, statusOf(() -> versionService.saveRecovered(p.id(), new RecoveredVersionRequest("not base64!", "t", 0))));
        assertEquals(400, statusOf(() -> versionService.saveRecovered(p.id(), new RecoveredVersionRequest(null, "t", 0))));
        assertEquals(400, statusOf(() -> versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("x"), "t", null))));

        loginAs(newUser()); // not in the team
        assertEquals(403, statusOf(() -> versionService.saveRecovered(p.id(), new RecoveredVersionRequest(b64("x"), "t", 0))));
        assertEquals(0, versionsOf(p.id()).size());
    }
}
