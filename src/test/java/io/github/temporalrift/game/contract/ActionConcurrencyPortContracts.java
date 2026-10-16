package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

/** What the action ports promise about locks, due-time sweeps and unique keys, on top of their round trips. */
public final class ActionConcurrencyPortContracts {

    private ActionConcurrencyPortContracts() {}

    public interface HandSelectionConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void handSelection_lockMakesASecondTransactionWait() {
            var repository = port(HandSelectionRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var selection = selection(gameId, playerId, Instant.parse("2026-08-14T00:01:00Z"));
            assertThat(inNewTransaction(() -> repository.createIfAbsent(selection)))
                    .isTrue();

            assertSecondLockerWaitsForFirst(
                    () -> repository.findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 1, playerId));
            assertSecondLockerWaitsForFirst(() -> repository.findByIdWithLock(selection.id()));
        }

        @Test
        default void handSelection_openDueQueryIncludesTheBoundaryAndExcludesFutureSelections() {
            var repository = port(HandSelectionRepository.class);
            var now = Instant.parse("2026-08-14T00:01:00Z");
            var past = selection(UUID.randomUUID(), UUID.randomUUID(), now.minusMillis(1));
            var exact = selection(UUID.randomUUID(), UUID.randomUUID(), now);
            var future = selection(UUID.randomUUID(), UUID.randomUUID(), now.plusMillis(1));
            List.of(past, exact, future).forEach(repository::createIfAbsent);

            assertThat(repository.findOpenDueIds(now))
                    .contains(past.id(), exact.id())
                    .doesNotContain(future.id());
        }

