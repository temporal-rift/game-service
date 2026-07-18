package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.ActionRoundClosed;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundClosedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@ExtendWith(MockitoExtension.class)
class ActionEventPublisherAdapterTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    DefaultServiceEventsProducer producer;

    @Mock
    ActionEventWireMapper mapper;

    @Test
    @DisplayName("publish maps a known event and dispatches it through the generated producer")
    void publish_dispatchesToGeneratedProducer() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, producer, mapper);
        var aggregateId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var payload = new ActionRoundClosed(gameId, 1, 2, "ALL_SUBMITTED", 3);
        var envelope = DomainEventEnvelope.create(aggregateId, "ActionRound", gameId, 1, payload);
        var wirePayload = new ActionRoundClosedPayload();
        given(mapper.toWire(payload)).willReturn(wirePayload);

        // when
        adapter.publish(envelope);

        // then
        then(producer)
                .should()
                .publishActionRoundClosed(
                        eq(wirePayload), any(DefaultServiceEventsProducer.ActionRoundClosedPayloadHeaders.class));
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("publish falls back to a legacy EventEnvelope for an unmigrated event type")
    void publish_unmigratedEvent_fallsBackToLegacyEnvelope() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, producer, mapper);
        var aggregateId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        record UnmigratedEvent(UUID gameId) {}
        var envelope = DomainEventEnvelope.create(aggregateId, "ActionRound", gameId, 1, new UnmigratedEvent(gameId));

        // when
        adapter.publish(envelope);

        // then
        then(applicationEventPublisher).should().publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("publishInternally delegates the payload to Spring application events")
    void publishInternally_delegatesPayload() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, producer, mapper);
        var payload = new ActionRoundClosed(UUID.randomUUID(), 1, 2, "ALL_SUBMITTED", 3);

        // when
        adapter.publishInternally(payload);

        // then
        then(applicationEventPublisher).should().publishEvent(payload);
    }
}
