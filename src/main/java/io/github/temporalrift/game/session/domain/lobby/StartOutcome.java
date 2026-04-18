package io.github.temporalrift.game.session.domain.lobby;

import java.util.List;
import java.util.UUID;

public sealed interface StartOutcome {

    record GameStarted() implements StartOutcome {}

    record NotHost() implements StartOutcome {}

    record NotEnoughPlayers(int currentPlayerCount, int minPlayers) implements StartOutcome {}

    record HasDisconnectedPlayers(List<UUID> disconnectedPlayerIds) implements StartOutcome {}
}
