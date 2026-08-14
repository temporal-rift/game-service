package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionStatus;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.GameRulesPort;
import io.github.temporalrift.game.shared.HandSelected;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
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
    GameRulesPort gameRules;

    @Autowired
    SessionGameRulesPort sessionGameRules;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    HandSelectionRepository handSelectionRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void eraStarted_waitsForEveryFinalHandBeforeRoundOneStarts() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;
        var roundNumber = 1;
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(_ -> {
            gameRepository.save(new Game(gameId, UUID.randomUUID(), futureEventCatalog.allEventIds()));
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
}
