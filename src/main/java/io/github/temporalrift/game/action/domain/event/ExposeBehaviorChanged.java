package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

/** Filtered public payload that intentionally excludes the target's Round-3 signature. */
public record ExposeBehaviorChanged(
        UUID gameId, int eraNumber, int roundNumber, UUID activistPlayerId, UUID targetPlayerId)
        implements ActionEventPayload {}
