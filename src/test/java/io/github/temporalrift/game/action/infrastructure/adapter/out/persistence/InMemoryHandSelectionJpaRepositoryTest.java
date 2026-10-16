package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;

class InMemoryHandSelectionJpaRepositoryTest {

    @Test
    void malformedDealtCardsAreRejectedAsBadDataWhereTheRowIsInserted() {
        var repository = new InMemoryHandSelectionJpaRepository(
                new InMemoryDatabase(Duration.ofSeconds(1)),
                JsonMapper.builder().build());

        var id = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        assertThatThrownBy(() -> repository.insertIfAbsent(id, gameId, 1, playerId, "OPEN", Instant.EPOCH, "not json"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("dealt_cards");
    }
}
