package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.CardType;

public record CardPlayed(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        UUID cardInstanceId,
        CardType cardType,
        UUID targetEventId,
        UUID sourceOutcomeId,
        UUID targetOutcomeId) {}
