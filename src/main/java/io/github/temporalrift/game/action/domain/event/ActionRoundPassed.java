package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

/**
 * Private fact that a player passed an action round, consuming their slot without acting. It carries no card or
 * action detail; publicly the pass appears only at round close, as the same neutral skip a timer expiry produces.
 */
public record ActionRoundPassed(UUID gameId, int eraNumber, int roundNumber, UUID playerId)
        implements ActionEventPayload {}
