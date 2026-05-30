package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FutureEventDefinitionJpaRepository extends JpaRepository<FutureEventDefinitionJpaEntity, UUID> {

    List<FutureEventDefinitionJpaEntity> findAllByGameIdAndEraNumberOrderByDisplayOrder(UUID gameId, int eraNumber);

    void deleteAllByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
