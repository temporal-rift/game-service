package io.github.temporalrift.game.scoring.domain.playerscore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;

public class PlayerScore {

    public static final String AGGREGATE_TYPE = "PlayerScore";

    private final UUID id;
    private final UUID gameId;
    private final UUID playerId;
    private final Faction faction;
    private final List<ScoreEntry> history;
    private int totalScore;

    public PlayerScore(UUID id, UUID gameId, UUID playerId, Faction faction) {
        this(id, gameId, playerId, faction, 0, List.of());
    }

    private PlayerScore(
            UUID id, UUID gameId, UUID playerId, Faction faction, int totalScore, List<ScoreEntry> history) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.faction = Objects.requireNonNull(faction, "faction must not be null");
        this.totalScore = totalScore;
        this.history = new ArrayList<>(Objects.requireNonNull(history, "history must not be null"));
    }

    public static PlayerScore reconstitute(
            UUID id, UUID gameId, UUID playerId, Faction faction, int totalScore, List<ScoreEntry> history) {
        return new PlayerScore(id, gameId, playerId, faction, totalScore, history);
    }

    public ScoreEntry apply(int eraNumber, ScoreReason reason) {
        validateEra(eraNumber);
        Objects.requireNonNull(reason, "reason must not be null");
        if (!reason.belongsTo(faction)) {
            throw new InvalidScoreReasonException(faction, reason);
        }

        totalScore += reason.pointsDelta();
        var entry = new ScoreEntry(eraNumber, reason, reason.pointsDelta(), totalScore);
        history.add(entry);
        return entry;
    }

    private void validateEra(int eraNumber) {
        if (eraNumber < 1) {
            throw new InvalidScoreEraException(eraNumber);
        }
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

    public List<ScoreEntry> history() {
        return Collections.unmodifiableList(history);
    }
}
