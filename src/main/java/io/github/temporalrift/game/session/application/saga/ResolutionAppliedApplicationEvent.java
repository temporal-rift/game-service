package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

/** An era's timeline resolution is recorded on the game, so a scoring result that waited for it can proceed. */
public record ResolutionAppliedApplicationEvent(UUID gameId, int eraNumber) {}
