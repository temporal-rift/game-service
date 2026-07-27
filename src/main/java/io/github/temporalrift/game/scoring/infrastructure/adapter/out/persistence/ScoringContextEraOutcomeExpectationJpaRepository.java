package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScoringContextEraOutcomeExpectationJpaRepository
        extends JpaRepository<ScoringContextEraOutcomeExpectationJpaEntity, UUID> {

    Optional<ScoringContextEraOutcomeExpectationJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                    INSERT INTO scoring_context_era_outcome_expectation
                        (id, game_id, era_number, expected_outcome_count)
                    VALUES (:id, :gameId, :eraNumber, :expectedOutcomeCount)
                    ON CONFLICT (game_id, era_number)
                    DO UPDATE SET expected_outcome_count = EXCLUDED.expected_outcome_count
                    """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("gameId") UUID gameId,
            @Param("eraNumber") int eraNumber,
            @Param("expectedOutcomeCount") int expectedOutcomeCount);
}
