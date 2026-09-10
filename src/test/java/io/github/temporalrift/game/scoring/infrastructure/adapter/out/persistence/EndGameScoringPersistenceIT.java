package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EndGameScoreFactRepository;
import io.github.temporalrift.game.scoring.domain.port.out.FactionIdentificationRepository;

@PersistenceIntegrationTest
class EndGameScoringPersistenceIT {

    @Autowired
    EndGameScoreFactRepository endGameScoreFactRepository;

    @Autowired
    FactionIdentificationRepository factionIdentificationRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void ledgers_preservePriorIdentificationAndClaimEndGameFactOnlyOnce() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ -> {
            factionIdentificationRepository.recordIdentification(gameId, playerId);
            factionIdentificationRepository.recordIdentification(gameId, playerId);
            assertThat(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                    .isTrue();
            assertThat(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                    .isFalse();
        });

        assertThat(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .isTrue();
    }
}
