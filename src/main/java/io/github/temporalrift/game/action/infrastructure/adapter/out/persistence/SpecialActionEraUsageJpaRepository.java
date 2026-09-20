package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpecialActionEraUsageJpaRepository extends JpaRepository<SpecialActionEraUsageJpaEntity, UUID> {

    Optional<SpecialActionEraUsageJpaEntity> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId);

    /**
     * Counts eras in which the player claimed the given special. Backs the derived game-wide Seal
     * budget: one accepted Seal per era at most, counted across eras.
     */
    @Query(value = """
                    SELECT COUNT(*) FROM special_action_era_usage
                    WHERE game_id = :gameId AND player_id = :playerId AND :special = ANY(claimed_specials)
                    """, nativeQuery = true)
    int countErasClaiming(
            @Param("gameId") UUID gameId, @Param("playerId") UUID playerId, @Param("special") String special);
}
