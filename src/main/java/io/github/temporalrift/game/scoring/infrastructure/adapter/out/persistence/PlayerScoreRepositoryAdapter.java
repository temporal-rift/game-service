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
        var entity = jpaRepository.findById(score.id()).orElseGet(PlayerScoreJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(score.id());
            entity.setGameId(score.gameId());
            entity.setPlayerId(score.playerId());
        }
        entity.setFaction(score.faction().name());
        entity.setTotalScore(score.totalScore());
        jpaRepository.save(entity);

        var alreadyPersisted = (int) historyJpaRepository.countByPlayerScoreId(score.id());
        var newEntries =
                score.history().subList(alreadyPersisted, score.history().size());
        var newHistoryRows = newEntries.stream()
                .map(entry ->
                        PlayerScoreHistoryJpaEntity.fromDomain(score.id(), score.gameId(), score.playerId(), entry))
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
