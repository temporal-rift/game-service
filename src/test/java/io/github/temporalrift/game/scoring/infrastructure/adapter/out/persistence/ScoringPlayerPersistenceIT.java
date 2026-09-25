package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;

@PersistenceIntegrationTest
class ScoringPlayerPersistenceIT {

    @Autowired
    ScoringPlayerRepository playerRepository;

    @Autowired
    ScoringPlayerJpaRepository jpaRepository;

    @Test
    void upsertPlayerName_withBoundaryLengthName_persistsItUnchanged() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var boundaryName = "A".repeat(32);

        playerRepository.upsertPlayerName(gameId, playerId, boundaryName);

        assertThat(jpaRepository.findAllByGameId(gameId)).singleElement().satisfies(row -> {
            assertThat(row.getGameId()).isEqualTo(gameId);
            assertThat(row.getPlayerId()).isEqualTo(playerId);
            assertThat(row.getPlayerName()).isEqualTo(boundaryName);
        });
    }
}
