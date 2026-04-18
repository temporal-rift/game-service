package io.github.temporalrift.game.session.application.saga;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.session.FactionsDrawn;
import io.github.temporalrift.events.session.GameStarted;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

// Stub: saga state is not persisted (blocked by #3). Compensation flows not yet implemented.
@Service
class GameStartSagaImpl implements GameStartSaga {

    private static final int DECK_SIZE = 30;

    private final LobbyRepository lobbyRepository;
    private final GameRepository gameRepository;
    private final SessionEventPublisher eventPublisher;
    private final GameRulesPort gameRules;
    private final Clock clock;
    private final SecureRandom random;

    GameStartSagaImpl(
            LobbyRepository lobbyRepository,
            GameRepository gameRepository,
            SessionEventPublisher eventPublisher,
            GameRulesPort gameRules,
            Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
        this.random = new SecureRandom();
    }

    @Override
    public void start(UUID gameId, Lobby lobby) {
        var players = lobby.currentPlayers();
        var factions = drawFactions(players.size());

        for (var i = 0; i < players.size(); i++) {
            var playerId = players.get(i).playerId();
            var faction = factions.get(i);
            lobby.assignFaction(playerId, faction);
            eventPublisher.publish(EventEnvelope.create(
                    lobby.id(),
                    Lobby.AGGREGATE_TYPE,
                    gameId,
                    1,
                    new FactionAssigned(gameId, playerId, faction.name())));
        }

        var factionNames = factions.stream().map(Faction::name).toList();
        eventPublisher.publish(EventEnvelope.create(
                lobby.id(), Lobby.AGGREGATE_TYPE, gameId, 1, new FactionsDrawn(gameId, lobby.id(), factionNames)));

        lobby.start();
        lobbyRepository.save(lobby);

        var playerIds = players.stream().map(p -> p.playerId()).toList();
        var game = new Game(gameId, lobby.id(), buildDeck());
        gameRepository.save(game);

        eventPublisher.publish(EventEnvelope.create(
                lobby.id(),
                Lobby.AGGREGATE_TYPE,
                gameId,
                1,
                new GameStarted(gameId, lobby.id(), playerIds, factions.size(), DECK_SIZE)));

        eventPublisher.publish(
                EventEnvelope.create(game.id(), Game.AGGREGATE_TYPE, gameId, 1, new EraStarted(gameId, 1, List.of())));
    }

    private List<Faction> drawFactions(int count) {
        var roster = new ArrayList<>(Arrays.asList(Faction.values()));
        Collections.shuffle(roster, random);
        return List.copyOf(roster.subList(0, count));
    }

    private List<UUID> buildDeck() {
        var deck = new ArrayList<UUID>(DECK_SIZE);
        for (var i = 0; i < DECK_SIZE; i++) {
            deck.add(UUID.randomUUID());
        }
        return Collections.unmodifiableList(deck);
    }
}
