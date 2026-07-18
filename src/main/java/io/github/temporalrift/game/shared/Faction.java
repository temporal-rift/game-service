package io.github.temporalrift.game.shared;

import java.util.Optional;
import java.util.Set;

public enum Faction {
    ERASERS(Set.of(SpecialAction.ANNIHILATE, SpecialAction.CORRUPT, SpecialAction.CASCADE)),
    PROPHETS(Set.of(SpecialAction.FORESIGHT, SpecialAction.SEAL, SpecialAction.FULFILLMENT)),
    REVISIONISTS(Set.of(SpecialAction.REWRITE, SpecialAction.MIMIC, SpecialAction.OBSCURE)),
    WEAVERS(Set.of(SpecialAction.THREAD, SpecialAction.TAPESTRY, SpecialAction.UNRAVEL)),
    ACTIVISTS(Set.of(SpecialAction.RALLY, SpecialAction.EXPOSE, SpecialAction.MOMENTUM));

    private final Set<SpecialAction> specialActions;

    Faction(Set<SpecialAction> specialActions) {
        this.specialActions = specialActions;
    }

    public Set<SpecialAction> getSpecialActions() {
        return specialActions;
    }

    public boolean hasSpecialAction(SpecialAction specialAction) {
        return specialActions.contains(specialAction);
    }

    /**
     * Parses a faction name, tolerating null or unknown values instead of throwing.
     * Intended for consumers of untrusted or possibly-stale event payloads.
     */
    public static Optional<Faction> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
