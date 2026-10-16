package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.simulation.domain.execution.ClockAcknowledgement;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;
import io.github.temporalrift.game.simulation.domain.execution.ClockOperation;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.OutboxProbe;

/** The simulation module's persisted driven ports and the modules' execution probes. */
public final class SimulationPortContracts {

    private SimulationPortContracts() {}

    public interface ExecutionPortContract extends PortContract {

        @Test
        default void execution_insertIfAbsentStoresTheFirstExecutionOnly() {
            var repository = port(ExecutionRepository.class);
            var first = Execution.configure(ExecutionContextTestData.context("42"));
            var second = Execution.configure(ExecutionContextTestData.context("43"));

            assertThat(repository.find()).isEmpty();
            assertThat(repository.insertIfAbsent(first)).isTrue();
            assertThat(repository.insertIfAbsent(second)).isFalse();

            assertThat(repository.find()).contains(first);
            assertThat(repository.findWithLock()).contains(first);
        }

        @Test
        default void execution_updateChangesTheRevisionAndLogicalTimeButNotTheContext() {
            var repository = port(ExecutionRepository.class);
            var configured = Execution.configure(ExecutionContextTestData.context("42"));
            repository.insertIfAbsent(configured);
            var advanced = new Execution(
                    configured.context(),
                    configured.revision() + 1,
                    configured.logicalTime().plusSeconds(90));

            repository.update(advanced);

            assertThat(repository.find()).contains(advanced);
            assertThat(repository.find().orElseThrow().context()).isEqualTo(configured.context());
        }
    }

    public interface EntropyDecisionPortContract extends PortContract {

        @Test
        default void entropyDecision_realizedWordIsReadBackAndTheFirstWriteWins() {
            var repository = port(EntropyDecisionRepository.class);
            var coordinate = "era=" + UUID.randomUUID();

            assertThat(repository.find("CARD_DEAL", coordinate, 0)).isEmpty();
            repository.save("CARD_DEAL", coordinate, 0, 7L);
            repository.save("CARD_DEAL", coordinate, 0, 9L);

            assertThat(repository.find("CARD_DEAL", coordinate, 0)).contains(7L);
            assertThat(repository.find("CARD_DEAL", coordinate, 1)).isEmpty();
            assertThat(repository.find("OTHER_PURPOSE", coordinate, 0)).isEmpty();
        }
    }

    public interface ClockOperationPortContract extends PortContract {

        @Test
        default void clockOperation_savedOperationIsReadBackWithItsAcknowledgement() {
            var repository = port(ClockOperationRepository.class);
            var operationId = UUID.randomUUID();
            var at = Instant.parse("2026-03-01T10:00:00Z");
            var operation = new ClockOperation(
                    new ClockAdvance(operationId, 0, at), new ClockAcknowledgement(operationId, 1, at));

            assertThat(repository.find(operationId)).isEmpty();
            repository.save(operation);

            assertThat(repository.find(operationId)).contains(operation);
        }

        @Test
        default void clockOperation_recordingTheSameOperationTwiceIsRejected() {
            var repository = port(ClockOperationRepository.class);
            var operationId = UUID.randomUUID();
            var at = Instant.parse("2026-03-01T10:00:00Z");
            var operation = new ClockOperation(
                    new ClockAdvance(operationId, 0, at), new ClockAcknowledgement(operationId, 1, at));
            repository.save(operation);

            assertThatThrownBy(() -> repository.save(operation)).isInstanceOf(RuntimeException.class);
        }
    }

    public interface OutboxProbePortContract extends PortContract {

        @Test
        default void outboxProbe_reportsANonNegativeNumberOfPendingPublications() {
            assertThat(port(OutboxProbe.class).pendingPublications()).isNotNegative();
        }
    }

    public interface ExecutionProbePortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void executionProbes_countActionTimersAsDueOrNext() {
            var sagas = port(ActionRoundSagaRepository.class);
            var now = Instant.parse("2026-08-14T00:01:00Z");
            var before = observe(now);

            inNewTransaction(() -> {
                sagas.save(actionSaga(now.minusSeconds(1)));
                sagas.save(actionSaga(now.plusSeconds(60)));
            });
            var after = observe(now);

            assertThat(after.dueTimers()).isEqualTo(before.dueTimers() + 1);
            assertThat(after.nextDeadline()).isNotNull().isBeforeOrEqualTo(now.plusSeconds(60));
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void executionProbes_countReconnectGracePeriodsAsDue() {
            var sagas = port(PlayerReconnectSagaRepository.class);
            var now = Instant.parse("2026-08-14T00:01:00Z");
            var before = observe(now);

            inNewTransaction(() -> {
                sagas.save(new PlayerReconnectSagaState(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        PlayerReconnectSagaStatus.GRACE_PERIOD,
                        now.minusSeconds(1)));
                sagas.save(new PlayerReconnectSagaState(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        PlayerReconnectSagaStatus.RECONNECTED,
                        now.minusSeconds(1)));
            });

            assertThat(observe(now).dueTimers()).isEqualTo(before.dueTimers() + 1);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void executionProbes_countResolvedErasWithoutACompletionAsContinuations() {
            var contexts = port(EraScoringContextRepository.class);
            var completions = port(ScoringEraCompletionRepository.class);
            var gameId = UUID.randomUUID();
            var before = observe(Instant.parse("2026-08-14T00:01:00Z"));

            inNewTransaction(
                    () -> contexts.saveEraResolutionCompleted(new EraResolutionCompleted(gameId, 1, List.of())));
            var pending = observe(Instant.parse("2026-08-14T00:01:00Z"));
            inNewTransaction(() -> completions.tryMarkScoringComplete(gameId, 1));
            var done = observe(Instant.parse("2026-08-14T00:01:00Z"));

            assertThat(pending.continuations()).isEqualTo(before.continuations() + 1);
            assertThat(done.continuations()).isEqualTo(before.continuations());
        }

        private ExecutionProbe.Observation observe(Instant now) {
            var observations = context().getBeansOfType(ExecutionProbe.class).values().stream()
                    .map(probe -> probe.observe(now))
                    .toList();
            var next = observations.stream()
                    .map(ExecutionProbe.Observation::nextDeadline)
                    .filter(Objects::nonNull)
                    .min(java.util.Comparator.naturalOrder())
                    .orElse(null);
            return new ExecutionProbe.Observation(
                    null,
                    false,
                    observations.stream()
                            .mapToInt(ExecutionProbe.Observation::dueTimers)
                            .sum(),
                    observations.stream()
                            .mapToInt(ExecutionProbe.Observation::continuations)
                            .sum(),
                    next);
        }

        private static ActionRoundSagaState actionSaga(Instant timerExpiresAt) {
            return new ActionRoundSagaState(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    1,
                    ActionRoundSagaStatus.WAITING,
                    List.of(UUID.randomUUID()),
                    timerExpiresAt);
        }
    }
}
