package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/** The five-card hand finalised from a player's private pending deal. */
public record HandSelected(
        UUID gameId,
        int eraNumber,
        UUID playerId,
        SelectionOrigin selectionOrigin,
        List<HandDealt.CardInstance> cards) {

    public enum SelectionOrigin {
        PLAYER,
        TIMEOUT_RANDOM
    }
}
