package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.envelope.EventEnvelope;

@ExtendWith(MockitoExtension.class)
class ActionEventPublisherAdapterTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Test
    @DisplayName("publish delegates the envelope to Spring application events")
    void publish_delegatesEnvelope() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher);
        var envelope = new EventEnvelope(
                UUID.randomUUID(),
                "io.github.temporalrift.events.action.ActionRoundClosed",
                UUID.randomUUID(),
                "ActionRound",
                UUID.randomUUID(),
                Instant.now(),
                1,
                new ActionRoundClosed(UUID.randomUUID(), 1, 2, "ALL_SUBMITTED", 3));

        // when
        adapter.publish(envelope);

        // then
        then(applicationEventPublisher).should().publishEvent(envelope);
    }

    @Test
    @DisplayName("publishInternally delegates the payload to Spring application events")
    void publishInternally_delegatesPayload() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher);
        var payload = new ActionRoundClosed(UUID.randomUUID(), 1, 2, "ALL_SUBMITTED", 3);

        // when
        adapter.publishInternally(payload);

        // then
        then(applicationEventPublisher).should().publishEvent(payload);
    }
}
