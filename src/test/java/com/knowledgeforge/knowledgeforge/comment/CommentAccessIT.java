package com.knowledgeforge.knowledgeforge.comment;

import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class CommentAccessIT extends NodeIntegrationTest {
    @Autowired CommentService service;

    private CreateCommentRequest body(String text) {
        CreateCommentRequest r = new CreateCommentRequest();
        r.setContent(text);
        return r;
    }

    @Test
    void commentsRespectNodeAccess() {
        NodeDTO p = createPage("A");
        CommentResponseDTO mine = service.addComment(p.id(), body("hello"));

        loginAs(newMember("VIEWER")); // VIEWERs may read and comment
        assertEquals(1, service.getComments(p.id()).size());
        CommentResponseDTO v = service.addComment(p.id(), body("question"));
        assertEquals(403, statusOf(() -> service.deleteMyComment(mine.getId()))); // not theirs
        service.deleteMyComment(v.getId());

        loginAs(newUser()); // outsider: no access to the page
        assertTrue(statusOf(() -> service.getComments(p.id())) >= 403);
        assertTrue(statusOf(() -> service.addComment(p.id(), body("x"))) >= 403);
        assertTrue(statusOf(() -> service.deleteMyComment(mine.getId())) >= 403);
    }

    @Test
    void privateCommentsAreHiddenFromOthers() {
        NodeDTO p = createPage("A");
        CommentResponseDTO c = service.addComment(p.id(), body("my private note"));
        mongo.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new org.bson.types.ObjectId(c.getId()))),
                new org.springframework.data.mongodb.core.query.Update().set("privateTo", new org.bson.types.ObjectId(admin.getId())),
                "comments");
        assertEquals(1, service.getComments(p.id()).size());
        loginAs(newMember("EDITOR"));
        assertEquals(0, service.getComments(p.id()).size());
    }

    @Test
    void emptyCommentRejected() {
        NodeDTO p = createPage("A");
        assertEquals(400, statusOf(() -> service.addComment(p.id(), body("  "))));
    }
}
