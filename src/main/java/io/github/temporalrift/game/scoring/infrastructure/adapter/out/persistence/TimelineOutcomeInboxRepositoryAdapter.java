package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;

@Component
class TimelineOutcomeInboxRepositoryAdapter implements TimelineOutcomeInboxRepository {

    private final ScoringTimelineOutcomeInboxJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    TimelineOutcomeInboxRepositoryAdapter(
            ScoringTimelineOutcomeInboxJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OutcomeApplied outcome) {
        // Native ON CONFLICT DO NOTHING, not check-then-insert or catch-and-swallow: a failed
        // statement inside this @Transactional(REQUIRES_NEW) handler aborts the whole Postgres
        // transaction even if the Java exception is caught, poisoning every later write in the
        // same call (score updates, era completion). A DB-level no-op conflict never aborts it.
        jpaRepository.insertIfAbsent(
                UUID.randomUUID(),
                outcome.gameId(),
                outcome.eraNumber(),
                outcome.eventId(),
                outcome.winningOutcomeId(),
                objectMapper.writeValueAsString(outcome));
    }

    @Override
    public List<OutcomeApplied> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return jpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(gameId, eraNumber).stream()
                .map(ScoringTimelineOutcomeInboxJpaEntity::toDomain)
                .toList();
    }
}
