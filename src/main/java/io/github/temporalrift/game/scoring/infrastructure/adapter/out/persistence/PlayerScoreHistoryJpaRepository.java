package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerScoreHistoryJpaRepository extends JpaRepository<PlayerScoreHistoryJpaEntity, UUID> {

    long countByPlayerScoreId(UUID playerScoreId);

    List<PlayerScoreHistoryJpaEntity> findAllByGameId(UUID gameId);

    List<PlayerScoreHistoryJpaEntity> findAllByGameIdOrderByEraNumberAsc(UUID gameId);
}
