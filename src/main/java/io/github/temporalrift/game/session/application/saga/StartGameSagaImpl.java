package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.session.FactionsDrawn;
import io.github.temporalrift.events.session.GameStarted;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.game.Game;
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
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

@Service
class StartGameSagaImpl implements StartGameSaga {

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StartGameSagaStateManager stateManager;
    private final StartGameSagaCompensator compensator;
    private final FutureEventCatalogPort futureEventCatalog;
    private final SecureRandom random;

    StartGameSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            StartGameSagaStateManager stateManager,
            StartGameSagaCompensator compensator,
            FutureEventCatalogPort futureEventCatalog) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stateManager = stateManager;
        this.compensator = compensator;
        this.futureEventCatalog = futureEventCatalog;
        this.random = new SecureRandom();
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public UUID start(UUID lobbyId, UUID requestingPlayerId) {
        var lobby = lobbyRepository.findByIdWithLock(lobbyId).orElseThrow(() -> new LobbyNotFoundException(lobbyId));
        var gameId = lobby.gameId();
        validateStartRequest(lobby, requestingPlayerId);

        // Generated here rather than inside initRunning so it is available to the catch block even
        // though the RUNNING row it identifies never durably commits — compensate() runs in its own
        // transaction and cannot see writes from this (about to roll back) one.
        var sagaId = UUID.randomUUID();
        try {
            stateManager.initRunning(sagaId, gameId, lobby.id());

            var assignments = drawFactionAssignments(lobby.currentPlayers());
            applyFactionAssignments(lobby, assignments);
            publishFactionEvents(gameId, lobby, assignments);
            createAndSaveGame(gameId, lobby, assignments);

            stateManager.complete(gameId, lobby.id());
            return gameId;
        } catch (Exception e) {
            compensator.compensate(sagaId, gameId, lobby.id(), e.getMessage());
            throw e;
        }
    }

    private void validateStartRequest(Lobby lobby, UUID requestingPlayerId) {
        switch (lobby.requestStart(requestingPlayerId)) {
            case StartOutcome.GameStarted() -> {
                /* no action needed */
            }
            case StartOutcome.NotHost() -> throw new NotLobbyHostException();
            case StartOutcome.NotEnoughPlayers(var count, var min) -> throw new NotEnoughPlayersException(count, min);
            case StartOutcome.HasDisconnectedPlayers(var ids) -> throw new DisconnectedPlayersException(ids);
        }
    }

    private void applyFactionAssignments(Lobby lobby, List<FactionAssignment> assignments) {
        assignments.forEach(a -> lobby.assignFaction(a.playerId(), a.faction()));
    }

    private void publishFactionEvents(UUID gameId, Lobby lobby, List<FactionAssignment> assignments) {
        assignments.forEach(a -> {
            var factionAssigned =
                    new FactionAssigned(gameId, a.playerId(), a.faction().name());
            eventPublisher.publish(EventEnvelope.create(lobby.id(), Lobby.AGGREGATE_TYPE, gameId, 1, factionAssigned));
            applicationEventPublisher.publishEvent(factionAssigned);
        });
    }

    private void createAndSaveGame(UUID gameId, Lobby lobby, List<FactionAssignment> assignments) {
        var factionNames = assignments.stream().map(a -> a.faction().name()).toList();
        eventPublisher.publish(EventEnvelope.create(
                lobby.id(), Lobby.AGGREGATE_TYPE, gameId, 1, new FactionsDrawn(gameId, lobby.id(), factionNames)));

        lobby.start();
        lobbyRepository.save(lobby);

        var gameDeck = buildDeck();
        var game = new Game(gameId, lobby.id(), gameDeck);
        gameRepository.save(game);

        var playerIds = assignments.stream().map(FactionAssignment::playerId).toList();

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                gameId,
                1,
                new GameStarted(gameId, lobby.id(), playerIds, assignments.size(), gameDeck.size())));

        var eraStarted = new EraStarted(gameId, 1, List.of(), playerIds);
        eventPublisher.publish(EventEnvelope.create(game.id(), Game.AGGREGATE_TYPE, gameId, 1, eraStarted));
        applicationEventPublisher.publishEvent(eraStarted);
    }

    private List<FactionAssignment> drawFactionAssignments(List<LobbyPlayer> players) {
        var roster = new ArrayList<>(Arrays.asList(Faction.values()));
        Collections.shuffle(roster, random);
        return IntStream.range(0, players.size())
                .mapToObj(i -> new FactionAssignment(players.get(i).playerId(), roster.get(i)))
                .toList();
    }

    private List<UUID> buildDeck() {
        var deck = new ArrayList<>(futureEventCatalog.allEventIds());
        Collections.shuffle(deck, random);
        return List.copyOf(deck);
    }
}
