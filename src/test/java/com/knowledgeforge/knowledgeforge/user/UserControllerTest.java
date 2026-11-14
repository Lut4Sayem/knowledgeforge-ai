package com.knowledgeforge.knowledgeforge.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    UserRepository users = mock(UserRepository.class);
    UserController controller = new UserController(users, null, null, null);
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("me@test.com", null);

    UpdateProfileRequest named(String name) {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setFullName(name);
        return r;
    }

    @Test
    void blankOrTooLongNameIs400AndNothingIsSaved() {
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(new User("Me", "me@test.com", "hash")));
        for (String bad : new String[] {"", "   ", "x".repeat(UserController.MAX_NAME_LENGTH + 1)}) {
            assertThatThrownBy(() -> controller.updateMe(auth, named(bad)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(users, never()).save(any());
    }

    @Test
    void goodNameIsTrimmedAndSaved() {
        User me = new User("Me", "me@test.com", "hash");
        when(users.findByEmail("me@test.com")).thenReturn(Optional.of(me));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        controller.updateMe(auth, named("  Ada L  "));
        assertThat(me.getFullName()).isEqualTo("Ada L");
    }
}
