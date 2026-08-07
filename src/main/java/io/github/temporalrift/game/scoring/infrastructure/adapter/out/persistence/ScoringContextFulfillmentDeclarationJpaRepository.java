package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextFulfillmentDeclarationJpaRepository
        extends JpaRepository<ScoringContextFulfillmentDeclarationJpaEntity, UUID> {

    List<ScoringContextFulfillmentDeclarationJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber);

    // Idempotent on (game_id, era_number, player_id, target_event_id): redelivery of the same
    // declaration, or the same declaration repeated in a later round, must not produce a second row.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_fulfillment_declaration
                        (id, game_id, era_number, player_id, target_event_id)
                    VALUES (:id, :gameId, :eraNumber, :playerId, :targetEventId)
                    ON CONFLICT (game_id, era_number, player_id, target_event_id) DO NOTHING
                    """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("playerId") UUID playerId,
            @Param("targetEventId") UUID targetEventId);
}
