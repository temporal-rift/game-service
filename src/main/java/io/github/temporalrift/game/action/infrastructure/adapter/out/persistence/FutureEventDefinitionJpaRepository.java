package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FutureEventDefinitionJpaRepository extends JpaRepository<FutureEventDefinitionJpaEntity, UUID> {

    List<FutureEventDefinitionJpaEntity> findAllByGameIdAndEraNumberOrderByDisplayOrder(UUID gameId, int eraNumber);

    // The outcome FK has no ON DELETE CASCADE, and bulk statements bypass Hibernate's cascade
    // handling — outcome rows must be deleted before their parent definitions.
    @Modifying
    @Query(value = """
                    DELETE FROM action_future_event_outcome
                    WHERE future_event_definition_id IN (
                        SELECT id FROM action_future_event_definition
                        WHERE game_id = :gameId AND era_number = :eraNumber)
                    """, nativeQuery = true)
    void deleteOutcomesByGameIdAndEraNumber(@Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);

    @Modifying
    @Query(value = """
                    DELETE FROM action_future_event_definition
                    WHERE game_id = :gameId AND era_number = :eraNumber
                    """, nativeQuery = true)
    void deleteDefinitionsByGameIdAndEraNumber(@Param("gameId") UUID gameId, @Param("eraNumber") int eraNumber);
}
