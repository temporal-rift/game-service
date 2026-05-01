package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.PlayerPrincipal;

class PlayerAuthenticationTokenTest {

    static PlayerPrincipal principal() {
        return new PlayerPrincipal(UUID.randomUUID());
    }

    @Test
    @DisplayName("getPrincipal returns the principal supplied at construction")
    void getPrincipal_returnsPrincipal() {
        // given
        var p = principal();
        var token = new PlayerAuthenticationToken(p);

        // when / then
        assertThat(token.getPrincipal()).isEqualTo(p);
    }

    @Test
    @DisplayName("getCredentials always returns null")
    void getCredentials_returnsNull() {
        // given
        var token = new PlayerAuthenticationToken(principal());

        // when / then
        assertThat(token.getCredentials()).isNull();
    }

    @Test
    @DisplayName("token is authenticated immediately after construction")
    void isAuthenticated_trueAfterConstruction() {
        // given
        var token = new PlayerAuthenticationToken(principal());

        // when / then
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("equal tokens share the same principal")
    void equals_samePrincipal_returnsTrue() {
        // given
        var p = principal();
        var t1 = new PlayerAuthenticationToken(p);
        var t2 = new PlayerAuthenticationToken(p);

        // then
        assertThat(t1).isEqualTo(t2).hasSameHashCodeAs(t2);
    }

    @Test
    @DisplayName("tokens with different principals are not equal")
    void equals_differentPrincipal_returnsFalse() {
        // given
        var t1 = new PlayerAuthenticationToken(principal());
        var t2 = new PlayerAuthenticationToken(principal());

        // then
        assertThat(t1).isNotEqualTo(t2);
    }
}
