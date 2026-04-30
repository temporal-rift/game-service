package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.session.FactionsDrawn;
import io.github.temporalrift.events.session.GameStarted;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class StartGameSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final List<UUID> CATALOG_IDS =
            IntStream.range(0, 30).mapToObj(i -> UUID.randomUUID()).toList();
    static final List<LobbyPlayer> TWO_PLAYERS = List.of(
            new LobbyPlayer(UUID.randomUUID(), "Alice", null, Instant.now(), true),
            new LobbyPlayer(UUID.randomUUID(), "Bob", null, Instant.now(), true));

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    GameRepository gameRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    StartGameSagaStateManager stateManager;

    @Mock
    StartGameSagaCompensator compensator;

    @Mock
    FutureEventCatalogPort futureEventCatalog;

    @Mock
    Lobby lobby;

    @InjectMocks
    StartGameSagaImpl saga;

    @Test
    @DisplayName("happy path — all events published in order, saga marked COMPLETED")
    void start_happyPath_publishesAllEventsInOrderAndCompletes() {
        // given
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);

        // when
        saga.start(GAME_ID, lobby);

        // then
        var ordered = inOrder(stateManager, eventPublisher);
        then(stateManager).should(ordered).initRunning(GAME_ID, LOBBY_ID);
        then(eventPublisher).should(ordered, times(2)).publish(envelopeWithPayload(FactionAssigned.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(FactionsDrawn.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(GameStarted.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(EraStarted.class));
        then(stateManager).should(ordered).complete(GAME_ID, LOBBY_ID);
        then(compensator).should(never()).compensate(any(), any());
    }

    @Test
    @DisplayName("faction assignment fails — compensate called with gameId, exception rethrown, COMPLETED never saved")
    void start_factionAssignmentFails_compensatesAndRethrows() {
        // given
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        willThrow(new RuntimeException("duplicate faction")).given(lobby).assignFaction(any(), any());

        // when / then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> saga.start(GAME_ID, lobby));
        then(compensator).should().compensate(eq(GAME_ID), any());
        then(stateManager).should(never()).complete(any(), any());
    }

    @Test
    @DisplayName("player disconnects during saga — complete throws, compensate is called, COMPLETED not persisted")
    void start_playerDisconnectsDuringSaga_compensatesWhenCompleteFails() {
        // given — saga is RUNNING, disconnect listener marks CANCELLED before complete() runs
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);
        willThrow(new RuntimeException("cancelled")).given(stateManager).complete(any(), any());

        // when / then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> saga.start(GAME_ID, lobby));
        then(stateManager).should().initRunning(GAME_ID, LOBBY_ID);
        then(stateManager).should().complete(GAME_ID, LOBBY_ID);
        then(compensator).should().compensate(eq(GAME_ID), any());
    }

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }
}
