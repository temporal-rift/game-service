package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextActionFactJpaRepository extends JpaRepository<ScoringContextActionFactJpaEntity, UUID> {

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_context_action_fact
                        (id, game_id, era_number, player_id, faction, reason, consumed)
                    VALUES (:id, :gameId, :eraNumber, :playerId, :faction, :reason, false)
                    ON CONFLICT (game_id, era_number, player_id, reason) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("playerId") UUID playerId,
            @Param("faction") String faction,
            @Param("reason") String reason);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select fact from ScoringContextActionFactJpaEntity fact
            where fact.gameId = :gameId and fact.eraNumber = :eraNumber and fact.consumed = false
            """)
    List<ScoringContextActionFactJpaEntity> findAllUnconsumedWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);
}
