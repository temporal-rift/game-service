package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

/** Private reveal that tells the suppressed player how long Jam blocks faction specials. */
public record PlayerJammed(UUID gameId, int eraNumber, UUID playerId, int jammedUntilRound)
        implements ActionEventPayload {}
