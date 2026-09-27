package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record ChainLinkThreaded(UUID gameId, int eraNumber, UUID playerId) {}
