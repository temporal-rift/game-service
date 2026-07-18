package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.UUID;

public record ChainCompleted(UUID gameId, int eraNumber, UUID chainId, UUID playerId, List<ChainLink> links) {

    public record ChainLink(UUID eventId, UUID outcomeId, int eraNumber) {}
}
