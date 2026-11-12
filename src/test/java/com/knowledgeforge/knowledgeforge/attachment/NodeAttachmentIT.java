package com.knowledgeforge.knowledgeforge.attachment;

import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.node.TrashService;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class NodeAttachmentIT extends NodeIntegrationTest {
    @Autowired AttachmentService service;
    @Autowired AttachmentRepository attachments;
    @Autowired AttachmentStorageService storage;
    @Autowired TrashService trash;

    private Attachment upload(NodeDTO p) {
        return service.uploadNodeFile(p.id(), new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1, 2, 3}));
    }

    @Test
    void uploadRequiresEditOnNode() {
        NodeDTO p = createPage("A");
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> upload(p)));
        loginAs(admin);
        Attachment a = upload(p);
        assertEquals(p.id(), a.getNodeId());
        assertEquals(1, service.getNodeAttachments(p.id()).size());
    }

    @Test
    void viewerCanDownload() {
        NodeDTO p = createPage("A");
        Attachment a = upload(p);
        loginAs(newMember("VIEWER"));
        assertEquals(1, service.getNodeAttachments(p.id()).size());
        assertNotNull(service.download(a.getId()).content());
    }

    @Test
    void nonMemberCannotDownload() {
        NodeDTO p = createPage("A");
        Attachment a = upload(p);
        User outsider = newUser();
        loginAs(outsider);
        assertTrue(statusOf(() -> service.download(a.getId())) >= 403);
        assertTrue(statusOf(() -> service.getNodeAttachments(p.id())) >= 403);
    }

    @Test
    void purgeDeletesNodeAttachmentsAndFiles() {
        NodeDTO p = createPage("A");
        Attachment a = upload(p);
        trash.trash(p.id());
        trash.deleteForever(p.id());
        assertTrue(attachments.findByNodeId(p.id()).isEmpty());
        assertThrows(RuntimeException.class, () -> storage.load(a.getFileId()));
    }
}
