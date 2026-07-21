package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentPlayerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsThePlayerIdFromTheAuthenticatedPrincipal() {
        var playerId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(new PlayerPrincipal(playerId), null));

        assertThat(CurrentPlayer.id()).isEqualTo(playerId);
    }

    @Test
    void rejectsAnUnexpectedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("player", null));

        assertThatThrownBy(CurrentPlayer::id).isInstanceOf(AccessDeniedException.class);
    }
}
