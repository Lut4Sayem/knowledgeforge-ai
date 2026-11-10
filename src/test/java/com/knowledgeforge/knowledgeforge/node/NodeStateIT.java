package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentRepository;
import com.knowledgeforge.knowledgeforge.nodecontent.NodeContentService;
import com.knowledgeforge.knowledgeforge.nodecontent.SaveContentRequest;
import com.knowledgeforge.knowledgeforge.support.ExplainAssert;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Publish, lock and tag listing (Task 5.2). */
class NodeStateIT extends NodeIntegrationTest {
    @Autowired NodeContentService contentService;
    @Autowired NodeContentRepository contents;
    @Autowired NodeRepository nodes;

    @Test
    void publishFlipsState() {
        NodeDTO p = createPage("A");
        assertEquals(PublishState.DRAFT, p.publishState());
        assertEquals(PublishState.PUBLISHED, nodeService.publish(p.id()).publishState());
        assertEquals(PublishState.PUBLISHED, contents.findById(p.id()).orElseThrow().getSearch().getPublishState());
        assertEquals(PublishState.DRAFT, nodeService.unpublish(p.id()).publishState());
        assertEquals(PublishState.DRAFT, contents.findById(p.id()).orElseThrow().getSearch().getPublishState());
    }

    @Test
    void viewerCannotPublishOrLock() {
        NodeDTO p = createPage("A");
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> nodeService.publish(p.id())));
        assertEquals(403, statusOf(() -> nodeService.lock(p.id())));
    }

    @Test
    void lockedPageRejectsSaveAndRename() {
        NodeDTO p = createPage("A");
        NodeDTO locked = nodeService.lock(p.id());
        assertTrue(locked.locked());
        assertEquals(admin.getId(), locked.lockedBy());

        assertEquals(403, statusOf(() -> contentService.save(p.id(), new SaveContentRequest(0L, "AQID", "x"))));
        assertEquals(403, statusOf(() -> nodeService.update(p.id(), new UpdateNodeRequest("B", null, null))));
        assertEquals(403, statusOf(() -> nodeService.publish(p.id())));
        // still readable
        assertTrue(nodeService.get(p.id()).locked());
    }

    @Test
    void canManageSurvivesLockAndArchive() {
        NodeDTO p = createPage("A");
        assertTrue(p.canManage());
        assertTrue(nodeService.lock(p.id()).canManage()); // locked: access is VIEW, but an editor can still unlock
        assertEquals(Access.VIEW, nodeService.get(p.id()).access());
        nodeService.unlock(p.id());
        mongo.updateFirst(Query.query(Criteria.where("id").is(p.id())), new Update().set("status", "ARCHIVED"), Node.class);
        assertTrue(nodeService.get(p.id()).canManage()); // archived: an editor can still unarchive
        loginAs(newMember("VIEWER"));
        assertFalse(nodeService.get(p.id()).canManage());
    }

    @Test
    void unlockOnlyByEditor() {
        NodeDTO p = createPage("A");
        nodeService.lock(p.id());
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> nodeService.unlock(p.id())));
        loginAs(newMember("EDITOR")); // another editor may unlock (domain 4.8)
        NodeDTO open = nodeService.unlock(p.id());
        assertFalse(open.locked());
        assertNull(open.lockedBy());
        assertEquals(1, contentService.save(p.id(), new SaveContentRequest(0L, "AQID", "x")).revision());
    }

    @Test
    void tagListingHidesInaccessiblePages() {
        NodeDTO mine = createPage("Mine");
        NodeDTO secret = createPage("Secret");
        NodeDTO trashed = createPage("Gone");
        for (NodeDTO n : List.of(mine, secret, trashed)) nodeService.update(n.id(), new UpdateNodeRequest(null, null, List.of("Plan")));
        nodeService.update(createPage("Other tag").id(), new UpdateNodeRequest(null, null, List.of("misc")));
        Node s = nodes.findById(secret.id()).orElseThrow();
        Node.Restriction r = new Node.Restriction();
        r.setViewers(List.of(new ObjectId().toHexString()));
        s.setRestriction(r);
        nodes.save(s);
        nodes.findById(trashed.id()).ifPresent(t -> {
            t.setStatus(NodeStatus.TRASHED);
            nodes.save(t);
        });

        var other = newMember("EDITOR"); // an unlisted editor sees neither the restricted nor the trashed page
        loginAs(other);
        assertEquals(List.of(mine.id()), nodeService.taggedPages("PLAN").stream().map(TaggedNodeDTO::id).toList());
        assertEquals("Docs", nodeService.taggedPages("plan").get(0).spaceName());

        // a user of another team sees nothing, not even a 403
        loginAs(newUser());
        assertEquals(List.of(), nodeService.taggedPages("plan"));
    }

    @Test
    void tagQueryUsesIndex() {
        ExplainAssert.usesIndex(mongo, "nodes", Query.query(Criteria.where("tags").is("plan")), "tags");
    }

}
