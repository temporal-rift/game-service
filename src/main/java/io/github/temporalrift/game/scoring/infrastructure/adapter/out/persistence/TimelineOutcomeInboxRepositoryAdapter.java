package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            jpaRepository.saveAndFlush(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(outcome));
        } catch (DataIntegrityViolationException _) {
            // Another transaction already stored this (gameId, eraNumber, eventId) — already idempotent.
        }
    }

    @Override
    public List<OutcomeApplied> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(gameId, eraNumber).stream()
                .map(ScoringTimelineOutcomeInboxJpaEntity::toDomain)
                .toList();
    }
}
