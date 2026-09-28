package io.github.temporalrift.game.session.domain.ending;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.temporalrift.game.shared.domain.model.Faction;

/** Winner selection for the timeline collapse and timeline stabilization endings. */
public final class SpecialEndingPolicy {

    private SpecialEndingPolicy() {}

    /**
     * A player's position at a special ending. {@code objectiveProgress} is the faction objective's progress count:
     * written outcomes resolved as written for a Prophet, the intact chain's confirmed links for a Weaver.
     */
    public record Standing(UUID playerId, Faction faction, int score, int objectiveProgress) {

        public Standing {
            Objects.requireNonNull(playerId, "playerId must not be null");
            Objects.requireNonNull(faction, "faction must not be null");
        }
    }

    public static Set<UUID> collapseWinners(List<Standing> standings) {
        return highestScorers(standings);
    }

    /** Qualifying Prophets and Weavers win; when none qualifies, the highest scorers win. */
    public static Set<UUID> stabilizationWinners(List<Standing> standings, StabilizationThresholds thresholds) {
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        var qualifiers = standings.stream()
                .filter(standing -> qualifiesForStabilization(standing, thresholds))
                .map(Standing::playerId)
                .collect(Collectors.toUnmodifiableSet());
        return qualifiers.isEmpty() ? highestScorers(standings) : qualifiers;
    }

    public static boolean qualifiesForStabilization(Standing standing, StabilizationThresholds thresholds) {
        return switch (standing.faction()) {
            case PROPHETS -> standing.objectiveProgress() >= thresholds.prophetWrittenResolutions();
            case WEAVERS -> standing.objectiveProgress() >= thresholds.weaverActiveChainLinks();
            case ERASERS, REVISIONISTS, ACTIVISTS -> false;
        };
    }

    private static Set<UUID> highestScorers(List<Standing> standings) {
        Objects.requireNonNull(standings, "standings must not be null");
        var highest = standings.stream().mapToInt(Standing::score).max();
        if (highest.isEmpty()) {
            return Set.of();
        }
        return standings.stream()
                .filter(standing -> standing.score() == highest.getAsInt())
                .map(Standing::playerId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
