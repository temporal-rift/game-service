package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;

public record EraStarted(
        UUID gameId, int eraNumber, List<PendingCarryOverEvent> carryOverEvents, List<UUID> playerIds) {}
