package io.github.temporalrift.game.scoring.application.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.scoring.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEventPublisher;

@Component
public class UpdateScoresCommandHandler {

    private final PlayerScoreRepository playerScoreRepository;
    private final EraScoringContextRepository contextRepository;
    private final EraScoreEvaluator eraScoreEvaluator;
    private final ScoringEventPublisher scoringEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public UpdateScoresCommandHandler(
            PlayerScoreRepository playerScoreRepository,
            EraScoringContextRepository contextRepository,
            EraScoreEvaluator eraScoreEvaluator,
            ScoringEventPublisher scoringEventPublisher,
            ApplicationEventPublisher applicationEventPublisher) {
        this.playerScoreRepository = Objects.requireNonNull(playerScoreRepository);
        this.contextRepository = Objects.requireNonNull(contextRepository);
        this.eraScoreEvaluator = Objects.requireNonNull(eraScoreEvaluator);
        this.scoringEventPublisher = Objects.requireNonNull(scoringEventPublisher);
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher);
    }

    public void handle(UpdateEraScoresCommand command) {
        var context = contextRepository.getRequired(command.gameId(), command.eraNumber());

        Map<UUID, PlayerScore> scoresByPlayer = new HashMap<>();
        for (var existing : playerScoreRepository.findAllByGameIdWithLock(command.gameId())) {
            scoresByPlayer.put(existing.playerId(), existing);
        }
        for (var player : context.players()) {
            scoresByPlayer.computeIfAbsent(
                    player.playerId(),
                    id -> new PlayerScore(UUID.randomUUID(), command.gameId(), id, player.faction()));
        }

        Map<UUID, Integer> initialTotals = scoresByPlayer.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().totalScore()));

        var decisions = eraScoreEvaluator.evaluate(context, command.outcomes());

        for (var decision : decisions) {
            var score = scoresByPlayer.get(decision.playerId());
            if (score != null) {
                score.apply(decision.eraNumber(), decision.reason());
            }
        }

        var savedScores = playerScoreRepository.saveAll(List.copyOf(scoresByPlayer.values()));
        Map<UUID, PlayerScore> savedByPlayer =
                savedScores.stream().collect(Collectors.toMap(PlayerScore::playerId, Function.identity()));

        Map<UUID, List<PlayerScoreDecision>> decisionsByPlayer =
                decisions.stream().collect(Collectors.groupingBy(PlayerScoreDecision::playerId));

        var updates = context.players().stream()
                .map(player -> buildUpdate(
                        player.playerId(), player.faction(), savedByPlayer, initialTotals, decisionsByPlayer))
                .sorted(Comparator.comparing(ScoresUpdated.ScoreUpdate::playerId))
                .toList();

        var scoresUpdated = new ScoresUpdated(command.gameId(), command.eraNumber(), updates);

        scoringEventPublisher.publish(
                EventEnvelope.create(command.gameId(), PlayerScore.AGGREGATE_TYPE, command.gameId(), 1, scoresUpdated));
        applicationEventPublisher.publishEvent(scoresUpdated);
    }

    private ScoresUpdated.ScoreUpdate buildUpdate(
            UUID playerId,
            Faction faction,
            Map<UUID, PlayerScore> savedByPlayer,
            Map<UUID, Integer> initialTotals,
            Map<UUID, List<PlayerScoreDecision>> decisionsByPlayer) {
        var score = savedByPlayer.get(playerId);
        int newTotal = score != null ? score.totalScore() : 0;

        var playerDecisions = decisionsByPlayer.getOrDefault(playerId, List.of());
        if (playerDecisions.isEmpty()) {
            return new ScoresUpdated.ScoreUpdate(playerId, faction, 0, "NO_SCORE_CHANGE", newTotal);
        }

        int initialTotal = initialTotals.getOrDefault(playerId, 0);
        int pointsDelta = newTotal - initialTotal;
        String reason =
                playerDecisions.stream().map(d -> d.reason().name()).sorted().collect(Collectors.joining(","));

        return new ScoresUpdated.ScoreUpdate(playerId, faction, pointsDelta, reason, newTotal);
    }
}
