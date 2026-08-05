package io.github.temporalrift.game.action.infrastructure.adapter.out.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundStartedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActionRoundTimerExpiredPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ActivistDeclarationRecordedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.BandedProbabilityPublishedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.CardPlayedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeBehaviorChangedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.ExposeSignatureRevealedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.PlayerSkippedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.RoundSummaryPublishedPayload;
import io.github.temporalrift.asyncapi.actionevents.GeneratedChannelContract.SpecialActionPlayedPayload;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.ActionRoundTimerExpired;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.event.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.ExposeBehaviorChanged;
import io.github.temporalrift.game.action.domain.event.ExposeSignatureRevealed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.RoundSummaryPublished;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;
import io.github.temporalrift.game.shared.SpecialAction;

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

        var activistDeclarationRecorded = new ActivistDeclarationRecorded(
                gameId, 1, 2, playerId, ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID());
        var activistDeclarationRecordedWire = mock(ActivistDeclarationRecordedPayload.class);
        given(mapper.toWire(activistDeclarationRecorded)).willReturn(activistDeclarationRecordedWire);

        var actionRoundStarted = new ActionRoundStarted(gameId, 1, 2, 30, List.of(playerId));
        var actionRoundStartedWire = mock(ActionRoundStartedPayload.class);
        given(mapper.toWire(actionRoundStarted)).willReturn(actionRoundStartedWire);

        var cardPlayed = new CardPlayed(
                gameId,
                1,
                2,
                playerId,
                UUID.randomUUID(),
                CardType.PUSH,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
        var cardPlayedWire = mock(CardPlayedPayload.class);
        given(mapper.toWire(cardPlayed)).willReturn(cardPlayedWire);

        var exposeSignatureRevealed = new ExposeSignatureRevealed(
                gameId,
                1,
                2,
                playerId,
                UUID.randomUUID(),
                new ProbabilityInfluenceSignature(
                        CardType.PUSH, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        var exposeSignatureRevealedWire = mock(ExposeSignatureRevealedPayload.class);
        given(mapper.toWire(exposeSignatureRevealed)).willReturn(exposeSignatureRevealedWire);

        var exposeBehaviorChanged = new ExposeBehaviorChanged(gameId, 1, 2, playerId, UUID.randomUUID());
        var exposeBehaviorChangedWire = mock(ExposeBehaviorChangedPayload.class);
        given(mapper.toWire(exposeBehaviorChanged)).willReturn(exposeBehaviorChangedWire);

        var specialActionPlayed = new SpecialActionPlayed(
                gameId,
                1,
                2,
                playerId,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
        var specialActionPlayedWire = mock(SpecialActionPlayedPayload.class);
        given(mapper.toWire(specialActionPlayed)).willReturn(specialActionPlayedWire);

        var actionRoundTimerExpired = new ActionRoundTimerExpired(gameId, 1, 2, List.of(playerId));
        var actionRoundTimerExpiredWire = mock(ActionRoundTimerExpiredPayload.class);
        given(mapper.toWire(actionRoundTimerExpired)).willReturn(actionRoundTimerExpiredWire);

        var playerSkipped = new PlayerSkipped(gameId, 1, 2, playerId, "NO_ACTION");
        var playerSkippedWire = mock(PlayerSkippedPayload.class);
        given(mapper.toWire(playerSkipped)).willReturn(playerSkippedWire);

        var roundSummaryPublished = new RoundSummaryPublished(gameId, 1, 2, List.of());
        var roundSummaryPublishedWire = mock(RoundSummaryPublishedPayload.class);
        given(mapper.toWire(roundSummaryPublished)).willReturn(roundSummaryPublishedWire);

        var bandedProbabilityPublished = new BandedProbabilityPublished(gameId, 1, List.of());
        var bandedProbabilityPublishedWire = mock(BandedProbabilityPublishedPayload.class);
        given(mapper.toWire(bandedProbabilityPublished)).willReturn(bandedProbabilityPublishedWire);

        var activistDeclarationRecordedEnvelope = envelope(gameId, activistDeclarationRecorded);
        var actionRoundStartedEnvelope = envelope(gameId, actionRoundStarted);
        var cardPlayedEnvelope = envelope(gameId, cardPlayed);
        var exposeSignatureRevealedEnvelope = envelope(gameId, exposeSignatureRevealed);
        var exposeBehaviorChangedEnvelope = envelope(gameId, exposeBehaviorChanged);
        var specialActionPlayedEnvelope = envelope(gameId, specialActionPlayed);
        var actionRoundTimerExpiredEnvelope = envelope(gameId, actionRoundTimerExpired);
        var playerSkippedEnvelope = envelope(gameId, playerSkipped);
        var roundSummaryPublishedEnvelope = envelope(gameId, roundSummaryPublished);
        var bandedProbabilityPublishedEnvelope = envelope(gameId, bandedProbabilityPublished);

        adapter.publish(activistDeclarationRecordedEnvelope);
        adapter.publish(actionRoundStartedEnvelope);
        adapter.publish(cardPlayedEnvelope);
        adapter.publish(exposeSignatureRevealedEnvelope);
        adapter.publish(exposeBehaviorChangedEnvelope);
        adapter.publish(specialActionPlayedEnvelope);
        adapter.publish(actionRoundTimerExpiredEnvelope);
        adapter.publish(playerSkippedEnvelope);
        adapter.publish(roundSummaryPublishedEnvelope);
        adapter.publish(bandedProbabilityPublishedEnvelope);

        then(outboundEvents)
                .should()
                .publish(
                        eq("ActivistDeclarationRecorded"),
                        same(activistDeclarationRecordedWire),
                        same(activistDeclarationRecordedEnvelope));
        then(outboundEvents)
                .should()
                .publish(eq("ActionRoundStarted"), same(actionRoundStartedWire), same(actionRoundStartedEnvelope));
        then(outboundEvents).should().publish(eq("CardPlayed"), same(cardPlayedWire), same(cardPlayedEnvelope));
        then(outboundEvents)
                .should()
                .publish(
                        eq("ExposeSignatureRevealed"),
                        same(exposeSignatureRevealedWire),
                        same(exposeSignatureRevealedEnvelope));
        then(outboundEvents)
                .should()
                .publish(
                        eq("ExposeBehaviorChanged"),
                        same(exposeBehaviorChangedWire),
                        same(exposeBehaviorChangedEnvelope));
        then(outboundEvents)
                .should()
                .publish(eq("SpecialActionPlayed"), same(specialActionPlayedWire), same(specialActionPlayedEnvelope));
        then(outboundEvents)
                .should()
                .publish(
                        eq("ActionRoundTimerExpired"),
                        same(actionRoundTimerExpiredWire),
                        same(actionRoundTimerExpiredEnvelope));
        then(outboundEvents)
                .should()
                .publish(eq("PlayerSkipped"), same(playerSkippedWire), same(playerSkippedEnvelope));
        then(outboundEvents)
                .should()
                .publish(
                        eq("RoundSummaryPublished"),
                        same(roundSummaryPublishedWire),
                        same(roundSummaryPublishedEnvelope));
        then(outboundEvents)
                .should()
                .publish(
                        eq("BandedProbabilityPublished"),
                        same(bandedProbabilityPublishedWire),
                        same(bandedProbabilityPublishedEnvelope));
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
