package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraEndedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraFailedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EraStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.EventsDrawnPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionAssignedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionRevealedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.FactionsDrawnPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameEndedAbnormallyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameEndedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartCancelledPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartFailedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.GameStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.HandDealtPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.HostTransferredPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.LobbyClosedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.LobbyCreatedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerAbandonedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerDisconnectedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerJoinedLobbyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.PlayerLeftLobbyPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.ResolutionStartedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineCollapsedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.TimelineStabilizedPayload;
import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.WinConditionMetPayload;
import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.FactionsDrawn;
import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.GameStartCancelled;
import io.github.temporalrift.game.session.domain.event.GameStartFailed;
import io.github.temporalrift.game.session.domain.event.GameStarted;
import io.github.temporalrift.game.session.domain.event.HostTransferred;
import io.github.temporalrift.game.session.domain.event.LobbyClosed;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.FactionRevealed;
import io.github.temporalrift.game.shared.GameEnded;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;
import io.github.temporalrift.game.shared.PlayerJoinedLobby;

@ExtendWith(MockitoExtension.class)
class SessionEventPublisherAdapterTest {

    @Mock
    SessionEventWireMapper mapper;

    @Mock
    OutboundIntegrationEventPublisher outboundEvents;

    /**
     * Every branch is proven to forward the exact object {@code mapper.toWire(...)} returns, not the raw
     * domain payload — the class of bug already found in the {@code ResolutionStarted} branch, which an
     * {@code any()} payload matcher cannot detect. Payloads are mocked rather than constructed since only
     * their identity (not their field values) matters here.
     */
    @Test
    void publish_forwardsTheMappedWirePayloadUnderItsAsyncApiMessageName() {
        var adapter = new SessionEventPublisherAdapter(mapper, outboundEvents);
        var gameId = UUID.randomUUID();

        assertBranch(adapter, gameId, mock(LobbyCreated.class), mock(LobbyCreatedPayload.class), "LobbyCreated");
        assertBranch(
                adapter,
                gameId,
                mock(PlayerJoinedLobby.class),
                mock(PlayerJoinedLobbyPayload.class),
                "PlayerJoinedLobby");
        assertBranch(
                adapter, gameId, mock(PlayerLeftLobby.class), mock(PlayerLeftLobbyPayload.class), "PlayerLeftLobby");
        assertBranch(adapter, gameId, mock(LobbyClosed.class), mock(LobbyClosedPayload.class), "LobbyClosed");
        assertBranch(
                adapter, gameId, mock(HostTransferred.class), mock(HostTransferredPayload.class), "HostTransferred");
        assertBranch(adapter, gameId, mock(EraStarted.class), mock(EraStartedPayload.class), "EraStarted");
        assertBranch(adapter, gameId, mock(EraEnded.class), mock(EraEndedPayload.class), "EraEnded");
        assertBranch(adapter, gameId, mock(EraFailed.class), mock(EraFailedPayload.class), "EraFailed");
        assertBranch(
                adapter, gameId, mock(FactionAssigned.class), mock(FactionAssignedPayload.class), "FactionAssigned");
        assertBranch(adapter, gameId, mock(FactionsDrawn.class), mock(FactionsDrawnPayload.class), "FactionsDrawn");
        assertBranch(
                adapter,
                gameId,
                mock(GameStartCancelled.class),
                mock(GameStartCancelledPayload.class),
                "GameStartCancelled");
        assertBranch(
                adapter, gameId, mock(GameStartFailed.class), mock(GameStartFailedPayload.class), "GameStartFailed");
        assertBranch(adapter, gameId, mock(GameStarted.class), mock(GameStartedPayload.class), "GameStarted");
        assertBranch(
                adapter, gameId, mock(PlayerAbandoned.class), mock(PlayerAbandonedPayload.class), "PlayerAbandoned");
        assertBranch(
                adapter,
                gameId,
                mock(PlayerDisconnected.class),
                mock(PlayerDisconnectedPayload.class),
                "PlayerDisconnected");
        assertBranch(
                adapter, gameId, mock(WinConditionMet.class), mock(WinConditionMetPayload.class), "WinConditionMet");
        assertBranch(
                adapter,
                gameId,
                mock(GameEndedAbnormally.class),
                mock(GameEndedAbnormallyPayload.class),
                "GameEndedAbnormally");
        assertBranch(adapter, gameId, mock(GameEnded.class), mock(GameEndedPayload.class), "GameEnded");
        assertBranch(
                adapter,
                gameId,
                mock(TimelineCollapsed.class),
                mock(TimelineCollapsedPayload.class),
                "TimelineCollapsed");
        assertBranch(
                adapter,
                gameId,
                mock(TimelineStabilized.class),
                mock(TimelineStabilizedPayload.class),
                "TimelineStabilized");
        assertBranch(
                adapter, gameId, mock(FactionRevealed.class), mock(FactionRevealedPayload.class), "FactionRevealed");
        assertBranch(adapter, gameId, mock(EventsDrawn.class), mock(EventsDrawnPayload.class), "EventsDrawn");
        assertBranch(adapter, gameId, mock(HandDealt.class), mock(HandDealtPayload.class), "HandDealt");
        assertBranch(
                adapter,
                gameId,
                mock(ResolutionStarted.class),
                mock(ResolutionStartedPayload.class),
                "ResolutionStarted");
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            LobbyCreated payload,
            LobbyCreatedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            PlayerJoinedLobby payload,
            PlayerJoinedLobbyPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            PlayerLeftLobby payload,
            PlayerLeftLobbyPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            LobbyClosed payload,
            LobbyClosedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            HostTransferred payload,
            HostTransferredPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            EraStarted payload,
            EraStartedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            EraEnded payload,
            EraEndedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            EraFailed payload,
            EraFailedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            FactionAssigned payload,
            FactionAssignedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            FactionsDrawn payload,
            FactionsDrawnPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            GameStartCancelled payload,
            GameStartCancelledPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            GameStartFailed payload,
            GameStartFailedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            GameStarted payload,
            GameStartedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            PlayerAbandoned payload,
            PlayerAbandonedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            PlayerDisconnected payload,
            PlayerDisconnectedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            WinConditionMet payload,
            WinConditionMetPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            GameEndedAbnormally payload,
            GameEndedAbnormallyPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            GameEnded payload,
            GameEndedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            TimelineCollapsed payload,
            TimelineCollapsedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            TimelineStabilized payload,
            TimelineStabilizedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            FactionRevealed payload,
            FactionRevealedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            EventsDrawn payload,
            EventsDrawnPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            HandDealt payload,
            HandDealtPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    private void assertBranch(
            SessionEventPublisherAdapter adapter,
            UUID gameId,
            ResolutionStarted payload,
            ResolutionStartedPayload wirePayload,
            String eventType) {
        given(mapper.toWire(payload)).willReturn(wirePayload);
        var event = envelope(gameId, payload);

        adapter.publish(event);

        then(outboundEvents).should().publish(eq(eventType), same(wirePayload), eq(event));
    }

    @Test
    void publish_rejectsAnUnsupportedPayload() {
        var adapter = new SessionEventPublisherAdapter(mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        record UnsupportedEvent(UUID gameId) {}

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.publish(envelope(gameId, new UnsupportedEvent(gameId))))
                .withMessageContaining("Unsupported session event payload");
    }

    private static DomainEventEnvelope<?> envelope(UUID gameId, Object payload) {
        return DomainEventEnvelope.create(
                UUID.randomUUID(), "Session", gameId, 1, payload, java.time.Clock.systemUTC());
    }
}
