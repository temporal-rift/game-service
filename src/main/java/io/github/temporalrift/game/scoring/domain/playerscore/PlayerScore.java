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
        return applyScore(eraNumber, reason);
    }

    /**
     * Applies the one-time game-end score entry outside the numbered-era scoring loop.
     *
     * <p>The history records this entry with era {@code 0}, which is reserved exclusively for
     * {@link ScoreReason#FACTION_UNIDENTIFIED}; callers must use {@link #apply(int, ScoreReason)}
     * for ordinary era scoring.
     */
    public ScoreEntry applyEndGame(ScoreReason reason) {
        if (reason != ScoreReason.FACTION_UNIDENTIFIED) {
            throw new IllegalArgumentException("only FACTION_UNIDENTIFIED may be applied at game end");
        }
        return applyScore(0, reason);
    }

    private ScoreEntry applyScore(int eraNumber, ScoreReason reason) {
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
