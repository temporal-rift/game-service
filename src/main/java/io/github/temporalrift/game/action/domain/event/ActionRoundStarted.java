package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

public record ActionRoundStarted(
        UUID gameId, int eraNumber, int roundNumber, int timerSeconds, List<UUID> pendingPlayerIds)
        implements ActionEventPayload {}
