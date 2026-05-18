package io.github.temporalrift.game.session.application.saga;

import java.util.List;
import java.util.UUID;

public record StartActionRoundApplicationEvent(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {}
