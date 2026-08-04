package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ActionEventPublisherAdapterTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    ActionEventWireMapper mapper;

    @Mock
    OutboundIntegrationEventPublisher outboundEvents;

    @Test
    void publishRoundClosed_usesStableMessageType() {
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        var payload = new ActionRoundClosed(gameId, 1, 2, "ALL_SUBMITTED", 3);
        var event = DomainEventEnvelope.create(
                UUID.randomUUID(), "ActionRound", gameId, 1, payload, java.time.Clock.systemUTC());

        adapter.publishRoundClosed(event);

        then(outboundEvents).should().publish(eq("ActionRoundClosed"), any(), eq(event));
    }

    @Test
    void publish_usesAsyncApiMessageNamesRatherThanBindingNames() {
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        adapter.publish(envelope(gameId, new ActionRoundStarted(gameId, 1, 2, 30, List.of(playerId))));
        adapter.publish(envelope(
                gameId,
                new CardPlayed(
                        gameId,
                        1,
                        2,
                        playerId,
                        UUID.randomUUID(),
                        io.github.temporalrift.game.shared.CardType.PUSH,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID())));
        adapter.publish(envelope(gameId, new PlayerSkipped(gameId, 1, 2, playerId, "NO_ACTION")));
        adapter.publish(envelope(
                gameId,
                new SpecialActionPlayed(
                        gameId,
                        1,
                        2,
                        playerId,
                        io.github.temporalrift.game.shared.Faction.ERASERS,
                        io.github.temporalrift.game.shared.SpecialAction.ANNIHILATE,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID())));

        then(outboundEvents).should().publish(eq("ActionRoundStarted"), any(), any());
        then(outboundEvents).should().publish(eq("CardPlayed"), any(), any());
        then(outboundEvents).should().publish(eq("PlayerSkipped"), any(), any());
        then(outboundEvents).should().publish(eq("SpecialActionPlayed"), any(), any());
    }

    @Test
    void publishInternally_delegatesPayload() {
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, mapper, outboundEvents);
        var payload = new ActionRoundClosed(UUID.randomUUID(), 1, 2, "ALL_SUBMITTED", 3);

        adapter.publishInternally(payload);

        then(applicationEventPublisher).should().publishEvent(payload);
    }

    private static DomainEventEnvelope<ActionEventPayload> envelope(UUID gameId, ActionEventPayload payload) {
        return DomainEventEnvelope.create(
                UUID.randomUUID(), "ActionRound", gameId, 1, payload, java.time.Clock.systemUTC());
    }
}
