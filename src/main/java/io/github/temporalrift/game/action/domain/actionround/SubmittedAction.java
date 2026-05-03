package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;

public sealed interface SubmittedAction permits SubmittedAction.CardAction, SubmittedAction.SpecialActionSubmission {

    UUID playerId();

    record CardAction(UUID playerId, UUID cardInstanceId, CardType cardType, UUID targetEventId, UUID targetOutcomeId)
            implements SubmittedAction {}

    record SpecialActionSubmission(
            UUID playerId,
            Faction faction,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId)
            implements SubmittedAction {}
}
