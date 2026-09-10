package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;

@PersistenceIntegrationTest
class ScoringEraCompletionPersistenceIT {

    @Autowired
    ScoringEraCompletionRepository scoringEraCompletionRepository;

    @Autowired
    ScoringEraCompletionJpaRepository jpaRepository;

    @Test
    void findMaxEraNumberByGameId_noCompletedEras_returnsNull() {
        assertThat(jpaRepository.findMaxEraNumberByGameId(UUID.randomUUID())).isNull();
    }

    @Test
    void findMaxEraNumberByGameId_returnsHighestCompletedEraForThatGame() {
        var gameId = UUID.randomUUID();
        var otherGameId = UUID.randomUUID();
        scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 1);
        scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 2);
        scoringEraCompletionRepository.tryMarkScoringComplete(otherGameId, 5);

        assertThat(jpaRepository.findMaxEraNumberByGameId(gameId)).isEqualTo(2);
    }

    @Test
    void tryMarkScoringComplete_firstClaimWinsSecondClaimFails() {
        var gameId = UUID.randomUUID();

        var firstClaim = scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 1);
        var duplicateClaim = scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 1);

        assertThat(firstClaim).isTrue();
        assertThat(duplicateClaim).isFalse();
    }

    @Test
    void tryMarkScoringComplete_differentEraForSameGameClaimsIndependently() {
        var gameId = UUID.randomUUID();

        var eraOneClaim = scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 1);
        var eraTwoClaim = scoringEraCompletionRepository.tryMarkScoringComplete(gameId, 2);

        assertThat(eraOneClaim).isTrue();
        assertThat(eraTwoClaim).isTrue();
    }
}
