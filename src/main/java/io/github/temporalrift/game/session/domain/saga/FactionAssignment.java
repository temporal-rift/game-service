package io.github.temporalrift.game.session.domain.saga;

import java.util.UUID;

public record FactionAssignment(UUID playerId, String faction) {}
