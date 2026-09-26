package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@ExtendWith(MockitoExtension.class)
@DisplayName("StalledEventTargetLock")
class StalledEventTargetLockTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 1;

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    ActionRound round1;

    @Mock
    ActionRound round2;

    @InjectMocks
    StalledEventTargetLock lock;

    @Test
    @DisplayName("round 1 has no prior rounds so nothing is stalled")
    void round1HasNoStalledEvents() {
        assertThat(lock.stalledEventIds(GAME_ID, ERA, 1)).isEmpty();
    }

    @Test
    @DisplayName("round 1 Stall locks the event for round 2")
    void round1StallLocksEventForRound2() {
        var eventId = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), eventId)));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).containsExactly(eventId);
    }

    @Test
    @DisplayName("same-round Stall does not lock its own round")
    void sameRoundStallDoesNotLockOwnRound() {
        // The resolver only reads strictly prior rounds, so a Stall submitted in round 2
        // never blocks other round 2 submissions on the same event (simultaneous resolution).
        var eventId = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.empty());

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).isEmpty();
    }

    @Test
    @DisplayName("Nullified Stall does not lock the event")
    void nullifiedStallDoesNotLock() {
        var eventId = UUID.randomUUID();
        var staller = UUID.randomUUID();
        var nullifier = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions())
                .willReturn(List.of(
                        stallAction(staller, eventId), nullifyAction(nullifier, CardGrade.I, List.of(staller))));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).isEmpty();
    }

    @Test
    @DisplayName("grade II Nullify cancels both targets so neither Stall locks")
    void gradeIiNullifyCancelsBothStalls() {
        var eventA = UUID.randomUUID();
        var eventB = UUID.randomUUID();
        var stallerA = UUID.randomUUID();
        var stallerB = UUID.randomUUID();
        var nullifier = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions())
                .willReturn(List.of(
                        stallAction(stallerA, eventA),
                        stallAction(stallerB, eventB),
                        nullifyAction(nullifier, CardGrade.II, List.of(stallerA, stallerB))));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).isEmpty();
    }

    @Test
    @DisplayName("a Nullify still cancels even when it was itself nullified")
    void cancelledNullifyStillCancelsItsTarget() {
        // Every NULLIFY contributes simultaneously, even one that was itself nullified:
        // the Nullify naming the staller still cancels the Stall.
        var eventId = UUID.randomUUID();
        var staller = UUID.randomUUID();
        var nullifierA = UUID.randomUUID();
        var nullifierB = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions())
                .willReturn(List.of(
                        stallAction(staller, eventId),
                        nullifyAction(nullifierA, CardGrade.I, List.of(staller)),
                        nullifyAction(nullifierB, CardGrade.I, List.of(nullifierA))));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).isEmpty();
    }

    @Test
    @DisplayName("mutually-targeting Nullifies cancel each other, leaving the Stall live")
    void mutualNullifyLeavesUnrelatedStallLive() {
        // A nullifies B and B nullifies A simultaneously: both Nullifies are cancelled,
        // but neither named the staller, so the Stall still locks the event.
        var eventId = UUID.randomUUID();
        var staller = UUID.randomUUID();
        var nullifierA = UUID.randomUUID();
        var nullifierB = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions())
                .willReturn(List.of(
                        stallAction(staller, eventId),
                        nullifyAction(nullifierA, CardGrade.I, List.of(nullifierB)),
                        nullifyAction(nullifierB, CardGrade.I, List.of(nullifierA))));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).containsExactly(eventId);
    }

    @Test
    @DisplayName("round 3 sees Stalls from rounds 1 and 2")
    void round3SeesBothPriorRounds() {
        var eventA = UUID.randomUUID();
        var eventB = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 2))
                .willReturn(Optional.of(round2));
        given(round1.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), eventA)));
        given(round2.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), eventB)));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 3)).containsExactlyInAnyOrder(eventA, eventB);
    }

    @ParameterizedTest
    @EnumSource(
            value = CardType.class,
            names = {"PUSH", "SUPPRESS", "SWING", "COLLIDE", "STALL", "TRACE"})
    @DisplayName("every scalar event-targeting card is rejected on a stalled event")
    void everyEventTargetingCardIsRejected(CardType cardType) {
        var eventId = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), eventId)));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> lock.requireEventNotStalled(GAME_ID, ERA, 2, eventId))
                .withMessageContaining(eventId.toString());
    }

    @ParameterizedTest
    @EnumSource(
            value = SpecialAction.class,
            names = {
                "ANNIHILATE",
                "SEAL",
                "FORESIGHT",
                "REWRITE",
                "MIMIC",
                "CASCADE",
                "THREAD",
                "REWEAVE",
                "FULFILLMENT"
            })
    @DisplayName("every event-targeting special is rejected on a stalled event")
    void everyEventTargetingSpecialIsRejected(SpecialAction specialAction) {
        var eventId = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), eventId)));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> lock.requireEventNotStalled(GAME_ID, ERA, 2, eventId))
                .withMessageContaining(eventId.toString());
    }

    @Test
    @DisplayName("null target and round 1 never throw")
    void nullTargetAndRound1NeverThrow() {
        lock.requireEventNotStalled(GAME_ID, ERA, 2, null);
        lock.requireEventNotStalled(GAME_ID, ERA, 1, UUID.randomUUID());
    }

    private static SubmittedAction.CardAction stallAction(UUID playerId, UUID eventId) {
        return new SubmittedAction.CardAction(
                playerId, UUID.randomUUID(), CardType.STALL, CardGrade.I, eventId, null, null, null, null, null, null);
    }

    private static SubmittedAction.CardAction nullifyAction(UUID playerId, CardGrade grade, List<UUID> targets) {
        return new SubmittedAction.CardAction(
                playerId, UUID.randomUUID(), CardType.NULLIFY, grade, null, null, null, null, null, targets, null);
    }
}
