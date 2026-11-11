package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextEventOutcomeJpaRepository extends JpaRepository<ScoringContextEventOutcomeJpaEntity, UUID> {

    List<ScoringContextEventOutcomeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber);

    // Earliest round wins: the writing is replaced, as a whole, only when none is recorded yet or the incoming
    // Foresight was played in a strictly earlier round. A later Foresight on the same event, a same-round
    // redelivery, or a delivery arriving out of round order never overwrites the earliest commitment. A legacy
    // writing without a round is kept as is.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_event_outcome
                        (id, game_id, era_number, event_id, written_outcome_id, written_by_player_id,
                         written_round_number)
                    VALUES (:id, :gameId, :eraNumber, :eventId, :outcomeId, :playerId, :roundNumber)
                    ON CONFLICT (game_id, era_number, event_id) DO UPDATE SET
                        written_outcome_id = EXCLUDED.written_outcome_id,
                        written_by_player_id = EXCLUDED.written_by_player_id,
                        written_round_number = EXCLUDED.written_round_number
                    WHERE scoring_context_event_outcome.written_outcome_id IS NULL
                        OR scoring_context_event_outcome.written_round_number > EXCLUDED.written_round_number
                    """, nativeQuery = true)
    void insertWrittenOutcomeIfEarliest(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("roundNumber") int roundNumber,
            @Param("eventId") UUID eventId,
            @Param("outcomeId") UUID outcomeId,
            @Param("playerId") UUID playerId);
}
