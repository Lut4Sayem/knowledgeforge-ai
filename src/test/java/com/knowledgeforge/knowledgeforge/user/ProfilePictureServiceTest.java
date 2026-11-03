package com.knowledgeforge.knowledgeforge.user;

import com.knowledgeforge.knowledgeforge.attachment.AttachmentStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Profile picture upload rules; repository and GridFS are mocked.
class ProfilePictureServiceTest {

    static final String EMAIL = "me@test.com";

    UserRepository users = mock(UserRepository.class);
    AttachmentStorageService storage = mock(AttachmentStorageService.class);
    ProfilePictureService service = new ProfilePictureService(users, storage);
    User me = new User("Me", EMAIL, "hash");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, null));
        me.setId("u1");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(me));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static MockMultipartFile file(String name, int bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[bytes]);
    }

    static int statusOf(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    @Test
    void uploadStoresImageWithOurContentTypeAndReplacesOldFile() {
        me.setPictureFileId("old");
        when(storage.store(any(), eq("image/png"))).thenReturn("new");

        User saved = service.upload(file("me.PNG", 10));

        assertThat(saved.getPictureFileId()).isEqualTo("new");
        assertThat(saved.getPictureContentType()).isEqualTo("image/png");
        assertThat(saved.getProfilePicture()).isEqualTo("/api/users/u1/picture?v=new");
        verify(storage).delete("old");
    }

    @Test
    void svgAndOtherTypesAreRejected() {
        assertThatThrownBy(() -> service.upload(file("x.svg", 10))).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST.value()));
        assertThatThrownBy(() -> service.upload(file("x.html", 10))).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST.value()));
        verify(storage, never()).store(any(), any());
    }

    @Test
    void oversizeAndEmptyAreRejected() {
        assertThatThrownBy(() -> service.upload(file("big.jpg", (int) ProfilePictureService.MAX_BYTES + 1)))
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value()));
        assertThatThrownBy(() -> service.upload(file("empty.jpg", 0)))
                .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void failedSaveDeletesTheNewFile() {
        when(storage.store(any(), any())).thenReturn("new");
        when(users.save(any(User.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.upload(file("me.jpg", 10))).hasMessage("db down");
        verify(storage).delete("new");
    }

    @Test
    void removeClearsPictureAndDeletesFile() {
        me.setPictureFileId("old");
        me.setProfilePicture("/api/users/u1/picture?v=old");

        User saved = service.remove();

        assertThat(saved.getProfilePicture()).isNull();
        assertThat(saved.getPictureFileId()).isNull();
        verify(storage).delete("old");
    }

    @Test
    void userWithoutUploadedPictureIs404() {
        User linkOnly = new User("L", "l@test.com", "h");
        linkOnly.setProfilePicture("https://example.com/a.png");
        when(users.findById("u2")).thenReturn(Optional.of(linkOnly));

        assertThatThrownBy(() -> service.load("u2")).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }
}
