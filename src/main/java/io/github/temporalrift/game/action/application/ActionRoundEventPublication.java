package io.github.temporalrift.game.action.application;

import java.time.Clock;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

/** Publishes all events pulled from an action round through both delivery paths. */
public final class ActionRoundEventPublication {

    private ActionRoundEventPublication() {}

    /**
     * Publishes each aggregate event to the external envelope publisher and the in-process event
     * publisher.
     */
    public static void publish(ActionRound round, ActionEventPublisher actionEventPublisher, Clock clock) {
        // Action aggregate events need both publication paths: the outbox envelope for
        // cross-service delivery and the typed payload for the in-process action-round saga.
        for (var payload : round.pullEvents()) {
            publishExternal(round, payload, actionEventPublisher, clock);
            actionEventPublisher.publishInternally(payload);
        }
    }

    private static void publishExternal(
            ActionRound round, Object payload, ActionEventPublisher publisher, Clock clock) {
        switch (payload) {
            case ActionEventPayload actionEvent ->
                publisher.publish(DomainEventEnvelope.create(
                        round.id(),
                        ActionRound.AGGREGATE_TYPE,
                        round.gameId(),
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        actionEvent,
                        clock));
            case ActionRoundClosed roundClosed ->
                publisher.publishRoundClosed(DomainEventEnvelope.create(
                        round.id(),
                        ActionRound.AGGREGATE_TYPE,
                        round.gameId(),
                        DomainEventEnvelope.SCHEMA_VERSION_V1,
                        roundClosed,
                        clock));
            default -> throw new IllegalStateException("Unsupported action aggregate event: " + payload.getClass());
        }
    }
}
