package io.github.temporalrift.game.session.domain.ending;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Who wins among the players who qualified for normal victory at the same era boundary. */
public final class NormalVictoryPolicy {

    private NormalVictoryPolicy() {}

    /**
     * Every qualifier when the tiebreak is off; otherwise only the qualifiers holding the highest total among them,
     * who share the win when tied.
     */
    public static <T> List<T> winners(List<T> qualifiers, ToIntFunction<T> total, boolean scoreTiebreak) {
        Objects.requireNonNull(qualifiers, "qualifiers must not be null");
        Objects.requireNonNull(total, "total must not be null");
        if (!scoreTiebreak || qualifiers.size() <= 1) {
            return List.copyOf(qualifiers);
        }
        int highest = qualifiers.stream().mapToInt(total).max().orElseThrow();
        return qualifiers.stream()
                .filter(qualifier -> total.applyAsInt(qualifier) == highest)
                .toList();
    }
}
