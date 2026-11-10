package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.lobby.ConnectionStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.event.HandSelected;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@GameServiceIntegrationTest
class EraStartedToRoundStartIT {

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    ActionRoundRepository actionRoundRepository;

    @Autowired
    ActionRoundSagaRepository actionRoundSagaRepository;

    @Autowired
    EraSagaRepository eraSagaRepository;

    @Autowired
    FutureEventCatalogPort futureEventCatalog;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    GameRulesPort gameRules;

    @Autowired
    SessionGameRulesPort sessionGameRules;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    HandSelectionRepository handSelectionRepository;

    @Autowired
    io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository declarationPhaseRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    DeclineDeclarationUseCase declineDeclaration;

    @ParameterizedTest
    @EnumSource(
            value = Faction.class,
            names = {"ACTIVISTS", "PROPHETS"})
    void eraStarted_waitsForFinalHandsAndAdvancesAfterDeclarationDecisions(Faction firstFaction) {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;
        var roundNumber = 1;
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(_ -> {
            var lobbyId = UUID.randomUUID();
            gameRepository.save(new Game(gameId, lobbyId, futureEventCatalog.allEventIds()));
            // Opening a round resolves the participants who have not abandoned from the game's lobby.
            var joinedAt = java.time.Instant.parse("2026-01-01T00:00:00Z");
            lobbyRepository.save(new Lobby(
                    lobbyId,
                    gameId,
                    playerIds.getFirst(),
                    playerIds.stream()
                            .map(id -> new LobbyPlayer(id, "Player", null, joinedAt, ConnectionStatus.CONNECTED))
                            .toList(),
                    new LobbyConfig(
                            UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                            1,
                            5,
                            java.time.Clock.fixed(joinedAt, java.time.ZoneOffset.UTC))));
            for (var playerId : playerIds) {
                var player = new PlayerState(UUID.randomUUID(), gameId, playerId);
                player.assignFaction(
                        playerId.equals(playerIds.getFirst())
                                ? firstFaction
                                : playerId.equals(playerIds.get(1)) ? Faction.WEAVERS : Faction.REVISIONISTS);
                playerStateRepository.save(player);
            }
            applicationEventPublisher.publishEvent(new EraStarted(gameId, eraNumber, List.of(), playerIds));
        });

        var selections = playerIds.stream()
                .map(playerId -> awaitHandSelection(gameId, eraNumber, playerId))
                .toList();
        assertThat(awaitEraSagaState(gameId)).satisfies(state -> {
            assertThat(state.status()).isEqualTo(EraSagaStatus.WAITING_HAND_SELECTION);
            assertThat(state.handSelectedPlayerIds()).isEmpty();
        });
        assertThat(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber))
                .isEmpty();

        transactionTemplate.executeWithoutResult(_ ->
                selections.forEach(selection -> applicationEventPublisher.publishEvent(new HandSelected(
                        gameId,
                        eraNumber,
                        selection.playerId(),
                        HandSelected.SelectionOrigin.PLAYER,
                        selection.dealtCards().stream()
                                .limit(sessionGameRules.cardsPerHand())
                                .toList()))));

