package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.ai.AiAnswer;
import com.knowledgeforge.knowledgeforge.ai.AiAnswerRepository;
import com.knowledgeforge.knowledgeforge.ai.AiSummary;
import com.knowledgeforge.knowledgeforge.ai.AiSummaryRepository;
import com.knowledgeforge.knowledgeforge.attachment.Attachment;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentRepository;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentStorageService;
import com.knowledgeforge.knowledgeforge.comment.Comment;
import com.knowledgeforge.knowledgeforge.comment.CommentRepository;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.editlock.EditLock;
import com.knowledgeforge.knowledgeforge.editlock.EditLockRepository;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentRepository;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.nodeversion.NodeVersion;
import com.knowledgeforge.knowledgeforge.nodeversion.VersionKind;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.support.ExplainAssert;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrashIT extends NodeIntegrationTest {
    @Autowired TrashService trash;
    @Autowired NodeRepository nodes;
    @Autowired NodeContentRepository contents;
    @Autowired NodeContentService contentService;
    @Autowired EditLockRepository locks;
    @Autowired CommentRepository comments;
    @Autowired AttachmentRepository attachments;
    @Autowired AttachmentStorageService storage;
    @Autowired AiSummaryRepository summaries;
    @Autowired AiAnswerRepository answers;

    private NodeDTO child(NodeDTO parent, String title) {
        return nodeService.create(space.getId(), new CreateNodeRequest(parent.id(), title, null));
    }

    private Node node(String id) {
        return nodes.findById(id).orElseThrow();
    }

    private ResponseStatusException failure(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    // ---------- trash + restore ----------

    @Test
    void trashMovesWholeBranch() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        NodeDTO c = child(b, "C");
        NodeDTO other = createPage("Other");

        assertEquals(2, trash.subtreeCount(a.id()).count());
        assertEquals(2, trash.trash(a.id()).count());

        for (NodeDTO n : List.of(a, b, c)) {
            Node t = node(n.id());
            assertEquals(NodeStatus.TRASHED, t.getStatus());
            assertEquals(a.id(), t.getTrashRootId());
            assertEquals(admin.getId(), t.getTrashedBy());
            assertNotNull(t.getTrashedAt());
            assertEquals(NodeStatus.TRASHED, contents.findById(n.id()).orElseThrow().getSearch().getStatus());
        }
        assertEquals(NodeStatus.ACTIVE, node(other.id()).getStatus());
        assertEquals(List.of(other.id()), nodeService.tree(space.getId()).stream().map(NodeSummaryDTO::id).toList());

        // a trashed page is gone for the normal views, with a message that says why
        ResponseStatusException e = failure(() -> nodeService.get(a.id()));
        assertEquals(404, e.getStatusCode().value());
        assertTrue(e.getReason().contains("trash"), e.getReason());
        assertEquals(404, failure(() -> contentService.save(a.id(), new SaveContentRequest(0L, "AQID", "x"))).getStatusCode().value());
    }

    @Test
    void restoreBringsBranchBack() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        trash.trash(a.id());

        assertFalse(trash.restore(a.id()).movedToTop());

        assertEquals(NodeStatus.ACTIVE, node(a.id()).getStatus());
        assertEquals(NodeStatus.ACTIVE, node(b.id()).getStatus());
        assertNull(node(b.id()).getTrashRootId());
        assertNull(node(b.id()).getTrashedAt());
        assertEquals(List.of(a.id()), node(b.id()).getAncestors());
        assertEquals(NodeStatus.ACTIVE, contents.findById(b.id()).orElseThrow().getSearch().getStatus());
        assertEquals(2, nodeService.tree(space.getId()).size());
    }

    @Test
    void restoreWithMissingParentGoesToTop() {
        NodeDTO p = createPage("P");
        NodeDTO q = child(p, "Q");
        NodeDTO c = child(q, "C");
        NodeDTO g = child(c, "G");

        trash.trash(c.id());          // branch C + G
        trash.trash(p.id());          // branch P + Q (C is already trashed on its own)
        // parent Q is trashed -> C comes back at the top level
        assertTrue(trash.restore(c.id()).movedToTop());
        assertNull(node(c.id()).getParentId());
        assertEquals(List.of(), node(c.id()).getAncestors());
        assertEquals(List.of(c.id()), node(g.id()).getAncestors()); // descendants rewritten too
        assertEquals(NodeStatus.ACTIVE, node(g.id()).getStatus());

        // parent deleted forever: same result
        NodeDTO d = createPage("D");
        NodeDTO e = child(d, "E");
        trash.trash(e.id());
        trash.trash(d.id());
        trash.deleteForever(d.id());
        assertTrue(trash.restore(e.id()).movedToTop());
        assertNull(node(e.id()).getParentId());
    }

    @Test
    void trashListShowsRootsOnlyWithPurgeDateAndParentGone() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        child(b, "C");
        child(a, "Lone");
        trash.trash(b.id());
        trash.trash(a.id());

        BranchListDTO list = trash.trashList(space.getId());
        assertTrue(list.canPurge());
        assertEquals(2, list.items().size());
        BranchDTO ra = list.items().stream().filter(i -> i.id().equals(a.id())).findFirst().orElseThrow();
        BranchDTO rb = list.items().stream().filter(i -> i.id().equals(b.id())).findFirst().orElseThrow();
        assertEquals(1, ra.descendantCount()); // Lone only: B (with C) is its own branch
        assertEquals(1, rb.descendantCount());
        assertTrue(rb.parentGone());          // B's parent A is trashed
        assertFalse(ra.parentGone());         // A is top level
        assertEquals("A", rb.path().get(0).title());
        assertEquals(Duration.ofDays(30).toMillis(), ra.purgeAt().getTime() - ra.at().getTime());
        assertEquals(admin.getId(), ra.by().getUserId());
    }

    @Test
    void cannotRestoreInsideTrashedBranch() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        trash.trash(a.id());
        assertEquals(400, failure(() -> trash.restore(b.id())).getStatusCode().value());
        assertEquals(NodeStatus.TRASHED, node(b.id()).getStatus());
        assertEquals(400, failure(() -> trash.restore(createPage("live").id())).getStatusCode().value());
    }

    // ---------- permanent delete ----------

    @Test
    void onlyAdminDeletesForever() {
        NodeDTO a = createPage("A");
        trash.trash(a.id());
        User editor = newMember("EDITOR");
        loginAs(editor);
        assertTrue(!trash.trashList(space.getId()).canPurge());
        assertEquals(403, failure(() -> trash.deleteForever(a.id())).getStatusCode().value());
        assertTrue(nodes.existsById(a.id()));
        loginAs(admin);
        trash.deleteForever(a.id());
        assertFalse(nodes.existsById(a.id()));
    }

    @Test
    void personalSpaceOwnerDeletesForever() {
        Space mine = spaces.findByOwnerUserId(admin.getId()).orElseThrow();
        NodeDTO p = nodeService.create(mine.getId(), new CreateNodeRequest(null, "Mine", null));
        trash.trash(p.id());
        assertTrue(trash.trashList(mine.getId()).canPurge());
        trash.deleteForever(p.id());
        assertFalse(nodes.existsById(p.id()));
    }

    @Test
    void deleteForeverLeavesNothingBehind() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        NodeDTO keep = createPage("Keep");
        String fileA = seed(a.id()), fileB = seed(b.id()), fileKeep = seed(keep.id());
        answers.save(answer(space.getId()));
        assertTrue(leftovers(a.id()) >= 7 && leftovers(b.id()) >= 7, "seed must exist before the delete");

        trash.trash(a.id());
        trash.deleteForever(a.id());

        for (String id : List.of(a.id(), b.id())) assertEquals(0, leftovers(id), "left behind for " + id);
        for (String f : List.of(fileA, fileB)) assertThrows(ResponseStatusException.class, () -> storage.load(f));
        assertTrue(answers.findAll().stream().noneMatch(x -> space.getId().equals(x.getSpaceId())));
        // the unrelated page keeps everything
        assertTrue(leftovers(keep.id()) >= 7);
        assertNotNull(storage.load(fileKeep));
    }

    @Test
    void purgeDeletesAfter30DaysWithFiles() {
        NodeDTO old = createPage("Old");
        NodeDTO oldChild = child(old, "Old child");
        NodeDTO fresh = createPage("Fresh");
        String file = seed(old.id());
        seed(oldChild.id());
        seed(fresh.id());
        trash.trash(old.id());
        trash.trash(fresh.id());
        Date longAgo = Date.from(Instant.now().minus(Duration.ofDays(31)));
        mongo.updateMulti(Query.query(Criteria.where("trashRootId").is(old.id())),
                new Update().set("trashedAt", longAgo), Node.class);

        assertEquals(0, trash.purgeExpired(Date.from(Instant.now().minus(Duration.ofDays(60)))));
        assertEquals(1, trash.purgeExpired(Date.from(Instant.now().minus(Duration.ofDays(30)))));

        assertEquals(0, leftovers(old.id()));
        assertEquals(0, leftovers(oldChild.id()));
        assertThrows(ResponseStatusException.class, () -> storage.load(file));
        assertTrue(nodes.existsById(fresh.id()), "a page trashed today is not purged");
        assertTrue(leftovers(fresh.id()) >= 7);
        assertEquals(0, trash.purgeExpired(Date.from(Instant.now().minus(Duration.ofDays(30)))));
    }

    @Test
    void purgeQueryUsesIndex() {
        Query q = Query.query(Criteria.where("status").is("TRASHED").and("trashedAt").lt(new Date())
                .and("trashRootId").nin(List.of(new org.bson.types.ObjectId())));
        ExplainAssert.usesIndex(mongo, "nodes", q, "trashedAt_trashed");
    }

    // ---------- archive ----------

    @Test
    void archivedIsReadOnlyAndSearchable() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        assertEquals(1, trash.archive(a.id()).count());

        assertEquals(NodeStatus.ARCHIVED, node(b.id()).getStatus());
        assertEquals(NodeStatus.ARCHIVED, contents.findById(b.id()).orElseThrow().getSearch().getStatus());
        assertEquals(0, nodeService.tree(space.getId()).size());
        assertEquals(1, trash.archivedList(space.getId()).items().size());
        assertEquals("A", trash.archivedList(space.getId()).items().get(0).title());

        // viewable, not editable, and the reason is the message
        assertEquals(NodeStatus.ARCHIVED, nodeService.get(a.id()).status());
        ResponseStatusException e = failure(() -> nodeService.update(a.id(), new UpdateNodeRequest("x", null, null)));
        assertEquals(403, e.getStatusCode().value());
        assertTrue(e.getReason().contains("archived"), e.getReason());

        assertFalse(trash.unarchive(a.id()).movedToTop());
        assertEquals(NodeStatus.ACTIVE, node(b.id()).getStatus());
        assertEquals(NodeStatus.ACTIVE, contents.findById(b.id()).orElseThrow().getSearch().getStatus());
        assertEquals(2, nodeService.tree(space.getId()).size());
    }

    @Test
    void unarchiveWithTrashedParentGoesToTop() {
        NodeDTO a = createPage("A");
        NodeDTO b = child(a, "B");
        trash.archive(b.id());
        trash.trash(a.id());
        assertTrue(trash.unarchive(b.id()).movedToTop());
        assertNull(node(b.id()).getParentId());
    }

    // ---------- trash and archive never touch each other's pages ----------

    @Test
    void trashActionsNeverTouchArchivedPagesAndArchiveNeverTouchesTrashed() {
        NodeDTO p = createPage("P");
        NodeDTO arch = child(p, "Archived on its own");
        NodeDTO archKid = child(arch, "Its child");
        NodeDTO gone = child(p, "Trashed on its own");
        trash.archive(arch.id());
        trash.trash(gone.id());
        trash.archive(p.id());   // the trashed child stays trashed, the archived one keeps its own root
        assertEquals(NodeStatus.TRASHED, node(gone.id()).getStatus());
        assertEquals(gone.id(), node(gone.id()).getTrashRootId());
        assertEquals(arch.id(), node(arch.id()).getTrashRootId());
        assertEquals(arch.id(), node(archKid.id()).getTrashRootId());

        // wrong-state calls are refused and change nothing
        assertEquals(400, failure(() -> trash.unarchive(gone.id())).getStatusCode().value());
        assertEquals(400, failure(() -> trash.restore(arch.id())).getStatusCode().value());
        assertEquals(400, failure(() -> trash.deleteForever(arch.id())).getStatusCode().value());
        assertEquals(400, failure(() -> trash.deleteForever(p.id())).getStatusCode().value());
        assertEquals(NodeStatus.TRASHED, node(gone.id()).getStatus());
        assertEquals(NodeStatus.ARCHIVED, node(arch.id()).getStatus());

        // unarchiving P brings back P only; the other two keep their state
        trash.unarchive(p.id());
        assertEquals(NodeStatus.ACTIVE, node(p.id()).getStatus());
        assertEquals(NodeStatus.TRASHED, node(gone.id()).getStatus());
        assertEquals(NodeStatus.ARCHIVED, node(arch.id()).getStatus());
        assertEquals(NodeStatus.ARCHIVED, node(archKid.id()).getStatus());
    }

    @Test
    void purgeStaysInsideItsBranch() {
        NodeDTO a = createPage("A");
        NodeDTO arch = child(a, "Archived");
        NodeDTO archKid = child(arch, "Archived kid");
        NodeDTO other = createPage("Other");
        NodeDTO solo = child(a, "Trashed earlier");
        trash.archive(arch.id());
        trash.trash(solo.id());
        trash.trash(a.id());
        String fileArch = seed(arch.id()), fileOther = seed(other.id()), fileSolo = seed(solo.id());

        trash.deleteForever(a.id());

        assertFalse(nodes.existsById(a.id()));
        for (NodeDTO kept : List.of(arch, archKid, solo, other)) assertTrue(nodes.existsById(kept.id()), kept.title());
        assertEquals(NodeStatus.ARCHIVED, node(arch.id()).getStatus());
        assertEquals(NodeStatus.TRASHED, node(solo.id()).getStatus());
        assertTrue(contents.existsById(arch.id()));
        for (String f : List.of(fileArch, fileOther, fileSolo)) assertNotNull(storage.load(f)); // solo's file goes only when ITS branch is purged

        // the daily job leaves the archived orphan alone too
        mongo.updateFirst(Query.query(Criteria.where("id").is(solo.id())),
                new Update().set("trashedAt", Date.from(Instant.now().minus(Duration.ofDays(40)))), Node.class);
        assertTrue(trash.purgeExpired(new Date(System.currentTimeMillis() - Duration.ofDays(30).toMillis())) >= 1);
        assertFalse(nodes.existsById(solo.id()));
        assertTrue(nodes.existsById(arch.id()));
    }

    @Test
    void restoreToTopRewritesAncestorsForTheWholeBranchOnly() {
        NodeDTO p = createPage("P");
        NodeDTO c = child(p, "C");
        NodeDTO g = child(c, "G");
        NodeDTO h = child(g, "H");
        NodeDTO arch = child(h, "Archived below");
        trash.archive(arch.id());
        trash.trash(c.id());
        trash.trash(p.id());

        assertTrue(trash.restore(c.id()).movedToTop());
        assertEquals(List.of(c.id()), node(g.id()).getAncestors());
        assertEquals(List.of(c.id(), g.id()), node(h.id()).getAncestors());
        assertEquals(List.of(c.id(), g.id(), h.id()), node(arch.id()).getAncestors()); // stale prefix gone here too
        assertEquals(NodeStatus.ARCHIVED, node(arch.id()).getStatus());                 // ...but not restored
        assertEquals(NodeStatus.TRASHED, node(p.id()).getStatus());
    }

    @Test
    void listsSayWhoCanRestore() {
        NodeDTO a = createPage("A");
        NodeDTO b = createPage("B");
        trash.trash(a.id());
        trash.archive(b.id());
        loginAs(newMember("VIEWER"));
        assertFalse(trash.trashList(space.getId()).items().get(0).canRestore());
        assertFalse(trash.archivedList(space.getId()).items().get(0).canRestore());
        loginAs(newMember("EDITOR"));
        assertTrue(trash.trashList(space.getId()).items().get(0).canRestore());
        assertTrue(trash.archivedList(space.getId()).items().get(0).canRestore());
    }

    @Test
    void outsiderAndViewerCannotTrashOrSeeLists() {
        NodeDTO a = createPage("A");
        loginAs(newMember("VIEWER"));
        assertEquals(403, failure(() -> trash.trash(a.id())).getStatusCode().value());
        assertEquals(403, failure(() -> trash.archive(a.id())).getStatusCode().value());
        loginAs(newUser());
        assertEquals(403, failure(() -> trash.trashList(space.getId())).getStatusCode().value());
    }

    @Test
    void lockedPageCannotBeTrashedWithAMessage() {
        NodeDTO a = createPage("A");
        mongo.updateFirst(Query.query(Criteria.where("id").is(a.id())), new Update().set("locked", true), Node.class);
        ResponseStatusException e = failure(() -> trash.trash(a.id()));
        assertEquals(403, e.getStatusCode().value());
        assertTrue(e.getReason().contains("locked"), e.getReason());
    }

    // ---------- seeding ----------

    /** One of everything a page can own; returns the GridFS file id. */
    private String seed(String nodeId) {
        Date now = new Date();
        User u = admin;
        for (VersionKind kind : List.of(VersionKind.AUTO, VersionKind.RECOVERED)) {
            NodeVersion v = new NodeVersion();
            v.setNodeId(nodeId);
            v.setSpaceId(space.getId());
            v.setKind(kind);
            v.setTitle("t");
            v.setState(new byte[]{1, 2, 3});
            v.setText("t");
            v.setContentHash(kind.name());
            v.setAuthors(List.of(new UserRef(u.getId(), "x", null)));
            if (kind == VersionKind.RECOVERED) {
                v.setPrivateTo(u.getId());
                v.setBaseEpoch(0);
            }
            v.setCreatedAt(now);
            v.setUpdatedAt(now);
            mongo.insert(v);
        }
        Comment c = new Comment();
        c.setDocumentId(nodeId);
        c.setContent("hi");
        c.setCreatedBy(u.getId());
        c.setCreatedAt(now);
        comments.save(c);

        String fileId = storage.store(new ByteArrayInputStream(new byte[]{9, 9}), "text/plain");
        Attachment a = new Attachment();
        a.setNodeId(nodeId);
        a.setOriginalName("f.txt");
        a.setFileId(fileId);
        a.setContentType("text/plain");
        a.setSize(2L);
        a.setUploadedBy(u.getId());
        a.setUploadedAt(now);
        attachments.save(a);

        EditLock l = new EditLock();
        l.setId(nodeId);
        l.setHolder(new UserRef(u.getId(), "x", null));
        l.setExpiresAt(Date.from(Instant.now().plusSeconds(600)));
        locks.save(l);

        AiSummary s = new AiSummary();
        s.setDocumentId(nodeId);
        s.setSummary("sum");
        s.setDocumentUpdatedAt(now);
        s.setCreatedAt(now);
        summaries.save(s);
        return fileId;
    }

    private AiAnswer answer(String spaceId) {
        AiAnswer a = new AiAnswer();
        a.setSpaceId(spaceId);
        a.setQuestionKey("q");
        a.setFingerprint("f");
        a.setAnswer("a");
        a.setCreatedAt(new Date());
        return a;
    }

    /** Rows still referring to the page, across every collection a page owns. */
    private long leftovers(String id) {
        long n = 0;
        n += nodes.existsById(id) ? 1 : 0;
        n += contents.existsById(id) ? 1 : 0;
        n += mongo.count(Query.query(Criteria.where("nodeId").is(id)), NodeVersion.class);
        n += comments.findByDocumentIdOrderByCreatedAtAsc(id).size();
        n += attachments.findByNodeId(id).size();
        n += locks.existsById(id) ? 1 : 0;
        n += summaries.findByDocumentId(id).isPresent() ? 1 : 0;
        return n;
    }
}
