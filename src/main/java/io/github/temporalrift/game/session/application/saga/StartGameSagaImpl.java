package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.session.FactionsDrawn;
import io.github.temporalrift.events.session.GameStarted;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
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
    private final StartGameSagaStateManager stateManager;
    private final StartGameSagaCompensator compensator;
    private final FutureEventCatalogPort futureEventCatalog;
    private final SecureRandom random;

    StartGameSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            StartGameSagaStateManager stateManager,
            StartGameSagaCompensator compensator,
            FutureEventCatalogPort futureEventCatalog) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.stateManager = stateManager;
        this.compensator = compensator;
        this.futureEventCatalog = futureEventCatalog;
        this.random = new SecureRandom();
    }

    @Override
    @Transactional(propagation = REQUIRES_NEW)
    public void start(UUID gameId, Lobby lobby) {
        try {
            stateManager.initRunning(gameId, lobby.id());

            var assignments = drawFactionAssignments(lobby.currentPlayers());
            applyFactionAssignments(lobby, assignments);
            publishFactionEvents(gameId, lobby, assignments);
            createAndSaveGame(gameId, lobby, assignments);

            stateManager.complete(gameId, lobby.id());
        } catch (Exception e) {
            compensator.compensate(gameId, e.getMessage());
            throw e;
        }
    }

    private void applyFactionAssignments(Lobby lobby, List<FactionAssignment> assignments) {
        assignments.forEach(a -> lobby.assignFaction(a.playerId(), a.faction()));
    }

    private void publishFactionEvents(UUID gameId, Lobby lobby, List<FactionAssignment> assignments) {
        assignments.forEach(a -> eventPublisher.publish(EventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                gameId,
                1,
                new FactionAssigned(gameId, a.playerId(), a.faction().name()))));
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

        eventPublisher.publish(
                EventEnvelope.create(game.id(), Game.AGGREGATE_TYPE, gameId, 1, new EraStarted(gameId, 1, List.of())));
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
