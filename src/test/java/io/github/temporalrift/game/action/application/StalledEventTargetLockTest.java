package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

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

    @Test
    @DisplayName("stalled event is rejected; anything else passes")
    void stalledEventIsRejected() {
        var stalledEventId = UUID.randomUUID();
        var liveEventId = UUID.randomUUID();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions()).willReturn(List.of(stallAction(UUID.randomUUID(), stalledEventId)));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> lock.requireEventNotStalled(GAME_ID, ERA, 2, stalledEventId))
                .withMessageContaining(stalledEventId.toString());
        assertThatCode(() -> lock.requireEventNotStalled(GAME_ID, ERA, 2, liveEventId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("legacy scalar-target NULLIFY still cancels the Stall")
    void legacyScalarNullifyStillCancelsStall() {
        // Rows stored before the player-target list existed carry a scalar NULLIFY target;
        // the shared cancellation rule honors them, so the Stall does not lock the event.
        var eventId = UUID.randomUUID();
        var staller = UUID.randomUUID();
        var nullifier = UUID.randomUUID();
        var legacyNullify = new SubmittedAction.CardAction(
                nullifier,
                UUID.randomUUID(),
                CardType.NULLIFY,
                CardGrade.I,
                null,
                null,
                null,
                null,
                staller,
                null,
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round1));
        given(round1.submittedActions()).willReturn(List.of(stallAction(staller, eventId), legacyNullify));

        assertThat(lock.stalledEventIds(GAME_ID, ERA, 2)).isEmpty();
    }

    @Test
    @DisplayName("null target and round 1 never throw")
    void nullTargetAndRound1NeverThrow() {
        assertThatCode(() -> lock.requireEventNotStalled(GAME_ID, ERA, 2, null)).doesNotThrowAnyException();
        assertThatCode(() -> lock.requireEventNotStalled(GAME_ID, ERA, 1, UUID.randomUUID()))
                .doesNotThrowAnyException();
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
