package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScoringContextPlayerJpaRepository extends JpaRepository<ScoringContextPlayerJpaEntity, UUID> {

    List<ScoringContextPlayerJpaEntity> findAllByGameId(UUID gameId);

    Optional<ScoringContextPlayerJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId);
}
