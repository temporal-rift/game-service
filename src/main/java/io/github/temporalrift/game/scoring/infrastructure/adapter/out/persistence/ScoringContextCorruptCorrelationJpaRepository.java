package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextCorruptCorrelationJpaRepository
        extends JpaRepository<ScoringContextCorruptCorrelationJpaEntity, UUID> {

    List<ScoringContextCorruptCorrelationJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber);

    // Idempotent on (game_id, era_number, corrupting_player_id, card_instance_id): redelivery of the same
    // correlation, or the round-close pass retrying, must not produce a second row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_corrupt_correlation
                        (id, game_id, era_number, corrupting_player_id, target_player_id, card_instance_id,
                         target_event_id, source_outcome_id, target_outcome_id)
                    VALUES (:id, :gameId, :eraNumber, :corruptingPlayerId, :targetPlayerId, :cardInstanceId,
                            :targetEventId, :sourceOutcomeId, :targetOutcomeId)
                    ON CONFLICT (game_id, era_number, corrupting_player_id, card_instance_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("corruptingPlayerId") UUID corruptingPlayerId,
            @Param("targetPlayerId") UUID targetPlayerId,
            @Param("cardInstanceId") UUID cardInstanceId,
            @Param("targetEventId") UUID targetEventId,
            @Param("sourceOutcomeId") UUID sourceOutcomeId,
            @Param("targetOutcomeId") UUID targetOutcomeId);

    // Not yet called by any production listener: no timeline-service event confirming inversion
    // outcome exists yet (temporal-rift/timeline-service#12 / #16). Ready for that future consumer —
    // keyed on the same natural key as insertIfAbsent above.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    UPDATE scoring_context_corrupt_correlation
                    SET took_effect = :tookEffect
                    WHERE game_id = :gameId AND era_number = :eraNumber
                        AND corrupting_player_id = :corruptingPlayerId AND card_instance_id = :cardInstanceId
                    """, nativeQuery = true)
    void confirmInversion(
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("corruptingPlayerId") UUID corruptingPlayerId,
            @Param("cardInstanceId") UUID cardInstanceId,
            @Param("tookEffect") boolean tookEffect);
}
