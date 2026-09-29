package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.port.out.RevisionistExposureRepository;

@PersistenceIntegrationTest
class RevisionistExposurePersistenceIT {

    @Autowired
    RevisionistExposureRepository revisionistExposureRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void recordExposure_isIdempotentAndScopedToItsGame() {
        var gameId = UUID.randomUUID();
        var otherGameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var otherPlayerId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ -> {
            revisionistExposureRepository.recordExposure(gameId, playerId);
            revisionistExposureRepository.recordExposure(gameId, playerId);
            revisionistExposureRepository.recordExposure(otherGameId, otherPlayerId);
        });

        assertThat(revisionistExposureRepository.exposedPlayerIds(gameId)).containsExactly(playerId);
        assertThat(revisionistExposureRepository.exposedPlayerIds(otherGameId)).containsExactly(otherPlayerId);
    }
}
