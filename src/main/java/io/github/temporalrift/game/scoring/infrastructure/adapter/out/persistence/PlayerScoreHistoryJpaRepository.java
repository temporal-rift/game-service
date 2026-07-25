package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlayerScoreHistoryJpaRepository extends JpaRepository<PlayerScoreHistoryJpaEntity, UUID> {

    List<PlayerScoreHistoryJpaEntity> findAllByPlayerScoreIdOrderByEraNumberAsc(UUID playerScoreId);

    long countByPlayerScoreId(UUID playerScoreId);

    List<PlayerScoreHistoryJpaEntity> findAllByGameId(UUID gameId);

    @Query("select max(h.eraNumber) from PlayerScoreHistoryJpaEntity h where h.gameId = :gameId")
    Integer findMaxEraNumberByGameId(@Param("gameId") UUID gameId);
}
