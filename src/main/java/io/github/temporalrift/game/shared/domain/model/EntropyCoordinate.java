package io.github.temporalrift.game.shared.domain.model;

import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;

/**
 * Stable semantic position of a choice or identity inside a game: never wall time, delivery order or a transport
 * identifier. Components that do not apply stay {@code null}.
 */
public record EntropyCoordinate(Integer era, Integer round, UUID player, Integer slot, UUID subject) {

    private static final EntropyCoordinate NONE = new EntropyCoordinate(null, null, null, null, null);

    public static EntropyCoordinate none() {
        return NONE;
    }

    public EntropyCoordinate era(int value) {
        return new EntropyCoordinate(value, round, player, slot, subject);
    }

    public EntropyCoordinate round(int value) {
        return new EntropyCoordinate(era, value, player, slot, subject);
    }

    public EntropyCoordinate player(UUID value) {
        return new EntropyCoordinate(era, round, value, slot, subject);
    }

    public EntropyCoordinate slot(int value) {
        return new EntropyCoordinate(era, round, player, value, subject);
    }

    public EntropyCoordinate subject(UUID value) {
        return new EntropyCoordinate(era, round, player, slot, value);
    }

    /** Renders the canonical text, letting the caller substitute a stable label for the player component. */
    public String render(Function<UUID, String> playerLabel) {
        var joiner = new StringJoiner(";");
        if (era != null) {
            joiner.add("era=" + era);
        }
        if (round != null) {
            joiner.add("round=" + round);
        }
        if (player != null) {
            joiner.add("player=" + playerLabel.apply(player));
        }
        if (slot != null) {
            joiner.add("slot=" + slot);
        }
        if (subject != null) {
            joiner.add("subject=" + subject);
        }
        return joiner.toString();
    }
}
