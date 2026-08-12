package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EraSagaScoresUpdatedInboxJpaRepository extends JpaRepository<EraSagaScoresUpdatedInboxJpaEntity, UUID> {

    @Modifying
    @Query(value = """
                    INSERT INTO era_saga_scores_updated_inbox (id, game_id, era_number, payload)
                    VALUES (:id, :gameId, :eraNumber, CAST(:payload AS jsonb))
                    ON CONFLICT (game_id, era_number) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("payload") String payload);

    // Matches game_id AND era_number, not just game_id, so a stale inbox row for an era the saga has
    // already advanced past can never be mistaken for a pending one belonging to the saga's current era.
    @Query(value = """
                    SELECT inbox.* FROM era_saga_scores_updated_inbox inbox
                    JOIN era_saga_state saga
                      ON saga.game_id = inbox.game_id AND saga.era_number = inbox.era_number
                    WHERE saga.status = 'WAITING_SCORES'
                    """, nativeQuery = true)
    List<EraSagaScoresUpdatedInboxJpaEntity> findRecordedButNotAdvanced();
}
