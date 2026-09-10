package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;

@Component
class PlayerScoreRepositoryAdapter implements PlayerScoreRepository {

    private final PlayerScoreJpaRepository jpaRepository;
    private final PlayerScoreHistoryJpaRepository historyJpaRepository;

    PlayerScoreRepositoryAdapter(
            PlayerScoreJpaRepository jpaRepository, PlayerScoreHistoryJpaRepository historyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.historyJpaRepository = historyJpaRepository;
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
        jpaRepository.upsert(
                score.id(), score.gameId(), score.playerId(), score.faction().name(), score.totalScore());
        // Read back rather than RETURNING id: the upsert clears the persistence context, so a row
        // loaded earlier in this transaction would otherwise still read at its pre-write value.
        var persistedId = jpaRepository
                .findByGameIdAndPlayerId(score.gameId(), score.playerId())
                .map(PlayerScoreJpaEntity::getId)
                .orElseThrow(() -> new IllegalStateException("player_score row missing after upsert for game "
                        + score.gameId() + " player " + score.playerId()));

        var alreadyPersisted = (int) historyJpaRepository.countByPlayerScoreId(persistedId);
        if (alreadyPersisted >= score.history().size()) {
            // Lost a first-insert race: the persisted row already has at least as
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
