package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LobbyPlayerPkTest {

    @Test
    @DisplayName("equal keys have the same lobbyId and playerId")
    void equals_sameFields_returnsTrue() {
        // given
        var lobbyId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        // when
        var pk1 = new LobbyPlayerPk(lobbyId, playerId);
        var pk2 = new LobbyPlayerPk(lobbyId, playerId);

        // then
        assertThat(pk1).isEqualTo(pk2).hasSameHashCodeAs(pk2);
    }

    @Test
    @DisplayName("same reference is equal to itself")
    void equals_sameReference_returnsTrue() {
        // given
        var pk = new LobbyPlayerPk(UUID.randomUUID(), UUID.randomUUID());

        // then
        assertThat(pk).hasSameHashCodeAs(pk);
    }

    @Test
    @DisplayName("keys with different lobbyId are not equal")
    void equals_differentLobbyId_returnsFalse() {
        // given
        var playerId = UUID.randomUUID();
        var pk1 = new LobbyPlayerPk(UUID.randomUUID(), playerId);
        var pk2 = new LobbyPlayerPk(UUID.randomUUID(), playerId);

        // then
        assertThat(pk1).isNotEqualTo(pk2);
    }

    @Test
    @DisplayName("keys with different playerId are not equal")
    void equals_differentPlayerId_returnsFalse() {
        // given
        var lobbyId = UUID.randomUUID();
        var pk1 = new LobbyPlayerPk(lobbyId, UUID.randomUUID());
        var pk2 = new LobbyPlayerPk(lobbyId, UUID.randomUUID());

        // then
        assertThat(pk1).isNotEqualTo(pk2);
    }

    @Test
    @DisplayName("getters return the values provided at construction")
    void getters_returnConstructorValues() {
        // given
        var lobbyId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        // when
        var pk = new LobbyPlayerPk(lobbyId, playerId);

        // then
        assertThat(pk.getLobbyId()).isEqualTo(lobbyId);
        assertThat(pk.getPlayerId()).isEqualTo(playerId);
    }
}
