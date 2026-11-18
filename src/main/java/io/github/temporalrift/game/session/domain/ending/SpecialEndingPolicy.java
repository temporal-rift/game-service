package io.github.temporalrift.game.session.domain.ending;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.temporalrift.game.shared.domain.model.Faction;

/**
 * Winner selection for the timeline collapse and timeline stabilization endings. A forfeited (abandoned) player
 * never wins; winners are chosen among the remaining standings.
 */
public final class SpecialEndingPolicy {

    private SpecialEndingPolicy() {}

    /**
     * A player's position at a special ending. {@code objectiveProgress} is the faction objective's progress count:
     * written outcomes resolved as written for a Prophet, the intact chain's confirmed links for a Weaver;
     * {@code objectiveThreshold} is the count that meets the objective.
     */
    public record Standing(
            UUID playerId,
            Faction faction,
            int score,
            int objectiveProgress,
            int objectiveThreshold,
            boolean forfeited) {

        public Standing {
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(faction, "faction must not be null");
        }

        /** Compares objective progress as a fraction of the threshold, exactly; no threshold counts as no progress. */
        int compareProgress(Standing other) {
            long mine = objectiveThreshold <= 0 ? 0 : (long) objectiveProgress * Math.max(other.objectiveThreshold, 1);
            long theirs = other.objectiveThreshold <= 0
                    ? 0
                    : (long) other.objectiveProgress * Math.max(objectiveThreshold, 1);
            return Long.compare(mine, theirs);
        }
    }

    /** The highest scorers win; with the tiebreak on, a tie on that score goes to the most objective progress. */
    public static Set<UUID> collapseWinners(List<Standing> standings, boolean scoreTiebreak) {
        return highestScorers(standings, scoreTiebreak);
    }

    /**
     * With faction claims on, qualifying Prophets and Weavers win; when claims are off or none qualifies, the
     * highest-scoring contenders win, a tie on that score broken by objective progress while the tiebreak is on.
     */
    public static Set<UUID> stabilizationWinners(
            List<Standing> standings, StabilizationThresholds thresholds, boolean scoreTiebreak) {
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        var qualifiers = Objects.requireNonNull(standings, "standings must not be null").stream()
                .filter(standing -> qualifiesForStabilization(standing, thresholds))
                .map(Standing::playerId)
                .collect(Collectors.toUnmodifiableSet());
        return qualifiers.isEmpty() ? highestScorers(standings, scoreTiebreak) : qualifiers;
    }

    public static boolean qualifiesForStabilization(Standing standing, StabilizationThresholds thresholds) {
        if (!thresholds.factionClaims() || standing.forfeited()) {
            return false;
        }
        return switch (standing.faction()) {
            case PROPHETS -> standing.objectiveProgress() >= thresholds.prophetWrittenResolutions();
            case WEAVERS -> standing.objectiveProgress() >= thresholds.weaverActiveChainLinks();
            case ERASERS, REVISIONISTS, ACTIVISTS -> false;
        };
    }

    private static Stream<Standing> contenders(List<Standing> standings) {
        Objects.requireNonNull(standings, "standings must not be null");
        return standings.stream().filter(standing -> !standing.forfeited());
    }

    private static Set<UUID> highestScorers(List<Standing> standings, boolean scoreTiebreak) {
        var highest = contenders(standings).mapToInt(Standing::score).max();
        if (highest.isEmpty()) {
            return Set.of();
        }
        var leaders = contenders(standings)
                .filter(standing -> standing.score() == highest.getAsInt())
                .toList();
        if (scoreTiebreak && leaders.size() > 1) {
            var best = leaders.stream().max(Standing::compareProgress).orElseThrow();
            leaders = leaders.stream()
                    .filter(standing -> standing.compareProgress(best) == 0)
                    .toList();
        }
        return leaders.stream().map(Standing::playerId).collect(Collectors.toUnmodifiableSet());
    }
}
