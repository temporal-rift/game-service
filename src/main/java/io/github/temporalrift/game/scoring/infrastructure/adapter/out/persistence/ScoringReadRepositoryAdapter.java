package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.shared.Faction;

@Component
class ScoringReadRepositoryAdapter implements ScoringReadRepository {

    private final PlayerScoreJpaRepository playerScoreJpaRepository;
    private final PlayerScoreHistoryJpaRepository historyJpaRepository;
    private final ScoringPlayerJpaRepository playerJpaRepository;

    ScoringReadRepositoryAdapter(
            PlayerScoreJpaRepository playerScoreJpaRepository,
            PlayerScoreHistoryJpaRepository historyJpaRepository,
            ScoringPlayerJpaRepository playerJpaRepository) {
        this.playerScoreJpaRepository = playerScoreJpaRepository;
        this.historyJpaRepository = historyJpaRepository;
        this.playerJpaRepository = playerJpaRepository;
    }

    @Override
    public List<CurrentScoreRow> findCurrentScores(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        var scoreEntities = playerScoreJpaRepository.findAllByGameId(gameId);
        if (scoreEntities.isEmpty()) {
            return List.of();
        }

        var namesByPlayerId = playerJpaRepository.findAllByGameId(gameId).stream()
                .collect(Collectors.toMap(ScoringPlayerJpaEntity::getPlayerId, ScoringPlayerJpaEntity::getPlayerName));
        var maxEra = historyJpaRepository.findMaxEraNumberByGameId(gameId);
        var eraNumber = maxEra == null ? 0 : maxEra;

        return scoreEntities.stream()
                .sorted(Comparator.comparing(PlayerScoreJpaEntity::getPlayerId))
                .map(entity -> new CurrentScoreRow(
                        gameId,
                        eraNumber,
                        entity.getPlayerId(),
                        namesByPlayerId.getOrDefault(entity.getPlayerId(), ""),
                        entity.getTotalScore(),
                        Faction.valueOf(entity.getFaction())))
                .toList();
    }

    @Override
    public List<ScoreHistoryRow> findScoreHistory(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        return historyJpaRepository.findAllByGameId(gameId).stream()
                .sorted(Comparator.comparingInt(PlayerScoreHistoryJpaEntity::getEraNumber)
                        .thenComparing(PlayerScoreHistoryJpaEntity::getPlayerId)
                        .thenComparing(PlayerScoreHistoryJpaEntity::getReason))
                .map(entity -> new ScoreHistoryRow(
                        gameId,
                        entity.getEraNumber(),
                        entity.getPlayerId(),
                        entity.getPointsDelta(),
                        ScoreReason.valueOf(entity.getReason())))
                .toList();
    }
}
