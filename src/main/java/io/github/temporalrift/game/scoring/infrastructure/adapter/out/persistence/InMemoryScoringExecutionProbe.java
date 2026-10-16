package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;

@Component
@Profile("in-memory")
class InMemoryScoringExecutionProbe implements ExecutionProbe {

    private final ScoringTimelineResolutionBarrierJpaRepository barriers;

    InMemoryScoringExecutionProbe(ScoringTimelineResolutionBarrierJpaRepository barriers) {
        this.barriers = barriers;
    }

    @Override
    public Observation observe(Instant now) {
        return new Observation(
                null, false, 0, barriers.findResolvedErasNotYetScored().size(), null);
    }
}
