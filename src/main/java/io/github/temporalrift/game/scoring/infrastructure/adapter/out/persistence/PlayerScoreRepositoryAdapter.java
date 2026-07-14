package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;

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
        return jpaRepository.findAllByGameId(gameId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlayerScore> findAllByGameIdWithLock(UUID gameId) {
        return jpaRepository.findAllByGameIdWithLock(gameId).stream()
                .map(this::toDomain)
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

    private PlayerScore toDomain(PlayerScoreJpaEntity entity) {
        var history = historyJpaRepository.findAllByPlayerScoreIdOrderByEraNumberAsc(entity.getId()).stream()
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
