package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, ScoringEraCompletionRepositoryAdapter.class})
class ScoringEraCompletionPersistenceIT {

    @Autowired
    ScoringEraCompletionRepository scoringEraCompletionRepository;

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
