package io.github.temporalrift.game.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.shared.domain.DomainEventEnvelope;

@ExtendWith(MockitoExtension.class)
class SagaHandoffPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    Consumer<DomainEventEnvelope<String>> kafkaPublish;

    SagaHandoffPublisher sagaHandoffPublisher;

    @BeforeEach
    void setUp() {
        sagaHandoffPublisher = new SagaHandoffPublisher(applicationEventPublisher);
    }

    @Test
    void publish_sameInstanceOverload_firesBothPathsWithEnvelopePayload() {
        var envelope = DomainEventEnvelope.create(
                UUID.randomUUID(), "Game", UUID.randomUUID(), DomainEventEnvelope.SCHEMA_VERSION_V1, "payload", CLOCK);

        sagaHandoffPublisher.publish(kafkaPublish, envelope);

        then(kafkaPublish).should().accept(envelope);
        then(applicationEventPublisher).should().publishEvent("payload");
    }

    @Test
    void publish_distinctInternalPayloadOverload_firesBothPathsWithDifferentPayloads() {
        var envelope = DomainEventEnvelope.create(
                UUID.randomUUID(), "Game", UUID.randomUUID(), DomainEventEnvelope.SCHEMA_VERSION_V1, "wire", CLOCK);
        var internalPayload = new Object();

        sagaHandoffPublisher.publish(kafkaPublish, envelope, internalPayload);

        then(kafkaPublish).should().accept(envelope);
        then(applicationEventPublisher).should().publishEvent(internalPayload);
    }

    @Test
    void publish_invokesKafkaBeforeInProcess() {
        var envelope = DomainEventEnvelope.create(
                UUID.randomUUID(), "Game", UUID.randomUUID(), DomainEventEnvelope.SCHEMA_VERSION_V1, "payload", CLOCK);
        var callOrder = new java.util.ArrayList<String>();
        Consumer<DomainEventEnvelope<String>> orderedKafkaPublish = e -> callOrder.add("kafka");
        var orderedApplicationEventPublisher = (ApplicationEventPublisher) event -> callOrder.add("internal");
        var publisher = new SagaHandoffPublisher(orderedApplicationEventPublisher);

        publisher.publish(orderedKafkaPublish, envelope);

        assertThat(callOrder).containsExactly("kafka", "internal");
    }
}
