package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerScoreHistoryJpaRepository extends JpaRepository<PlayerScoreHistoryJpaEntity, UUID> {

    List<PlayerScoreHistoryJpaEntity> findAllByPlayerScoreIdOrderByEraNumberAsc(UUID playerScoreId);

    long countByPlayerScoreId(UUID playerScoreId);
}
