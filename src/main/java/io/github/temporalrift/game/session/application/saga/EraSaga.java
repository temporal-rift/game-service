package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;

interface EraSaga {

    void start(UUID gameId, int eraNumber, List<UUID> playerIds, List<PendingCarryOverEvent> carryOverEvents);
}
