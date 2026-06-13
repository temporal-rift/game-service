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
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.shared.GameRulesPort;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EraStartedToRoundStartIT {

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    ActionRoundRepository actionRoundRepository;

    @Autowired
    ActionRoundSagaRepository actionRoundSagaRepository;

    @Autowired
    FutureEventCatalogPort futureEventCatalog;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    GameRulesPort gameRules;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void eraStarted_triggersRound1Start_andPersistsActionRound() {
        var gameId = UUID.randomUUID();
        var eraNumber = 1;
        var roundNumber = 1;
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(_ -> {
            gameRepository.save(new Game(gameId, UUID.randomUUID(), futureEventCatalog.allEventIds()));
            applicationEventPublisher.publishEvent(new EraStarted(gameId, eraNumber, List.of(), playerIds));
        });

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
