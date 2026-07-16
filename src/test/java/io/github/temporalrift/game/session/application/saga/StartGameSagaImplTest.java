package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.session.FactionsDrawn;
import io.github.temporalrift.events.session.GameStarted;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;
import io.github.temporalrift.game.session.domain.lobby.StartOutcome;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@ExtendWith(MockitoExtension.class)
class StartGameSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID REQUESTING_PLAYER_ID = UUID.randomUUID();
    static final Instant JOINED_AT = Instant.parse("2026-01-01T00:00:00Z");
    static final List<UUID> CATALOG_IDS =
            IntStream.range(0, 30).mapToObj(i -> UUID.randomUUID()).toList();
    static final List<LobbyPlayer> TWO_PLAYERS = List.of(
            new LobbyPlayer(UUID.randomUUID(), "Alice", null, JOINED_AT, true),
            new LobbyPlayer(UUID.randomUUID(), "Bob", null, JOINED_AT, true));

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

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    @Test
    @DisplayName("happy path — all events published in order, saga marked COMPLETED")
    void start_happyPath_publishesAllEventsInOrderAndCompletes() {
        // given
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);

        // when
        var result = saga.start(LOBBY_ID, REQUESTING_PLAYER_ID);

        // then
        assertThat(result).isEqualTo(GAME_ID);
        var ordered = inOrder(stateManager, eventPublisher);
        then(stateManager).should(ordered).initRunning(any(), eq(GAME_ID), eq(LOBBY_ID));
        then(eventPublisher).should(ordered, times(2)).publish(envelopeWithPayload(FactionAssigned.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(FactionsDrawn.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(GameStarted.class));
        then(eventPublisher).should(ordered).publish(envelopeWithPayload(EraStarted.class));
        then(stateManager).should(ordered).complete(GAME_ID, LOBBY_ID);
        then(compensator).should(never()).compensate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("faction assignment fails — compensate called with gameId/lobbyId, exception rethrown, "
            + "COMPLETED never saved")
    void start_factionAssignmentFails_compensatesAndRethrows() {
        // given
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        willThrow(new RuntimeException("duplicate faction")).given(lobby).assignFaction(any(), any());

        // when / then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(compensator).should().compensate(any(), eq(GAME_ID), eq(LOBBY_ID), any());
        then(stateManager).should(never()).complete(any(), any());
    }

    @Test
    @DisplayName("compensate receives the same sagaId that was passed to initRunning for this attempt")
    void start_factionAssignmentFails_compensatesWithSameSagaIdAsInitRunning() {
        // given
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        willThrow(new RuntimeException("duplicate faction")).given(lobby).assignFaction(any(), any());

        // when
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));

        // then
        var initRunningSagaId = ArgumentCaptor.forClass(UUID.class);
        var compensateSagaId = ArgumentCaptor.forClass(UUID.class);
        then(stateManager).should().initRunning(initRunningSagaId.capture(), eq(GAME_ID), eq(LOBBY_ID));
        then(compensator).should().compensate(compensateSagaId.capture(), eq(GAME_ID), eq(LOBBY_ID), any());
        assertThat(compensateSagaId.getValue()).isEqualTo(initRunningSagaId.getValue());
    }

    @Test
    @DisplayName("player disconnects during saga — complete throws, compensate is called, COMPLETED not persisted")
    void start_playerDisconnectsDuringSaga_compensatesWhenCompleteFails() {
        // given — saga is RUNNING, disconnect listener marks CANCELLED before complete() runs
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);
        willThrow(new RuntimeException("cancelled")).given(stateManager).complete(any(), any());

        // when / then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(stateManager).should().initRunning(any(), eq(GAME_ID), eq(LOBBY_ID));
        then(stateManager).should().complete(GAME_ID, LOBBY_ID);
        then(compensator).should().compensate(any(), eq(GAME_ID), eq(LOBBY_ID), any());
    }

    @Test
    @DisplayName("happy path — EraStarted also published as typed Spring event for internal saga trigger")
    void start_happyPath_publishesTypedEraStartedForInternalRouting() {
        // given
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        saga.start(LOBBY_ID, REQUESTING_PLAYER_ID);

        // then
        then(applicationEventPublisher).should(times(3)).publishEvent(captor.capture());
        var eraStarted = captor.getAllValues().stream()
                .filter(EraStarted.class::isInstance)
                .map(EraStarted.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(eraStarted.gameId()).isEqualTo(GAME_ID);
        assertThat(eraStarted.eraNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("happy path — FactionAssigned also published as typed Spring event per player")
    void start_happyPath_publishesTypedFactionAssignedPerPlayer() {
        // given
        stubStartableLobby();
        given(lobby.id()).willReturn(LOBBY_ID);
        given(lobby.currentPlayers()).willReturn(TWO_PLAYERS);
        given(futureEventCatalog.allEventIds()).willReturn(CATALOG_IDS);
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        saga.start(LOBBY_ID, REQUESTING_PLAYER_ID);

        // then
        then(applicationEventPublisher).should(times(3)).publishEvent(captor.capture());
        var factionAssignedEvents = captor.getAllValues().stream()
                .filter(FactionAssigned.class::isInstance)
                .map(FactionAssigned.class::cast)
                .toList();
        assertThat(factionAssignedEvents)
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.gameId()).isEqualTo(GAME_ID));
        assertThat(factionAssignedEvents.stream().map(FactionAssigned::playerId))
                .containsExactlyInAnyOrderElementsOf(
                        TWO_PLAYERS.stream().map(LobbyPlayer::playerId).toList());
    }

    @Test
    @DisplayName("lobby not found — throws before saga state is initialized")
    void start_lobbyNotFound_throwsBeforeInitializingSagaState() {
        // given
        given(lobbyRepository.findByIdWithLock(LOBBY_ID)).willReturn(Optional.empty());

        // when / then
        assertThatExceptionOfType(LobbyNotFoundException.class)
                .isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(stateManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("not host — throws before saga state is initialized")
    void start_notHost_throwsBeforeInitializingSagaState() {
        // given
        stubLobbyStartOutcome(new StartOutcome.NotHost());

        // when / then
        assertThatExceptionOfType(NotLobbyHostException.class)
                .isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(stateManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("not enough players — throws before saga state is initialized")
    void start_notEnoughPlayers_throwsBeforeInitializingSagaState() {
        // given
        stubLobbyStartOutcome(new StartOutcome.NotEnoughPlayers(2, 3));

        // when / then
        assertThatExceptionOfType(NotEnoughPlayersException.class)
                .isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(stateManager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("disconnected players — throws before saga state is initialized")
    void start_disconnectedPlayers_throwsBeforeInitializingSagaState() {
        // given
        stubLobbyStartOutcome(new StartOutcome.HasDisconnectedPlayers(List.of(UUID.randomUUID())));

        // when / then
        assertThatExceptionOfType(DisconnectedPlayersException.class)
                .isThrownBy(() -> saga.start(LOBBY_ID, REQUESTING_PLAYER_ID));
        then(stateManager).shouldHaveNoInteractions();
    }

    private void stubStartableLobby() {
        stubLobbyStartOutcome(new StartOutcome.GameStarted());
    }

    private void stubLobbyStartOutcome(StartOutcome startOutcome) {
        given(lobbyRepository.findByIdWithLock(LOBBY_ID)).willReturn(Optional.of(lobby));
        given(lobby.requestStart(REQUESTING_PLAYER_ID)).willReturn(startOutcome);
        given(lobby.gameId()).willReturn(GAME_ID);
    }
}
