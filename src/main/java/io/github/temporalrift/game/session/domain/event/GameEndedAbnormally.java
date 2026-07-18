package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record GameEndedAbnormally(UUID gameId, String reason) {}
