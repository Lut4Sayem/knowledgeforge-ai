package com.knowledgeforge.knowledgeforge.nodecontent;

import com.knowledgeforge.knowledgeforge.node.Node;
import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.node.UpdateNodeRequest;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class NodeContentServiceIT extends NodeIntegrationTest {
    static final int MB4 = 4 * 1024 * 1024;

    @Autowired NodeContentService contentService;
    @Autowired NodeContentRepository contents;

    private static String b64(int bytes) {
        byte[] b = new byte[bytes];
        Arrays.fill(b, (byte) 7);
        return Base64.getEncoder().encodeToString(b);
    }

    private static SaveContentRequest save(long base, String state, String text) {
        return new SaveContentRequest(base, state, text);
    }

    @Test
    void saveIncrementsRevision() {
        NodeDTO p = createPage("A");
        assertEquals(0, contentService.get(p.id()).revision());
        assertEquals(1, contentService.save(p.id(), save(0, b64(10), "hello")).revision());
        assertEquals(2, contentService.save(p.id(), save(1, b64(12), "hello again")).revision());

        ContentDTO loaded = contentService.get(p.id());
        assertEquals(2, loaded.revision());
        assertEquals(b64(12), loaded.state());
        assertEquals("YJS_BLOCKNOTE_V1", loaded.format());
        NodeContent c = contents.findById(p.id()).orElseThrow();
        assertEquals("hello again", c.getText());
        assertEquals(64, c.getContentHash().length());
        assertEquals(List.of(admin.getId()), c.getEditSession().getAuthors());
        assertNotNull(c.getEditSession().getStartedAt());
    }

    @Test
    void sessionKeepsStartAndDedupesAuthors() {
        NodeDTO p = createPage("A");
        contentService.save(p.id(), save(0, b64(5), "x"));
        var first = contents.findById(p.id()).orElseThrow().getEditSession().getStartedAt();
        contentService.save(p.id(), save(1, b64(6), "y"));
        var s = contents.findById(p.id()).orElseThrow().getEditSession();
        assertEquals(first, s.getStartedAt());
        assertEquals(1, s.getAuthors().size());
        loginAs(newMember("EDITOR"));
        contentService.save(p.id(), save(2, b64(7), "z"));
        assertEquals(2, contents.findById(p.id()).orElseThrow().getEditSession().getAuthors().size());
    }

    @Test
    void saveSetsNodeUpdatedBy() {
        NodeDTO p = createPage("A");
        User editor = newMember("EDITOR");
        loginAs(editor);
        contentService.save(p.id(), save(0, b64(5), "x"));
        Node n = mongo.findById(p.id(), Node.class);
        assertEquals(editor.getId(), n.getUpdatedBy().getUserId());
        assertTrue(n.getContentUpdatedAt().getTime() >= p.contentUpdatedAt().getTime());
    }

    @Test
    void textStartingWithDollarIsStoredLiterally() {
        NodeDTO p = createPage("$title");
        contentService.save(p.id(), save(0, b64(5), "$revision"));
        NodeContent c = contents.findById(p.id()).orElseThrow();
        assertEquals("$revision", c.getText());
        assertEquals("$title", c.getSearch().getTitle());
    }

    @Test
    void staleRevisionReturns409() {
        NodeDTO p = createPage("A");
        contentService.save(p.id(), save(0, b64(5), "mine"));
        var e = assertThrows(StaleRevisionException.class, () -> contentService.save(p.id(), save(0, b64(6), "stale")));
        assertEquals(1, e.getCurrentRevision());
        assertEquals("mine", contents.findById(p.id()).orElseThrow().getText());
    }

    @Test
    void twoSavesOnSameRevisionOnlyOneWins() throws Exception {
        NodeDTO p = createPage("A");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier go = new CyclicBarrier(2);
        List<Callable<Boolean>> jobs = List.of(0, 1).stream().<Callable<Boolean>>map(i -> () -> {
            loginAs(admin);
            go.await();
            try {
                contentService.save(p.id(), save(0, b64(5 + i), "t" + i));
                return true;
            } catch (StaleRevisionException e) {
                return false;
            }
        }).toList();
        int wins = 0;
        for (Future<Boolean> f : pool.invokeAll(jobs)) if (f.get()) wins++;
        pool.shutdown();
        assertEquals(1, wins);
        assertEquals(1, contents.findById(p.id()).orElseThrow().getRevision());
    }

    @Test
    void stateOverLimitIsRejectedAndLimitIsAllowed() {
        NodeDTO p = createPage("A");
        assertEquals(413, statusOf(() -> contentService.save(p.id(), save(0, b64(MB4 + 1), ""))));
        assertEquals(0, contents.findById(p.id()).orElseThrow().getRevision());
        assertEquals(1, contentService.save(p.id(), save(0, b64(MB4), "")).revision());
    }

    @Test
    void textOverLimitIsRejected() {
        NodeDTO p = createPage("A");
        assertEquals(413, statusOf(() -> contentService.save(p.id(), save(0, b64(5), "x".repeat(1_000_001)))));
        assertEquals(1, contentService.save(p.id(), save(0, b64(5), "x".repeat(1_000_000))).revision());
    }

    @Test
    void badInputIs400() {
        NodeDTO p = createPage("A");
        assertEquals(400, statusOf(() -> contentService.save(p.id(), save(0, "!!not base64!!", ""))));
        assertEquals(400, statusOf(() -> contentService.save(p.id(), new SaveContentRequest(null, b64(5), ""))));
        assertEquals(400, statusOf(() -> contentService.save(p.id(), new SaveContentRequest(0L, null, ""))));
    }

    @Test
    void viewerCannotSaveButCanRead() {
        NodeDTO p = createPage("A");
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> contentService.save(p.id(), save(0, b64(5), "x"))));
        assertEquals(0, contentService.get(p.id()).revision());
    }

    @Test
    void outsiderCannotReadOrSave() {
        NodeDTO p = createPage("A");
        loginAs(newUser());
        assertEquals(403, statusOf(() -> contentService.get(p.id())));
        assertEquals(403, statusOf(() -> contentService.save(p.id(), save(0, b64(5), "x"))));
    }

    @Test
    void lockedPageCannotBeSaved() {
        NodeDTO p = createPage("A");
        mongo.updateFirst(Query.query(Criteria.where("id").is(p.id())), new Update().set("locked", true), Node.class);
        assertEquals(403, statusOf(() -> contentService.save(p.id(), save(0, b64(5), "x"))));
    }

    @Test
    void unknownPageIs404() {
        assertEquals(404, statusOf(() -> contentService.get(new org.bson.types.ObjectId().toHexString())));
    }

    @Test
    void renameUpdatesSearchCopy() {
        NodeDTO p = createPage("Old");
        nodeService.update(p.id(), new UpdateNodeRequest("New", null, List.of("Alpha", "beta")));
        var s = contents.findById(p.id()).orElseThrow().getSearch();
        assertEquals("New", s.getTitle());
        assertEquals(List.of("alpha", "beta"), s.getTags());
        contentService.save(p.id(), save(0, b64(5), "x"));
        assertEquals("New", contents.findById(p.id()).orElseThrow().getSearch().getTitle());
    }
}
