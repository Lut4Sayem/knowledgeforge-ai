package com.knowledgeforge.knowledgeforge.attachment;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Upload/download rules. Node access is mocked per role; repositories and GridFS are mocked.
class AttachmentServiceTest {

    static final String EMAIL = "me@test.com", USER_ID = "u1", NODE_ID = "n1";

    UserRepository users = mock(UserRepository.class);
    AttachmentRepository attachments = mock(AttachmentRepository.class);
    AttachmentStorageService storage = mock(AttachmentStorageService.class);
    NodeAccessService access = mock(NodeAccessService.class);
    AttachmentService service = new AttachmentService(attachments, storage, users, access);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, null));
        User me = new User("Me", EMAIL, "hash");
        me.setId(USER_ID);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(me));

        when(storage.store(any(), anyString())).thenReturn("f1");
        when(attachments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attachments.findByFileIdIsNotNull()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Same rules NodeAccessService enforces: VIEWER may read, only ADMIN and EDITOR may write, non-members get 403. */
    void myRole(String role) {
        reset(access);
        ResponseStatusException denied = new ResponseStatusException(HttpStatus.FORBIDDEN, "No access");
        if (role == null) {
            doThrow(denied).when(access).requireView(any());
            doThrow(denied).when(access).requireEdit(any());
        } else if (role.equals("VIEWER")) {
            doThrow(denied).when(access).requireEdit(any());
        }
    }

    static MockMultipartFile file(String name, int bytes) {
        return new MockMultipartFile("file", name, "text/html", new byte[bytes]); // client type is ignored
    }

    static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    static Attachment fileAttachment(String id, String fileId, long size) {
        Attachment a = new Attachment();
        a.setId(id);
        a.setNodeId(NODE_ID);
        a.setFileId(fileId);
        a.setSize(size);
        a.setOriginalName("report.pdf");
        a.setContentType("application/pdf");
        return a;
    }

    @Test
    void editorUploadStoresFileWithOurContentType() {
        myRole("EDITOR");
        Attachment a = service.uploadNodeFile(NODE_ID, file("Report.PDF", 10));

        verify(storage).store(any(), eq("application/pdf"));
        assertThat(a.getFileId()).isEqualTo("f1");
        assertThat(a.getContentType()).isEqualTo("application/pdf"); // not the client's text/html
        assertThat(a.getSize()).isEqualTo(10);
        assertThat(a.getOriginalName()).isEqualTo("Report.PDF");
    }

    @Test
    void viewerCannotUpload() {
        myRole("VIEWER");
        assertStatus(() -> service.uploadNodeFile(NODE_ID, file("a.pdf", 10)), HttpStatus.FORBIDDEN);
        verifyNoInteractions(storage);
    }

    @Test
    void disallowedTypesAreRejected() {
        myRole("EDITOR");
        for (String name : List.of("logo.svg", "page.html", "tool.exe", "archive.zip", "noextension")) {
            assertStatus(() -> service.uploadNodeFile(NODE_ID, file(name, 10)), HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void emptyFileIs400AndOversizeIs413() {
        myRole("EDITOR");
        assertStatus(() -> service.uploadNodeFile(NODE_ID, file("a.pdf", 0)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.uploadNodeFile(NODE_ID, file("a.pdf", (int) AttachmentService.MAX_FILE_BYTES + 1)),
                HttpStatus.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(storage);
    }

    @Test
    void totalStorageCapIsEnforced() {
        myRole("EDITOR");
        when(attachments.findByFileIdIsNotNull())
                .thenReturn(List.of(fileAttachment("a0", "f0", AttachmentService.MAX_TOTAL_BYTES - 5)));
        assertStatus(() -> service.uploadNodeFile(NODE_ID, file("a.pdf", 10)), HttpStatus.PAYLOAD_TOO_LARGE);
        verify(storage, never()).store(any(), anyString());
    }

    @Test
    void storedFileIsRemovedIfRecordCannotBeSaved() {
        myRole("EDITOR");
        when(attachments.save(any())).thenThrow(new RuntimeException("db down"));
        assertThatThrownBy(() -> service.uploadNodeFile(NODE_ID, file("a.pdf", 10))).hasMessage("db down");
        verify(storage).delete("f1");
    }

    @Test
    void memberCanDownloadButNonMemberCannot() {
        when(attachments.findById("a1")).thenReturn(Optional.of(fileAttachment("a1", "f1", 3)));
        when(storage.load("f1")).thenReturn(new ByteArrayResource(new byte[3]));

        myRole("VIEWER");
        assertThat(service.download("a1").attachment().getId()).isEqualTo("a1");

        myRole(null);
        assertStatus(() -> service.download("a1"), HttpStatus.FORBIDDEN);
    }

    @Test
    void downloadIsAlwaysAnAttachmentWithNosniff() {
        myRole("VIEWER");
        Attachment a = fileAttachment("a1", "f1", 3);
        a.setOriginalName("résumé \"final\".pdf");
        when(attachments.findById("a1")).thenReturn(Optional.of(a));
        when(storage.load("f1")).thenReturn(new ByteArrayResource(new byte[3]));

        ResponseEntity<?> res = new AttachmentController(service).download("a1");

        String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment;").contains("filename*=UTF-8''");
        assertThat(res.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeaders().getContentType()).hasToString("application/pdf");
    }

    @Test
    void deletingAnAttachmentDeletesItsFile() {
        myRole("EDITOR");
        when(attachments.findById("a1")).thenReturn(Optional.of(fileAttachment("a1", "f1", 3)));
        service.deleteAttachment("a1");
        verify(attachments).deleteById("a1");
        verify(storage).delete("f1");
    }

    @Test
    void viewerCannotDeleteAttachments() {
        myRole("VIEWER");
        when(attachments.findById("a1")).thenReturn(Optional.of(fileAttachment("a1", "f1", 3)));
        assertStatus(() -> service.deleteAttachment("a1"), HttpStatus.FORBIDDEN);
        verify(attachments, never()).deleteById(any());
        verifyNoInteractions(storage);
    }

    @Test
    void fileNamesAreCleaned() {
        assertThat(AttachmentService.cleanFileName("..\\..//etc/notes.txt")).isEqualTo("....etcnotes.txt");
        assertThat(AttachmentService.cleanFileName("a\u0000b\nc.md")).isEqualTo("abc.md");

        String longName = AttachmentService.cleanFileName("x".repeat(500) + ".pdf");
        assertThat(longName).hasSize(AttachmentService.MAX_NAME_LENGTH).endsWith(".pdf");

        assertStatus(() -> AttachmentService.cleanFileName(" / "), HttpStatus.BAD_REQUEST);
    }
}
