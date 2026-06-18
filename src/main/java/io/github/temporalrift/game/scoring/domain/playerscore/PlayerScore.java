package io.github.temporalrift.game.scoring.domain.playerscore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.WinConditionMet;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.shared.AggregateRoot;

public class PlayerScore extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "PlayerScore";
    public static final String SCORE_THRESHOLD_WIN_TYPE = "SCORE_THRESHOLD";

    private final UUID id;
    private final UUID gameId;
    private final UUID playerId;
    private final Faction faction;
    private final List<ScoreEntry> history;
    private int totalScore;
    private boolean winningScoreRecorded;

    public PlayerScore(UUID id, UUID gameId, UUID playerId, Faction faction) {
        this(id, gameId, playerId, faction, 0, List.of(), false);
    }

    private PlayerScore(
            UUID id,
            UUID gameId,
            UUID playerId,
            Faction faction,
            int totalScore,
            List<ScoreEntry> history,
            boolean winningScoreRecorded) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.faction = Objects.requireNonNull(faction, "faction must not be null");
        this.totalScore = totalScore;
        this.history = new ArrayList<>(Objects.requireNonNull(history, "history must not be null"));
        this.winningScoreRecorded = winningScoreRecorded;
    }

    public static PlayerScore reconstitute(
            UUID id,
            UUID gameId,
            UUID playerId,
            Faction faction,
            int totalScore,
            boolean winningScoreRecorded,
            List<ScoreEntry> history) {
        return new PlayerScore(id, gameId, playerId, faction, totalScore, history, winningScoreRecorded);
    }

    public ScoreEntry apply(int eraNumber, ScoreReason reason, int winScoreThreshold) {
        validateEra(eraNumber);
        validateWinScoreThreshold(winScoreThreshold);
        Objects.requireNonNull(reason, "reason must not be null");
        if (!reason.belongsTo(faction)) {
            throw new InvalidScoreReasonException(faction, reason);
        }

        totalScore += reason.pointsDelta();
        var entry = new ScoreEntry(eraNumber, reason, reason.pointsDelta(), totalScore);
        history.add(entry);
        registerScoresUpdated(eraNumber, entry);
        registerWinConditionIfReached(winScoreThreshold);
        return entry;
    }

    private void validateEra(int eraNumber) {
        if (eraNumber < 1) {
            throw new InvalidScoreEraException(eraNumber);
        }
    }

    private void validateWinScoreThreshold(int winScoreThreshold) {
        if (winScoreThreshold < 1) {
            throw new InvalidWinScoreThresholdException(winScoreThreshold);
        }
    }

    private void registerScoresUpdated(int eraNumber, ScoreEntry entry) {
        var update = new ScoresUpdated.ScoreUpdate(
                playerId, faction, entry.pointsDelta(), entry.reason().name(), totalScore);
        registerEvent(new ScoresUpdated(gameId, eraNumber, List.of(update)));
    }

    private void registerWinConditionIfReached(int winScoreThreshold) {
        if (winningScoreRecorded || totalScore < winScoreThreshold) {
            return;
        }
        winningScoreRecorded = true;
        registerEvent(new WinConditionMet(gameId, playerId, faction.name(), totalScore, SCORE_THRESHOLD_WIN_TYPE));
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public UUID playerId() {
        return playerId;
    }

    public Faction faction() {
        return faction;
    }

    public int totalScore() {
        return totalScore;
    }

    public boolean winConditionRecorded() {
        return winningScoreRecorded;
    }

    public List<ScoreEntry> history() {
        return Collections.unmodifiableList(history);
    }
}
