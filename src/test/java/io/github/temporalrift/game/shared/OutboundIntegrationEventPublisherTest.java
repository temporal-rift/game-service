package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardGrade;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardPlayedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardType;
import io.github.temporalrift.asyncapi.scoringevents.GeneratedChannelContract.Faction;
import io.github.temporalrift.asyncapi.scoringevents.GeneratedChannelContract.ScoreUpdate;
import io.github.temporalrift.asyncapi.scoringevents.GeneratedChannelContract.ScoresUpdatedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartedPayload;

@ExtendWith(MockitoExtension.class)
class OutboundIntegrationEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    OutboundIntegrationEventPublisher outboundEvents;

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @BeforeEach
    void setUp() {
        outboundEvents = new OutboundIntegrationEventPublisher(
                applicationEventPublisher, JsonMapper.builder().build(), VALIDATOR_FACTORY.getValidator());
    }

    @Test
    void publish_validSessionPayload_publishesEvent() {
        var gameId = UUID.randomUUID();
        var payload = new GameStartedPayload(gameId, UUID.randomUUID(), List.of(UUID.randomUUID()), 3, 30);

        outboundEvents.publish("GameStarted", payload, envelope(gameId));

        then(applicationEventPublisher).should().publishEvent(any(OutboundIntegrationEvent.class));
    }

    @Test
    void publish_actionPayloadViolatingSizeConstraint_rejectedBeforeOutbox() {
        var gameId = UUID.randomUUID();
        var payload = new CardPlayedPayload(
                gameId,
                1,
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CardType.SCAN,
                CardGrade.II,
                null,
                List.of(),
                null,
                null,
                null);

        assertThatThrownBy(() -> outboundEvents.publish("CardPlayed", payload, envelope(gameId)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("CardPlayed")
                .hasMessageContaining("targetEventIds");

        then(applicationEventPublisher).should(never()).publishEvent(any(OutboundIntegrationEvent.class));
    }

    @Test
    void publish_scoringPayloadWithNestedViolation_rejectedBeforeOutbox() {
        var gameId = UUID.randomUUID();
        var payload = new ScoresUpdatedPayload(
                gameId, 1, List.of(new ScoreUpdate(null, Faction.PROPHETS, 4, "EVENT_RESOLVED_AS_WRITTEN", 12)));

        assertThatThrownBy(() -> outboundEvents.publish("ScoresUpdated", payload, envelope(gameId)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("ScoresUpdated")
                .hasMessageContaining("updates[0].playerId");

        then(applicationEventPublisher).should(never()).publishEvent(any(OutboundIntegrationEvent.class));
    }

    @Test
    void publish_rejectedPayload_doesNotExposeEventData() {
        var gameId = UUID.randomUUID();
        var payload = new GameStartedPayload(null, UUID.randomUUID(), List.of(), 3, 30);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ConstraintViolationException.class,
                () -> outboundEvents.publish("GameStarted", payload, envelope(gameId)));

        assertThat(thrown.getMessage()).contains("gameId");
        assertThat(thrown.getMessage()).doesNotContain(gameId.toString());
        then(applicationEventPublisher).should(never()).publishEvent(any(OutboundIntegrationEvent.class));
    }

    private static DomainEventEnvelope<String> envelope(UUID gameId) {
        return DomainEventEnvelope.create(UUID.randomUUID(), "Lobby", gameId, 1, "test-payload", CLOCK);
    }
}
