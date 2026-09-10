package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;

@Component
class PlayerScoreRepositoryAdapter implements PlayerScoreRepository {

    private final PlayerScoreJpaRepository jpaRepository;
    private final PlayerScoreHistoryJpaRepository historyJpaRepository;
    private final EntityManager entityManager;

    PlayerScoreRepositoryAdapter(
            PlayerScoreJpaRepository jpaRepository,
            PlayerScoreHistoryJpaRepository historyJpaRepository,
            EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.historyJpaRepository = historyJpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public List<PlayerScore> findAllByGameId(UUID gameId) {
        return toDomain(gameId, jpaRepository.findAllByGameId(gameId));
    }

    @Override
    public List<PlayerScore> findAllByGameIdWithLock(UUID gameId) {
        return toDomain(gameId, jpaRepository.findAllByGameIdWithLock(gameId));
    }

    private List<PlayerScore> toDomain(UUID gameId, List<PlayerScoreJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        // One history query for the whole game instead of one per score row.
        var historyByScoreId = historyJpaRepository.findAllByGameIdOrderByEraNumberAsc(gameId).stream()
                .collect(Collectors.groupingBy(PlayerScoreHistoryJpaEntity::getPlayerScoreId));
        return entities.stream()
                .map(entity -> toDomain(entity, historyByScoreId.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    public List<PlayerScore> saveAll(List<PlayerScore> scores) {
        for (var score : scores) {
            saveScoreAndNewHistory(score);
        }
        return scores;
    }

    private void saveScoreAndNewHistory(PlayerScore score) {
        var persistedId = jpaRepository.upsert(
                score.id(), score.gameId(), score.playerId(), score.faction().name(), score.totalScore());
        reloadIfManaged(persistedId);

        var alreadyPersisted = (int) historyJpaRepository.countByPlayerScoreId(persistedId);
        if (alreadyPersisted >= score.history().size()) {
            // Lost a first-insert race: the row upsert() returned already has at least as
            // much history as this in-memory aggregate knows about — nothing new to add.
            return;
        }
        var newEntries =
                score.history().subList(alreadyPersisted, score.history().size());
        var newHistoryRows = newEntries.stream()
                .map(entry ->
                        PlayerScoreHistoryJpaEntity.fromDomain(persistedId, score.gameId(), score.playerId(), entry))
                .toList();
        historyJpaRepository.saveAll(newHistoryRows);
    }

    // upsert() is native SQL, so a row already loaded in this transaction stays managed with its
    // pre-write total and any later read in the same transaction gets that stale instance back
    // instead of the row just written.
    private void reloadIfManaged(UUID persistedId) {
        var managed = entityManager.find(PlayerScoreJpaEntity.class, persistedId);
        if (managed != null) {
            entityManager.refresh(managed);
        }
    }

    private PlayerScore toDomain(PlayerScoreJpaEntity entity, List<PlayerScoreHistoryJpaEntity> historyEntities) {
        var history = historyEntities.stream()
                .map(PlayerScoreHistoryJpaEntity::toDomain)
                .toList();
        return PlayerScore.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getPlayerId(),
                Faction.valueOf(entity.getFaction()),
                entity.getTotalScore(),
                history);
    }
}
