package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;

@PersistenceIntegrationTest
class ScoringContextStabilizePersistenceIT {

    @Autowired
    ScoringContextStabilizeJpaRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void insertIfAbsent_duplicateWithinSameTransactionIsRecordedOnceAndDoesNotPoisonLaterWrites() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var otherEventId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ -> {
            repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);
            repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);
            repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, otherEventId);
        });

        assertThat(repository.findAllByGameIdAndEraNumber(gameId, 2))
                .extracting(ScoringContextStabilizeJpaEntity::getTargetEventId)
                .containsExactlyInAnyOrder(eventId, otherEventId);
    }

    @Test
    void findAllByGameIdAndEraNumber_listsOnlyThatEra() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ -> {
            repository.insertIfAbsent(UUID.randomUUID(), gameId, 2, playerId, eventId);
            repository.insertIfAbsent(UUID.randomUUID(), gameId, 3, playerId, eventId);
        });

        assertThat(repository.findAllByGameIdAndEraNumber(gameId, 3))
                .singleElement()
                .extracting(ScoringContextStabilizeJpaEntity::getEraNumber)
                .isEqualTo(3);
    }
}