        private static HandSelection selection(UUID gameId, UUID playerId, Instant expiresAt) {
            var cards = IntStream.rangeClosed(1, 7)
                    .mapToObj(slot -> new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.I, slot))
                    .toList();
            return HandSelection.open(new HandDealt(gameId, 1, playerId, expiresAt, cards));
        }
    }

    public interface DeclarationPhaseConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void declarationPhase_lockMakesASecondTransactionWait() {
            var repository = port(DeclarationPhaseRepository.class);
            var gameId = UUID.randomUUID();
            var phase = new DeclarationPhase(UUID.randomUUID(), gameId, 2, Instant.parse("2099-01-01T00:00:00Z"));
            assertThat(inNewTransaction(() -> repository.createIfAbsent(phase))).isTrue();

            assertSecondLockerWaitsForFirst(() -> repository.findByGameIdAndEraNumberWithLock(gameId, 2));
            assertSecondLockerWaitsForFirst(() -> repository.findByIdWithLock(phase.id()));
        }

        @Test
        default void declarationPhase_openDueQueryIncludesTheBoundaryAndExcludesFuturePhases() {
            var repository = port(DeclarationPhaseRepository.class);
            var now = Instant.parse("2026-08-14T00:01:00Z");
            var past = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, now.minusMillis(1));
            var exact = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, now);
            var future = new DeclarationPhase(UUID.randomUUID(), UUID.randomUUID(), 1, now.plusMillis(1));
            List.of(past, exact, future).forEach(repository::createIfAbsent);

            assertThat(repository.findOpenDueIds(now))
                    .contains(past.id(), exact.id())
                    .doesNotContain(future.id());
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void declarationPhase_concurrentCreateWaitsForTheFirstAndThenReportsDuplicate() throws Exception {
            var repository = port(DeclarationPhaseRepository.class);
            var gameId = UUID.randomUUID();
            var expiresAt = Instant.parse("2099-01-01T00:00:00Z");
            var firstCreated = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var first = CompletableFuture.supplyAsync(() -> inNewTransaction(() -> {
                var created = repository.createIfAbsent(new DeclarationPhase(UUID.randomUUID(), gameId, 1, expiresAt));
                firstCreated.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return created;
            }));
            assertThat(firstCreated.await(10, TimeUnit.SECONDS)).isTrue();
            var second = CompletableFuture.supplyAsync(() -> inNewTransaction(
                    () -> repository.createIfAbsent(new DeclarationPhase(UUID.randomUUID(), gameId, 1, expiresAt))));

            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        }
    }

    public interface ParadoxResolutionPhaseConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void paradoxResolutionPhase_lockMakesASecondTransactionWait() {
            var repository = port(ParadoxResolutionPhaseRepository.class);
            var gameId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(
                    new ParadoxResolutionPhase(UUID.randomUUID(), gameId, 2, Instant.parse("2099-01-01T00:00:00Z"))));

            assertSecondLockerWaitsForFirst(() -> repository.findByGameIdAndEraNumberWithLock(gameId, 2));
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void paradoxResolutionPhase_rosterLockIsPerGameAndEra() throws Exception {
            var repository = port(ParadoxResolutionPhaseRepository.class);
            var gameId = UUID.randomUUID();
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var holder = CompletableFuture.runAsync(() -> inNewTransaction(() -> {
                repository.lockParticipantRoster(gameId, 2);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                CompletableFuture.runAsync(() -> inNewTransaction(() -> repository.lockParticipantRoster(gameId, 3)))
                        .get(5, TimeUnit.SECONDS);
                CompletableFuture.runAsync(
                                () -> inNewTransaction(() -> repository.lockParticipantRoster(UUID.randomUUID(), 2)))
                        .get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
            }
            assertSecondLockerWaitsForFirst(() -> repository.lockParticipantRoster(gameId, 2));
        }
    }

    public interface PlayerStateConcurrencyPortContract extends PortContract {

        @Test
        default void playerState_findOrCreateWithLockCreatesOnceAndReturnsTheSameRow() {
            var repository = port(PlayerStateRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();

            var created = repository.findOrCreateWithLock(gameId, playerId);
            var again = repository.findOrCreateWithLock(gameId, playerId);

            assertThat(created.hand()).isEmpty();
            assertThat(again.id()).isEqualTo(created.id());
            assertThat(repository.findAllByGameId(gameId)).hasSize(1);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void playerState_locksMakeASecondTransactionWait() {
            var repository = port(PlayerStateRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new PlayerState(UUID.randomUUID(), gameId, playerId)));

            assertSecondLockerWaitsForFirst(() -> repository.findByGameIdAndPlayerIdWithLock(gameId, playerId));
            assertSecondLockerWaitsForFirst(() -> repository.lockAllByGameId(gameId));
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void playerState_secondRowForTheSamePlayerIsRejected() {
            var repository = port(PlayerStateRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new PlayerState(UUID.randomUUID(), gameId, playerId)));

            assertThatThrownBy(() -> inNewTransaction(
                            () -> repository.save(new PlayerState(UUID.randomUUID(), gameId, playerId))))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    public interface ReactiveOfferConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void reactiveOffer_locksMakeASecondTransactionWait() {
            var repository = port(ReactiveOfferRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var offer = new ReactiveOffer(UUID.randomUUID(), gameId, 2, playerId, UUID.randomUUID(), UUID.randomUUID());
            assertThat(inNewTransaction(() -> repository.createIfAbsent(offer))).isTrue();

            assertSecondLockerWaitsForFirst(
                    () -> repository.findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 2, playerId));
            assertSecondLockerWaitsForFirst(() -> repository.findAllByGameIdAndEraNumberWithLock(gameId, 2));
        }
    }

    public interface ActionRoundConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void actionRound_lockMakesASecondTransactionWait() {
            var repository = port(ActionRoundRepository.class);
            var gameId = UUID.randomUUID();
            var round = new ActionRound(
                    UUID.randomUUID(), new ActionRoundConfig(gameId, 2, 3, 45), List.of(UUID.randomUUID()));
            inNewTransaction(() -> repository.save(round));

            assertSecondLockerWaitsForFirst(
                    () -> repository.findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, 2, 3));
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void actionRound_secondRoundWithTheSameCoordinatesIsRejected() {
            var repository = port(ActionRoundRepository.class);
            var gameId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(new ActionRound(
                    UUID.randomUUID(), new ActionRoundConfig(gameId, 2, 3, 45), List.of(UUID.randomUUID()))));

            assertThatThrownBy(() -> inNewTransaction(() -> repository.save(new ActionRound(
                            UUID.randomUUID(), new ActionRoundConfig(gameId, 2, 3, 45), List.of(UUID.randomUUID())))))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    public interface ActionRoundSagaConcurrencyPortContract extends PortContract {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void actionRoundSaga_lockMakesASecondTransactionWait() {
            var repository = port(ActionRoundSagaRepository.class);
            var gameId = UUID.randomUUID();
            inNewTransaction(() -> repository.save(saga(gameId, Instant.parse("2099-01-01T00:00:30Z"))));

            assertSecondLockerWaitsForFirst(
                    () -> repository.findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, 1, 1));
        }

        @Test
        default void actionRoundSaga_waitingDueQueryIncludesTheBoundaryAndExcludesFutureTimers() {
            var repository = port(ActionRoundSagaRepository.class);
            var deadline = Instant.parse("2026-08-14T00:01:00Z");
            var past = repository.save(saga(UUID.randomUUID(), deadline.minusMillis(1)));
            var exact = repository.save(saga(UUID.randomUUID(), deadline));
            var future = repository.save(saga(UUID.randomUUID(), deadline.plusMillis(1)));

            var due = repository.findWaitingDueBy(deadline);

            assertThat(due).contains(past, exact).doesNotContain(future);
        }

        private static ActionRoundSagaState saga(UUID gameId, Instant timerExpiresAt) {
            return new ActionRoundSagaState(
                    UUID.randomUUID(),
                    gameId,
                    1,
                    1,
                    ActionRoundSagaStatus.WAITING,
                    List.of(UUID.randomUUID()),
                    timerExpiresAt);
        }
    }
}
