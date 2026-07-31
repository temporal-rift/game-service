package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SessionActivistDeclarationJpaRepository extends JpaRepository<SessionActivistDeclarationJpaEntity, UUID> {
    boolean existsByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);

    List<SessionActivistDeclarationJpaEntity> findAllByGameIdAndEraNumberAndTargetEventId(
            UUID gameId, int eraNumber, UUID targetEventId);
}
