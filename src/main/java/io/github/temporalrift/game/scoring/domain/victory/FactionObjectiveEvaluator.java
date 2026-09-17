package io.github.temporalrift.game.scoring.domain.victory;

import java.util.HashSet;
import java.util.Objects;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;

/**
 * History-based faction-objective evaluation at an era boundary. Pure domain logic: counts are
 * derived from the {@link PlayerScore} ledger, thresholds come from the caller so this class stays
 * free of configuration and framework types.
 */
public final class FactionObjectiveEvaluator {

    private FactionObjectiveEvaluator() {}

    public record Progress(int progressCount, int threshold, boolean objectiveMet) {}

    public static Progress evaluate(PlayerScore score, int eraNumber, VictoryRulesPort rules) {
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        return switch (score.faction()) {
            case ERASERS -> progress(eraserAnnihilations(score), rules.eraserAnnihilations());
            case PROPHETS -> progress(prophetWrittenResolutions(score), rules.prophetWrittenResolutions());
            case REVISIONISTS -> progress(revisionistSuccessfulEras(score), rules.revisionistSuccessfulEras());
            case WEAVERS -> weaverProgress(score, rules.weaverChainLength());
            case ACTIVISTS ->
                progress(activistConsecutiveSuccesses(score, eraNumber), rules.activistConsecutiveDeclarations());
        };
    }

    private static Progress progress(int count, int threshold) {
        return new Progress(count, threshold, count >= threshold);
    }

    /**
     * A completed chain satisfies the length requirement exactly: completion fires at length 3 and
     * completed chains reject further links, so a completion counts as a full qualifying chain while
     * anything less counts as no qualifying chain.
     */
    private static Progress weaverProgress(PlayerScore score, int chainLength) {
        boolean completed = weaverCompletions(score) > 0;
        return new Progress(completed ? chainLength : 0, chainLength, completed);
    }

    private static int eraserAnnihilations(PlayerScore score) {
        return (int) score.history().stream()
                .filter(entry -> entry.reason() == ScoreReason.ANNIHILATED_OUTCOME)
                .count();
    }

    private static int prophetWrittenResolutions(PlayerScore score) {
        return (int) score.history().stream()
                .filter(entry -> entry.reason() == ScoreReason.EVENT_RESOLVED_AS_WRITTEN
                        || entry.reason() == ScoreReason.FULFILLMENT_SUCCEEDED)
                .count();
    }

    private static int revisionistSuccessfulEras(PlayerScore score) {
        var eras = new HashSet<Integer>();
        for (var entry : score.history()) {
            if (entry.reason() == ScoreReason.SECRET_OUTCOME_WON) {
                eras.add(entry.eraNumber());
            }
        }
        return eras.size();
    }

    private static int weaverCompletions(PlayerScore score) {
        return (int) score.history().stream()
                .filter(entry -> entry.reason() == ScoreReason.CHAIN_COMPLETED)
                .count();
    }

    private static boolean isActivistSuccess(ScoreReason reason) {
        return reason == ScoreReason.DECLARED_OUTCOME_WON || reason == ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY;
    }

    private static int activistConsecutiveSuccesses(PlayerScore score, int eraNumber) {
        var successEras = new HashSet<Integer>();
        for (var entry : score.history()) {
            if (isActivistSuccess(entry.reason())) {
                successEras.add(entry.eraNumber());
            }
        }
        int streak = 0;
        for (int era = eraNumber; successEras.contains(era); era--) {
            streak++;
        }
        return streak;
    }
}
