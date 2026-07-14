package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScoringTimelineOutcomeInboxJpaRepository extends JpaRepository<ScoringTimelineOutcomeInboxJpaEntity, UUID> {

    List<ScoringTimelineOutcomeInboxJpaEntity> findAllByGameIdAndEraNumberOrderByEventIdAsc(UUID gameId, int eraNumber);

    Optional<ScoringTimelineOutcomeInboxJpaEntity> findByGameIdAndEraNumberAndEventId(
            UUID gameId, int eraNumber, UUID eventId);
}
