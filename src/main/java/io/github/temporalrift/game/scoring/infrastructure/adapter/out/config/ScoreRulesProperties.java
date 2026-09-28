package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.ScoreRulesPort;

@ConfigurationProperties("game.rules.scoring")
@Validated
public record ScoreRulesProperties(@NotNull Map<ScoreReason, Integer> scoreDeltas) implements ScoreRulesPort {

    public ScoreRulesProperties {
        Objects.requireNonNull(scoreDeltas, "game.rules.scoring.score-deltas must not be null");
        var normalized = new EnumMap<ScoreReason, Integer>(ScoreReason.class);
        normalized.putAll(scoreDeltas);
        if (!normalized.containsKey(ScoreReason.EXPOSE_SIGNATURE_REVEALED)
                && normalized.containsKey(ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR)) {
            normalized.put(
                    ScoreReason.EXPOSE_SIGNATURE_REVEALED, normalized.get(ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR));
        }
        if (!normalized.containsKey(ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR)
                && normalized.containsKey(ScoreReason.EXPOSE_SIGNATURE_REVEALED)) {
            normalized.put(
                    ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR, normalized.get(ScoreReason.EXPOSE_SIGNATURE_REVEALED));
        }
        requireAllReasons(normalized);
        scoreDeltas = Map.copyOf(normalized);
    }

    private static void requireAllReasons(Map<ScoreReason, Integer> values) {
        Objects.requireNonNull(values, "game.rules.scoring.score-deltas must not be null");
        var missing = Arrays.stream(ScoreReason.values())
                .filter(reason -> !values.containsKey(reason) || values.get(reason) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("game.rules.scoring.score-deltas is missing entries for: " + missing);
        }
    }

    @Override
    public int pointsDelta(ScoreReason reason) {
        return scoreDeltas.get(Objects.requireNonNull(reason, "reason must not be null"));
    }
}
