package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record ResolutionStarted(UUID gameId, int eraNumber) {}
