package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.CardType;

public record ParadoxResolutionCardPlayed(
        UUID gameId,
        int eraNumber,
        UUID playerId,
        UUID cardInstanceId,
        CardType cardType,
        UUID targetEventId,
        UUID targetOutcomeId)
        implements ActionEventPayload {}
