package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextEventOutcomeJpaRepository extends JpaRepository<ScoringContextEventOutcomeJpaEntity, UUID> {

    List<ScoringContextEventOutcomeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber);

    // On conflict, only starting_outcome_count is overwritten — a written_outcome_id recorded by an
    // earlier ForesightDeclared (which can arrive first or be redelivered) must never be clobbered here.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_event_outcome
                        (id, game_id, era_number, event_id, starting_outcome_count)
                    VALUES (:id, :gameId, :eraNumber, :eventId, :startingOutcomeCount)
                    ON CONFLICT (game_id, era_number, event_id)
                    DO UPDATE SET starting_outcome_count = EXCLUDED.starting_outcome_count
                    """, nativeQuery = true)
    void upsertBaseline(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("eventId") UUID eventId,
            @Param("startingOutcomeCount") int startingOutcomeCount);

    // First-wins: on conflict, written_outcome_id/written_by_player_id are only set if still null.
    // A Prophet who re-declares Foresight for the same event later in the era does not overwrite the
    // earliest commitment, and redelivery of the same declaration is a no-op either way.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_event_outcome
                        (id, game_id, era_number, event_id, starting_outcome_count,
                         written_outcome_id, written_by_player_id)
                    VALUES (:id, :gameId, :eraNumber, :eventId, 0, :outcomeId, :playerId)
                    ON CONFLICT (game_id, era_number, event_id) DO UPDATE SET
                        written_outcome_id = COALESCE(
                            scoring_context_event_outcome.written_outcome_id, EXCLUDED.written_outcome_id),
                        written_by_player_id = COALESCE(
                            scoring_context_event_outcome.written_by_player_id, EXCLUDED.written_by_player_id)
                    """, nativeQuery = true)
    void insertWrittenOutcomeIfFirst(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("eventId") UUID eventId,
            @Param("outcomeId") UUID outcomeId,
            @Param("playerId") UUID playerId);
}
