package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;

/** Private fact giving one participant, its sole viewer, the declaration modes they may use in the open window. */
public record DeclarationOptionsOffered(
        UUID gameId, int eraNumber, UUID playerId, List<ActivistDeclarationMode> eligibleModes)
        implements ActionEventPayload {

    public DeclarationOptionsOffered {
        eligibleModes = List.copyOf(eligibleModes);
        if (eligibleModes.isEmpty()) {
            throw new IllegalArgumentException("A declaration offer must name at least one eligible mode");
        }
    }
}
