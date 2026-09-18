package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record ChainBroken(
        UUID gameId, int eraNumber, UUID chainId, UUID playerId, UUID paradoxId, int chainLengthAtBreak) {}
