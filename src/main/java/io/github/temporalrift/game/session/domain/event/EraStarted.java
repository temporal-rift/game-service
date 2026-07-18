package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record EraStarted(UUID gameId, int eraNumber, List<UUID> cascadedEventIds, List<UUID> playerIds) {}
