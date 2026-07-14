package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringTimelineOutcomeInboxJpaRepository extends JpaRepository<ScoringTimelineOutcomeInboxJpaEntity, UUID> {

    List<ScoringTimelineOutcomeInboxJpaEntity> findAllByGameIdAndEraNumberOrderByEventIdAsc(UUID gameId, int eraNumber);

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_timeline_outcome_inbox
                        (id, game_id, era_number, event_id, winning_outcome_id, payload)
                    VALUES (:id, :gameId, :eraNumber, :eventId, :winningOutcomeId, CAST(:payload AS jsonb))
                    ON CONFLICT (game_id, era_number, event_id) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("eventId") UUID eventId,
            @Param("winningOutcomeId") UUID winningOutcomeId,
            @Param("payload") String payload);
}
