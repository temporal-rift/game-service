package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

public record ChainBroken(
        UUID gameId, int eraNumber, UUID chainId, UUID brokenByPlayerId, UUID targetPlayerId, int chainLengthAtBreak) {}
