package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;

class ScoringTimelineOutcomeInboxJpaEntityTest {

    @Test
    void fromDomain_mapsAllFieldsOntoEntity() {
        var outcome = new OutcomeApplied(
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new OutcomeApplied.ProbabilityState(UUID.randomUUID(), 70)));

        var entity = ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome);

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getGameId()).isEqualTo(outcome.gameId());
        assertThat(entity.getEraNumber()).isEqualTo(outcome.eraNumber());
        assertThat(entity.getEventId()).isEqualTo(outcome.eventId());
        assertThat(entity.getWinningOutcomeId()).isEqualTo(outcome.winningOutcomeId());
        assertThat(entity.getPayload()).isEqualTo(outcome);
    }

    @Test
    void toDomain_returnsStoredPayload() {
        var outcome = new OutcomeApplied(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of());
        var entity = ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome);

        assertThat(entity.toDomain()).isEqualTo(outcome);
    }
}
