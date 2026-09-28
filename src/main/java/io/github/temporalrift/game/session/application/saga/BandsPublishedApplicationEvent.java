package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

public record BandsPublishedApplicationEvent(UUID gameId, int eraNumber) {}
