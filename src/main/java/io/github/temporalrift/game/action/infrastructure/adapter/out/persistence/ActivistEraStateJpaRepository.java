package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ActivistEraStateJpaRepository extends JpaRepository<ActivistEraStateJpaEntity, UUID> {

    Optional<ActivistEraStateJpaEntity> findByGameIdAndEraNumberAndActivistPlayerId(
            UUID gameId, int eraNumber, UUID activistPlayerId);

    List<ActivistEraStateJpaEntity> findAllByGameIdAndEraNumberAndDeclarationModeIsNotNull(UUID gameId, int eraNumber);

    List<ActivistEraStateJpaEntity> findAllByGameIdAndEraNumberAndExposedPlayerIdIsNotNull(UUID gameId, int eraNumber);
}
