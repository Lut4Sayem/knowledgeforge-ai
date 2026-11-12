package com.knowledgeforge.knowledgeforge.attachment;

import com.knowledgeforge.knowledgeforge.activity.ActivityLogService;
import com.knowledgeforge.knowledgeforge.ai.AiSummaryRepository;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentEntity;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentRepository;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.documentVersion.DocumentVersionRepository;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMember;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
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

// Upload/download rules. Real DocumentService (so role checks are real); repositories and GridFS are mocked.
class AttachmentServiceTest {

    static final String EMAIL = "me@test.com", USER_ID = "u1", TEAM_ID = "t1", SPACE_ID = "s1", DOC_ID = "d1";

    UserRepository users = mock(UserRepository.class);
    TeamMemberRepository members = mock(TeamMemberRepository.class);
    SpaceRepository spaces = mock(SpaceRepository.class);
    DocumentRepository documents = mock(DocumentRepository.class);
    AttachmentRepository attachments = mock(AttachmentRepository.class);
    AttachmentStorageService storage = mock(AttachmentStorageService.class);
    AiSummaryRepository summaries = mock(AiSummaryRepository.class);

    DocumentService documentService = new DocumentService(documents, spaces, members, users,
            mock(DocumentVersionRepository.class), mock(ActivityLogService.class), attachments, storage, summaries);
    AttachmentService service = new AttachmentService(attachments, storage, documentService, users, mock(com.knowledgeforge.knowledgeforge.access.NodeAccessService.class));

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, null));
        User me = new User("Me", EMAIL, "hash");
        me.setId(USER_ID);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(me));

        Space space = new Space();
        space.setId(SPACE_ID);
        space.setTeamId(TEAM_ID);
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space));

        DocumentEntity doc = new DocumentEntity();
        doc.setId(DOC_ID);
        doc.setSpaceId(SPACE_ID);
        when(documents.findById(DOC_ID)).thenReturn(Optional.of(doc));

        when(members.canEdit(any(), any())).thenCallRealMethod();
        when(storage.store(any(), anyString())).thenReturn("f1");
        when(attachments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attachments.findByFileIdIsNotNull()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    void myRole(String role) {
        when(members.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(role != null);
        if (role == null) {
            when(members.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.empty());
            return;
        }
        TeamMember m = new TeamMember();
        m.setTeamId(TEAM_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        when(members.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.of(m));
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
        a.setDocumentId(DOC_ID);
        a.setFileId(fileId);
        a.setSize(size);
        a.setOriginalName("report.pdf");
        a.setContentType("application/pdf");
        return a;
    }

    @Test
    void editorUploadStoresFileWithOurContentType() {
        myRole("EDITOR");
        Attachment a = service.uploadFile(DOC_ID, file("Report.PDF", 10));

        verify(storage).store(any(), eq("application/pdf"));
        assertThat(a.getFileId()).isEqualTo("f1");
        assertThat(a.getContentType()).isEqualTo("application/pdf"); // not the client's text/html
        assertThat(a.getSize()).isEqualTo(10);
        assertThat(a.getOriginalName()).isEqualTo("Report.PDF");
    }

    @Test
    void viewerCannotUpload() {
        myRole("VIEWER");
        assertStatus(() -> service.uploadFile(DOC_ID, file("a.pdf", 10)), HttpStatus.FORBIDDEN);
        verifyNoInteractions(storage);
    }

    @Test
    void disallowedTypesAreRejected() {
        myRole("EDITOR");
        for (String name : List.of("logo.svg", "page.html", "tool.exe", "archive.zip", "noextension")) {
            assertStatus(() -> service.uploadFile(DOC_ID, file(name, 10)), HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void emptyFileIs400AndOversizeIs413() {
        myRole("EDITOR");
        assertStatus(() -> service.uploadFile(DOC_ID, file("a.pdf", 0)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.uploadFile(DOC_ID, file("a.pdf", (int) AttachmentService.MAX_FILE_BYTES + 1)),
                HttpStatus.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(storage);
    }

    @Test
    void totalStorageCapIsEnforced() {
        myRole("EDITOR");
        when(attachments.findByFileIdIsNotNull())
                .thenReturn(List.of(fileAttachment("a0", "f0", AttachmentService.MAX_TOTAL_BYTES - 5)));
        assertStatus(() -> service.uploadFile(DOC_ID, file("a.pdf", 10)), HttpStatus.PAYLOAD_TOO_LARGE);
        verify(storage, never()).store(any(), anyString());
    }

    @Test
    void storedFileIsRemovedIfRecordCannotBeSaved() {
        myRole("EDITOR");
        when(attachments.save(any())).thenThrow(new RuntimeException("db down"));
        assertThatThrownBy(() -> service.uploadFile(DOC_ID, file("a.pdf", 10))).hasMessage("db down");
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
    void deletingADocumentCleansUpItsFiles() {
        myRole("ADMIN");
        Attachment link = new Attachment();
        link.setUrl("https://example.com");
        List<Attachment> list = List.of(fileAttachment("a1", "f1", 3), link);
        when(attachments.findByDocumentId(DOC_ID)).thenReturn(list);

        documentService.deleteDocument(DOC_ID);

        verify(attachments).deleteAll(list);
        verify(storage).delete("f1");
        verify(storage).delete(null); // link attachment: storage ignores a null id
        verify(summaries).deleteByDocumentId(DOC_ID); // cached AI summary goes with the document
        verify(documents).deleteById(DOC_ID);
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
