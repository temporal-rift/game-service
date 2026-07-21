package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka.model.ScoresUpdatedPayload;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ScoresUpdated;

@ExtendWith(MockitoExtension.class)
class ScoringEventPublisherAdapterTest {

    @Mock
    DefaultServiceEventsProducer producer;

    @Mock
    ScoringEventWireMapper mapper;

    @Test
    void publish_mapsScoresUpdatedAndDispatchesItWithEnvelopeHeaders() {
        var adapter = new ScoringEventPublisherAdapter(producer, mapper);
        var eventId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var payload = new ScoresUpdated(
                gameId, 1, List.of(new ScoresUpdated.ScoreUpdate(UUID.randomUUID(), Faction.ERASERS, 3, "CHAIN", 12)));
        var envelope = new DomainEventEnvelope<>(eventId, aggregateId, "Scoring", gameId, Instant.now(), 2, payload);
        var wirePayload = new ScoresUpdatedPayload();
        var headers = ArgumentCaptor.forClass(DefaultServiceEventsProducer.ScoresUpdatedPayloadHeaders.class);
        given(mapper.toWire(payload)).willReturn(wirePayload);

        adapter.publish(envelope);

        then(producer).should().publishScoresUpdated(eq(wirePayload), headers.capture());
        assertThat(headers.getValue())
                .containsEntry("eventId", eventId.toString())
                .containsEntry("aggregateId", aggregateId.toString())
                .containsEntry("aggregateType", "Scoring")
                .containsEntry("gameId", gameId.toString())
                .containsEntry("occurredAt", envelope.occurredAt())
                .containsEntry("version", 2);
    }

    @Test
    void publish_rejectsAnUnsupportedPayload() {
        var adapter = new ScoringEventPublisherAdapter(producer, mapper);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.publish(DomainEventEnvelope.create(
                        UUID.randomUUID(), "Scoring", UUID.randomUUID(), 1, new Object(), java.time.Clock.systemUTC())))
                .withMessageContaining("Unsupported scoring event payload");
        then(producer).shouldHaveNoInteractions();
        then(mapper).shouldHaveNoInteractions();
    }
}
