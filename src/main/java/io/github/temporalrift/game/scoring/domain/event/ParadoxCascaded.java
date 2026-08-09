package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.UUID;

public record ParadoxCascaded(
        UUID gameId, int eraNumber, UUID paradoxId, UUID affectedEventId, List<UUID> detonatedByPlayerIds) {}
