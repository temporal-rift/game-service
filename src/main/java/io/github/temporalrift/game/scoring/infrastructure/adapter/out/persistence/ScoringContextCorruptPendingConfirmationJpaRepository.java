package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextCorruptPendingConfirmationJpaRepository
        extends JpaRepository<ScoringContextCorruptPendingConfirmationJpaEntity, UUID> {

    Optional<ScoringContextCorruptPendingConfirmationJpaEntity>
            findByGameIdAndEraNumberAndCorruptingPlayerIdAndTargetEventId(
                    UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetEventId);

    // Idempotent on the natural key: redelivered confirmations must not produce a second row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_corrupt_pending_confirmation
                        (id, game_id, era_number, corrupting_player_id, target_event_id, took_effect)
                    VALUES (:id, :gameId, :eraNumber, :corruptingPlayerId, :targetEventId, :tookEffect)
                    ON CONFLICT (game_id, era_number, corrupting_player_id, target_event_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("corruptingPlayerId") UUID corruptingPlayerId,
            @Param("targetEventId") UUID targetEventId,
            @Param("tookEffect") boolean tookEffect);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    DELETE FROM scoring_context_corrupt_pending_confirmation
                    WHERE game_id = :gameId AND era_number = :eraNumber
                        AND corrupting_player_id = :corruptingPlayerId AND target_event_id = :targetEventId
                    """, nativeQuery = true)
    void deletePending(
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("corruptingPlayerId") UUID corruptingPlayerId,
            @Param("targetEventId") UUID targetEventId);
}
