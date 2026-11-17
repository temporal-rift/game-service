package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;

class InMemoryScoringContextStabilizeJpaRepositoryTest {

    private final InMemoryScoringContextStabilizeJpaRepository repository =
            new InMemoryScoringContextStabilizeJpaRepository(new InMemoryDatabase(Duration.ofSeconds(1)));

    @Test
    void aSecondStabilizeOnTheSameEventIsRecordedOnce() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);
        repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);

        assertThat(repository.findAllByGameIdAndEraNumber(gameId, 2))
                .singleElement()
                .satisfies(stabilize -> {
                    assertThat(stabilize.getPlayerId()).isEqualTo(playerId);
                    assertThat(stabilize.getTargetEventId()).isEqualTo(eventId);
                });
    }

    @Test
    void stabilizesAreListedPerGameAndEra() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);
        repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, UUID.randomUUID(), eventId);
        repository.insertIfAbsent(UUID.randomUUID(), gameId, 3, playerId, eventId);
        repository.insertIfAbsent(UUID.randomUUID(), UUID.randomUUID(), 2, playerId, eventId);

        assertThat(repository.findAllByGameIdAndEraNumber(gameId, 2)).hasSize(2);
        assertThat(repository.findAllByGameIdAndEraNumber(gameId, 3)).hasSize(1);
    }
}
