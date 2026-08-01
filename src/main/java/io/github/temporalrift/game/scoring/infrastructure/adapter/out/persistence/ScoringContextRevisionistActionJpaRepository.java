package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextRevisionistActionJpaRepository
        extends JpaRepository<ScoringContextRevisionistActionJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO scoring_context_revisionist_action
                (id, game_id, era_number, player_id, action, target_event_id, target_outcome_id)
            VALUES (:id, :gameId, :eraNumber, :playerId, :action, :targetEventId, :targetOutcomeId)
            ON CONFLICT (game_id, era_number, player_id, action, target_event_id, target_outcome_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("playerId") UUID playerId,
            @Param("action") String action,
            @Param("targetEventId") UUID targetEventId,
            @Param("targetOutcomeId") UUID targetOutcomeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select action from ScoringContextRevisionistActionJpaEntity action
            where action.gameId = :gameId and action.eraNumber = :eraNumber and action.resolved is null
            """)
    List<ScoringContextRevisionistActionJpaEntity> findAllUnresolvedWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);

    boolean existsByGameIdAndEraNumberAndResolvedIsNull(UUID gameId, int eraNumber);
}
