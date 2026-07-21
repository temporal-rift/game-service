package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;

import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.FactionRevealed;
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
import io.github.temporalrift.game.session.domain.event.PlayerJoinedLobby;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.GameEnded;
import io.github.temporalrift.game.shared.HandDealt;

@ExtendWith(MockitoExtension.class)
class SessionEventPublisherAdapterTest {

    @Mock
    DefaultServiceEventsProducer producer;

    @Mock
    SessionEventWireMapper mapper;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Test
    void publish_dispatchesEveryGeneratedSessionEvent() {
        var adapter = new SessionEventPublisherAdapter(producer, mapper, applicationEventPublisher);
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        adapter.publish(envelope(gameId, new LobbyCreated(lobbyId, playerId, Instant.now())));
        adapter.publish(envelope(gameId, new PlayerJoinedLobby(lobbyId, playerId, "Ada")));
        adapter.publish(envelope(gameId, new PlayerLeftLobby(lobbyId, playerId)));
        adapter.publish(envelope(gameId, new LobbyClosed(lobbyId, gameId)));
        adapter.publish(envelope(gameId, new HostTransferred(lobbyId, playerId, UUID.randomUUID())));
        adapter.publish(envelope(gameId, new EraStarted(gameId, 1, List.of(UUID.randomUUID()), List.of(playerId))));
        adapter.publish(envelope(gameId, new EraEnded(gameId, 1, 0, 2)));
        adapter.publish(envelope(gameId, new EraFailed(gameId, 1, "PARADOX")));
        adapter.publish(envelope(gameId, new FactionAssigned(gameId, playerId, "ERASERS")));
        adapter.publish(envelope(gameId, new FactionsDrawn(gameId, lobbyId, List.of("ERASERS"))));
        adapter.publish(envelope(gameId, new GameStartCancelled(gameId, lobbyId)));
        adapter.publish(envelope(gameId, new GameStartFailed(gameId, lobbyId, "NOT_READY")));
        adapter.publish(envelope(gameId, new GameStarted(gameId, lobbyId, List.of(playerId), 3, 30)));
        adapter.publish(envelope(gameId, new PlayerAbandoned(gameId, playerId)));
        adapter.publish(envelope(gameId, new PlayerDisconnected(gameId, playerId)));
        adapter.publish(envelope(gameId, new WinConditionMet(gameId, playerId, "ERASERS", 42, "SCORE")));
        adapter.publish(envelope(gameId, new GameEndedAbnormally(gameId, "ADMIN")));
        adapter.publish(envelope(
                gameId,
                new GameEnded(gameId, "WIN", List.of(new GameEnded.PlayerScoreResult(playerId, "ERASERS", 42)))));
        adapter.publish(envelope(
                gameId,
                new TimelineCollapsed(
                        gameId,
                        1,
                        List.of(new TimelineCollapsed.PlayerFactionResult(playerId, "ERASERS")),
                        List.of())));
        adapter.publish(envelope(
                gameId,
                new TimelineStabilized(
                        gameId,
                        List.of(new TimelineStabilized.PlayerFactionResult(playerId, "ERASERS", 2)),
                        List.of())));
        adapter.publish(envelope(
                gameId,
                new FactionRevealed(gameId, List.of(new FactionRevealed.PlayerFactionResult(playerId, "ERASERS")))));
        adapter.publish(envelope(
                gameId,
                new EventsDrawn(
                        gameId,
                        1,
                        List.of(new EventsDrawn.FutureEvent(
                                UUID.randomUUID(),
                                "Event",
                                List.of(new EventsDrawn.Outcome(UUID.randomUUID(), "Outcome", 50)),
                                false)))));
        adapter.publish(envelope(
                gameId,
                new HandDealt(
                        gameId, 1, playerId, List.of(new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH)))));

        then(producer).should().publishLobbyCreated(any(), any());
        then(producer).should().publishPlayerJoinedLobby(any(), any());
        then(producer).should().publishPlayerLeftLobby(any(), any());
        then(producer).should().publishLobbyClosed(any(), any());
        then(producer).should().publishHostTransferred(any(), any());
        then(producer).should().publishEraStarted(any(), any());
        then(producer).should().publishEraEnded(any(), any());
        then(producer).should().publishEraFailed(any(), any());
        then(producer).should().publishFactionAssigned(any(), any());
        then(producer).should().publishFactionsDrawn(any(), any());
        then(producer).should().publishGameStartCancelled(any(), any());
        then(producer).should().publishGameStartFailed(any(), any());
        then(producer).should().publishGameStarted(any(), any());
        then(producer).should().publishPlayerAbandoned(any(), any());
        then(producer).should().publishPlayerDisconnected(any(), any());
        then(producer).should().publishWinConditionMet(any(), any());
        then(producer).should().publishGameEndedAbnormally(any(), any());
        then(producer).should().publishGameEnded(any(), any());
        then(producer).should().publishTimelineCollapsed(any(), any());
        then(producer).should().publishTimelineStabilized(any(), any());
        then(producer).should().publishFactionRevealed(any(), any());
        then(producer).should().publishEventsDrawn(any(), any());
        then(producer).should().publishHandDealt(any(), any());
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void publish_resolutionStartedCreatesAnExternalizedMessage() {
        var adapter = new SessionEventPublisherAdapter(producer, mapper, applicationEventPublisher);
        var gameId = UUID.randomUUID();
        var event = envelope(gameId, new ResolutionStarted(gameId, 1));
        var publishedEvent = ArgumentCaptor.forClass(Object.class);

        adapter.publish(event);

        then(applicationEventPublisher).should().publishEvent(publishedEvent.capture());
        assertThat(publishedEvent.getValue()).isInstanceOf(Message.class);
        var message = (Message<?>) publishedEvent.getValue();
        assertThat(message.getPayload()).isEqualTo(event.payload());
        assertThat(message.getHeaders())
                .containsEntry("eventId", event.eventId().toString())
                .containsEntry("aggregateId", event.aggregateId().toString())
                .containsEntry("gameId", event.gameId().toString())
                .containsEntry("spring.cloud.stream.sendto.destination", "Sessionpublish-resolution-started-out");
        then(producer).shouldHaveNoInteractions();
    }

    @Test
    void publish_rejectsAnUnsupportedPayload() {
        var adapter = new SessionEventPublisherAdapter(producer, mapper, applicationEventPublisher);
        var gameId = UUID.randomUUID();
        record UnsupportedEvent(UUID gameId) {}

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.publish(envelope(gameId, new UnsupportedEvent(gameId))))
                .withMessageContaining("Unsupported session event payload");
    }

    private static DomainEventEnvelope envelope(UUID gameId, Object payload) {
        return DomainEventEnvelope.create(
                UUID.randomUUID(), "Session", gameId, 1, payload, java.time.Clock.systemUTC());
    }
}
