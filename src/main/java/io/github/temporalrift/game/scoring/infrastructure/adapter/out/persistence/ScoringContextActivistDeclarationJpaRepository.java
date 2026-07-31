package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextActivistDeclarationJpaRepository
        extends JpaRepository<ScoringContextActivistDeclarationJpaEntity, UUID> {

    @Modifying
    @Query(value = """
                    INSERT INTO scoring_context_activist_declaration
                        (id, game_id, era_number, player_id, mode, target_event_id, target_outcome_id)
                    VALUES (:id, :gameId, :eraNumber, :playerId, :mode, :targetEventId, :targetOutcomeId)
                    ON CONFLICT (game_id, era_number, player_id) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("playerId") UUID playerId,
            @Param("mode") String mode,
            @Param("targetEventId") UUID targetEventId,
            @Param("targetOutcomeId") UUID targetOutcomeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select declaration from ScoringContextActivistDeclarationJpaEntity declaration
            where declaration.gameId = :gameId
                and declaration.eraNumber = :eraNumber
                and declaration.resolutionSucceeded is null
            """)
    List<ScoringContextActivistDeclarationJpaEntity> findAllUnresolvedWithLock(
            @Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);

    boolean existsByGameIdAndEraNumberAndResolutionSucceededIsNull(UUID gameId, int eraNumber);
}
