package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.model.ActionRoundClosedPayload;
import io.github.temporalrift.game.action.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ProbabilityBand;
import io.github.temporalrift.game.shared.SpecialAction;

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
        adapter.publishRoundClosed(envelope);

        // then
        then(producer)
                .should()
                .publishActionRoundClosed(
                        eq(wirePayload), any(DefaultServiceEventsProducer.ActionRoundClosedPayloadHeaders.class));
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("publish dispatches every action event through its generated producer operation")
    void publish_dispatchesEveryActionEventType() {
        // given
        var adapter = new ActionEventPublisherAdapter(applicationEventPublisher, producer, mapper);
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetId = UUID.randomUUID();

        var actionRoundStarted = new ActionRoundStarted(gameId, 1, 2, 30, List.of(playerId));
        var cardPlayed = new CardPlayed(
                gameId,
                1,
                2,
                playerId,
                UUID.randomUUID(),
                CardType.PUSH,
                targetId,
                UUID.randomUUID(),
                UUID.randomUUID());
        var specialActionPlayed = new SpecialActionPlayed(
                gameId,
                1,
                2,
                playerId,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                targetId,
                UUID.randomUUID(),
                UUID.randomUUID());
        var timerExpired = new ActionRoundTimerExpired(gameId, 1, 2, List.of(playerId));
        var playerSkipped = new PlayerSkipped(gameId, 1, 2, playerId, "NO_ACTION");
        var roundSummary = new RoundSummaryPublished(
                gameId, 1, 2, List.of(new RoundSummaryPublished.ActionSummary(playerId, "CARD", "PUSH", false)));
        var bandedProbability = new BandedProbabilityPublished(
                gameId,
                1,
                List.of(new BandedProbabilityPublished.EventBandState(
                        targetId,
                        List.of(new BandedProbabilityPublished.OutcomeBandState(
                                UUID.randomUUID(), ProbabilityBand.HIGH)))));

        // when
        adapter.publish(envelope(gameId, actionRoundStarted));
        adapter.publish(envelope(gameId, cardPlayed));
        adapter.publish(envelope(gameId, specialActionPlayed));
        adapter.publish(envelope(gameId, timerExpired));
        adapter.publish(envelope(gameId, playerSkipped));
        adapter.publish(envelope(gameId, roundSummary));
        adapter.publish(envelope(gameId, bandedProbability));

        // then
        then(producer).should().publishActionRoundStarted(any(), any());
        then(producer).should().publishCardPlayed(any(), any());
        then(producer).should().publishSpecialActionPlayed(any(), any());
        then(producer).should().publishActionRoundTimerExpired(any(), any());
        then(producer).should().publishPlayerSkipped(any(), any());
        then(producer).should().publishRoundSummaryPublished(any(), any());
        then(producer).should().publishBandedProbabilityPublished(any(), any());
        then(applicationEventPublisher).shouldHaveNoInteractions();
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

    private static DomainEventEnvelope<ActionEventPayload> envelope(UUID gameId, ActionEventPayload payload) {
        return DomainEventEnvelope.create(UUID.randomUUID(), "ActionRound", gameId, 1, payload);
    }
}