        var declarationPhase = awaitDeclarationPhase(gameId, eraNumber);
        if (firstFaction == Faction.ACTIVISTS) {
            assertThat(declarationPhase.status())
                    .isEqualTo(io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus.OPEN);
            assertThat(awaitEraSagaState(gameId).status()).isEqualTo(EraSagaStatus.WAITING_DECLARATION);
            assertThat(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, roundNumber))
                    .isEmpty();
            declineDeclaration.handle(new DeclineDeclarationUseCase.Command(gameId, eraNumber, playerIds.getFirst()));
        } else {
            assertThat(declarationPhase.status())
                    .isEqualTo(
                            io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus.CLOSED);
        }

        var actionRound = awaitActionRound(gameId, eraNumber, roundNumber);
        assertThat(actionRound.gameId()).isEqualTo(gameId);
        assertThat(actionRound.eraNumber()).isEqualTo(eraNumber);
        assertThat(actionRound.roundNumber()).isEqualTo(roundNumber);
        assertThat(actionRound.status()).isEqualTo(RoundStatus.OPEN);
        assertThat(actionRound.pendingPlayerIds()).containsExactlyInAnyOrderElementsOf(playerIds);
        assertThat(actionRound.timerSeconds()).isEqualTo(gameRules.actionRoundTimerSeconds(playerIds.size()));

        var sagaState = awaitSagaState(gameId, eraNumber, roundNumber);
        assertThat(sagaState.status()).isEqualTo(ActionRoundSagaStatus.WAITING);
        assertThat(sagaState.pendingPlayerIds()).containsExactlyInAnyOrderElementsOf(playerIds);
        assertThat(sagaState.timerExpiresAt()).isNotNull();

        assertAvailabilityReportedOncePerPlayer(gameId, eraNumber, roundNumber, playerIds);
    }

    private void assertAvailabilityReportedOncePerPlayer(
            UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {
        // One private availability fact per player, committed with the round open.
        assertThat(availabilityFacts(gameId)).isEqualTo(playerIds.size());

        // A repeated open of the same round is rejected as a whole and leaves each player with a single fact.
        transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(
                new StartActionRoundRequested(gameId, eraNumber, roundNumber, playerIds, playerIds)));
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(availabilityFacts(gameId)).isEqualTo(playerIds.size()));
    }

    private Integer availabilityFacts(UUID gameId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ? AND serialized_event LIKE ?",
                Integer.class,
                "%SpecialActionAvailabilityReported%",
                "%" + gameId + "%");
    }

    /**
     * Regression coverage for the cross-module pending-deal handoff: each player receives seven private cards, while
     * the action hand remains empty until a terminal {@code HandSelected} fact is published.
     */
    @Test
    void eraStarted_createsSevenCardPendingSelectionsWithoutProjectingPlayableHands() {
        var gameId = UUID.randomUUID();
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(_ -> {
            gameRepository.save(new Game(gameId, UUID.randomUUID(), futureEventCatalog.allEventIds()));
            applicationEventPublisher.publishEvent(new EraStarted(gameId, 1, List.of(), playerIds));
        });

        for (var playerId : playerIds) {
            var selection = awaitHandSelection(gameId, 1, playerId);
            assertThat(selection.status()).isEqualTo(HandSelectionStatus.OPEN);
            assertThat(selection.dealtCards()).hasSize(sessionGameRules.cardsPerDeal());
            assertThat(selection.selectedCards()).isEmpty();
            assertThat(playerStateRepository.findByGameIdAndPlayerId(gameId, playerId))
                    .isEmpty();
        }
    }

    private HandSelection awaitHandSelection(UUID gameId, int eraNumber, UUID playerId) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> transactionTemplate.execute(
                                _ -> handSelectionRepository.findByGameIdAndEraNumberAndPlayerIdWithLock(
                                        gameId, eraNumber, playerId)),
                        Optional::isPresent)
                .orElseThrow();
    }

    private EraSagaState awaitEraSagaState(UUID gameId) {
        return await().atMost(Duration.ofSeconds(10))
                .until(() -> eraSagaRepository.findByGameId(gameId), Optional::isPresent)
                .orElseThrow();
    }

    private ActionRound awaitActionRound(UUID gameId, int eraNumber, int roundNumber) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(
                                gameId, eraNumber, roundNumber),
                        Optional::isPresent)
                .orElseThrow();
    }

    private ActionRoundSagaState awaitSagaState(UUID gameId, int eraNumber, int roundNumber) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(
                                gameId, eraNumber, roundNumber),
                        Optional::isPresent)
                .orElseThrow();
    }

    private io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase awaitDeclarationPhase(
            UUID gameId, int eraNumber) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> transactionTemplate.execute(
                                _ -> declarationPhaseRepository.findByGameIdAndEraNumber(gameId, eraNumber)),
                        Optional::isPresent)
                .orElseThrow();
    }
}
