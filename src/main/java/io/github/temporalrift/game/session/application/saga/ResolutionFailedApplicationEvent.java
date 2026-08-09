package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

public record ResolutionFailedApplicationEvent(UUID gameId, int eraNumber, UUID affectedEventId, String reason) {}
