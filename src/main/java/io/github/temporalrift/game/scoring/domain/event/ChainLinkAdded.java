package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record ChainLinkAdded(UUID gameId, int eraNumber, UUID chainId, UUID playerId) {}
