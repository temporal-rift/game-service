package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record EraEnded(UUID gameId, int eraNumber, int cascadedParadoxCount, int nextEraNumber) {}
