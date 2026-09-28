package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.port.out.FactionDisclosureRepository;

@PersistenceIntegrationTest
class FactionDisclosurePersistenceIT {

    @Autowired
    FactionDisclosureRepository factionDisclosureRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void recordDisclosure_isIdempotentAndScopedToItsGame() {
        var gameId = UUID.randomUUID();
        var otherGameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var otherPlayerId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(_ -> {
            factionDisclosureRepository.recordDisclosure(gameId, playerId);
            factionDisclosureRepository.recordDisclosure(gameId, playerId);
            factionDisclosureRepository.recordDisclosure(otherGameId, otherPlayerId);
        });

        assertThat(factionDisclosureRepository.disclosedPlayerIds(gameId)).containsExactly(playerId);
        assertThat(factionDisclosureRepository.disclosedPlayerIds(otherGameId)).containsExactly(otherPlayerId);
    }
}
