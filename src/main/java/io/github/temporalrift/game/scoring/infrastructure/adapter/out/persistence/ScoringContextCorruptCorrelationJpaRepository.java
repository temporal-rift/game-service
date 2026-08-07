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
                        (id, game_id, era_number, corrupting_player_id, target_player_id, card_instance_id)
                    VALUES (:id, :gameId, :eraNumber, :corruptingPlayerId, :targetPlayerId, :cardInstanceId)
                    ON CONFLICT (game_id, era_number, corrupting_player_id, card_instance_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("corruptingPlayerId") UUID corruptingPlayerId,
            @Param("targetPlayerId") UUID targetPlayerId,
            @Param("cardInstanceId") UUID cardInstanceId);
}
