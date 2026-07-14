package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.timeline.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

@Component
class TimelineOutcomeInboxRepositoryAdapter implements TimelineOutcomeInboxRepository {

    private final ScoringTimelineOutcomeInboxJpaRepository jpaRepository;

    TimelineOutcomeInboxRepositoryAdapter(ScoringTimelineOutcomeInboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutcomeApplied outcome) {
        var existing = jpaRepository.findByGameIdAndEraNumberAndEventId(
                outcome.gameId(), outcome.eraNumber(), outcome.eventId());
        if (existing.isPresent()) {
            return;
        }
        jpaRepository.save(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome));
    }

    @Override
    public List<OutcomeApplied> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(gameId, eraNumber).stream()
                .map(ScoringTimelineOutcomeInboxJpaEntity::toDomain)
                .toList();
    }
}
