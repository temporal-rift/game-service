package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EraSagaStateJpaEntityTest {

    @Test
    @DisplayName("getters return the values set via setters")
    void settersAndGetters_roundTrip() {
        // given
        var entity = new EraSagaStateJpaEntity();
        var gameId = UUID.randomUUID();
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        // when
        entity.setGameId(gameId);
        entity.setEraNumber(2);
        entity.setStatus("WAITING_ROUND_1");
        entity.setPlayerIds(playerIds);

        // then
        assertThat(entity.getGameId()).isEqualTo(gameId);
        assertThat(entity.getEraNumber()).isEqualTo(2);
        assertThat(entity.getStatus()).isEqualTo("WAITING_ROUND_1");
        assertThat(entity.getPlayerIds()).isEqualTo(playerIds);
    }
}
