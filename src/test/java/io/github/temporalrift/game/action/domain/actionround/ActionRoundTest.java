package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

@DisplayName("ActionRound")
class ActionRoundTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 1;
    static final int ROUND = 1;
    static final int TIMER_SECONDS = 60;
    static final UUID PLAYER_A = UUID.randomUUID();
    static final UUID PLAYER_B = UUID.randomUUID();

    ActionRound openRound(List<UUID> players) {
        return new ActionRound(UUID.randomUUID(), GAME_ID, ERA, ROUND, players, TIMER_SECONDS);
    }

    @Test
    @DisplayName("constructor registers ActionRoundStarted with correct fields")
    void constructorRegistersActionRoundStarted() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));

        // when
        var events = round.pullEvents();

        // then
        assertThat(events).singleElement().isInstanceOf(ActionRoundStarted.class);
        var e = (ActionRoundStarted) events.getFirst();
        assertThat(e.gameId()).isEqualTo(GAME_ID);
        assertThat(e.timerSeconds()).isEqualTo(TIMER_SECONDS);
        assertThat(e.pendingPlayerIds()).containsExactly(PLAYER_A, PLAYER_B);
    }

    @Test
    @DisplayName("reconstitute does not register any events")
    void reconstituteRegistersNoEvents() {
        // when
        var round = ActionRound.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                ERA,
                ROUND,
                RoundStatus.OPEN,
                TIMER_SECONDS,
                null,
                List.of(PLAYER_A),
                List.of());

        // then
        assertThat(round.pullEvents()).isEmpty();
        assertThat(round.timerSeconds()).isEqualTo(TIMER_SECONDS);
        assertThat(round.closedReason()).isNull();
    }

    @Test
    @DisplayName(
            "submitCard — removes player from pending, registers CardPlayed, returns false when others still pending")
    void submitCardRemovesPlayerAndRegistersEvent() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();

        // when
        var allSubmitted = round.submitCard(
                PLAYER_A, cardId, CardType.PUSH, UUID.randomUUID(), null, UUID.randomUUID(), List.of(cardId));

        // then
        assertThat(allSubmitted).isFalse();
        assertThat(round.pendingPlayerIds()).doesNotContain(PLAYER_A).contains(PLAYER_B);
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOf(CardPlayed.class);
        assertThat(((CardPlayed) events.getFirst()).playerId()).isEqualTo(PLAYER_A);
        assertThat(((CardPlayed) events.getFirst()).cardType()).isEqualTo(CardType.PUSH);
        assertThat(((CardPlayed) events.getFirst()).sourceOutcomeId()).isNull();
    }

    @Test
    @DisplayName("submitCard — Swing preserves source and destination outcomes")
    void submitCardSwingPreservesSourceAndDestinationOutcomes() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();

        // when
        round.submitCard(PLAYER_A, cardId, CardType.SWING, eventId, sourceOutcomeId, targetOutcomeId, List.of(cardId));

        // then
        assertThat(round.submittedActions())
                .singleElement()
                .isInstanceOfSatisfying(SubmittedAction.CardAction.class, card -> {
                    assertThat(card.sourceOutcomeId()).isEqualTo(sourceOutcomeId);
                    assertThat(card.targetOutcomeId()).isEqualTo(targetOutcomeId);
                });
        assertThat(round.pullEvents()).singleElement().isInstanceOfSatisfying(CardPlayed.class, cardPlayed -> {
            assertThat(cardPlayed.targetEventId()).isEqualTo(eventId);
            assertThat(cardPlayed.sourceOutcomeId()).isEqualTo(sourceOutcomeId);
            assertThat(cardPlayed.targetOutcomeId()).isEqualTo(targetOutcomeId);
        });
    }

    @Test
    @DisplayName("submitCard — Swing without source outcome — throws InvalidActionTargetException")
    void submitCardSwingWithoutSourceOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var playerHand = List.of(cardId);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> round.submitCard(
                        PLAYER_A, cardId, CardType.SWING, targetEventId, null, targetOutcomeId, playerHand));
    }

    @Test
    @DisplayName("submitCard — Swing without target outcome — throws InvalidActionTargetException")
    void submitCardSwingWithoutTargetOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var playerHand = List.of(cardId);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> round.submitCard(
                        PLAYER_A, cardId, CardType.SWING, targetEventId, sourceOutcomeId, null, playerHand));
    }

    @Test
    @DisplayName("submitCard — Swing with same source and target outcome — throws InvalidActionTargetException")
    void submitCardSwingWithSameSourceAndTargetOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var playerHand = List.of(cardId);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> round.submitCard(
                        PLAYER_A, cardId, CardType.SWING, targetEventId, outcomeId, outcomeId, playerHand));
    }

    @Test
    @DisplayName("submitCard — last player submits — returns true")
    void submitCardLastPlayerReturnsTrue() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var cardId = UUID.randomUUID();

        // when
        var allSubmitted = round.submitCard(PLAYER_A, cardId, CardType.SUPPRESS, null, null, null, List.of(cardId));

        // then
        assertThat(allSubmitted).isTrue();
    }

    @Test
    @DisplayName("submitCard — round is CLOSED — throws ActionRoundClosedException")
    void submitCardOnClosedRoundThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.close("ALL_SUBMITTED");
        var cardId = UUID.randomUUID();
        var playerHand = List.of(cardId);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, null, playerHand));
    }

    @Test
    @DisplayName("submitCard — round is CLOSING — throws ActionRoundClosedException")
    void submitCardOnClosingRoundThrows() {
        // given
        var round = ActionRound.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                ERA,
                ROUND,
                RoundStatus.CLOSING,
                TIMER_SECONDS,
                "TIMER_EXPIRED",
                List.of(PLAYER_A),
                List.of());
        var cardId = UUID.randomUUID();
        var playerHand = List.of(cardId);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, null, playerHand));
    }

    @Test
    @DisplayName("submitCard — player already submitted — throws DuplicateSubmissionException")
    void submitCardDuplicateSubmissionThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var cardId2 = UUID.randomUUID();
        var initialHand = List.of(cardId, cardId2);
        var remainingHand = List.of(cardId2);
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, null, initialHand);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class)
                .isThrownBy(
                        () -> round.submitCard(PLAYER_A, cardId2, CardType.SUPPRESS, null, null, null, remainingHand));
    }

    @Test
    @DisplayName("submitCard — card not in hand — throws CardNotInHandException")
    void submitCardNotInHandThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var notInHand = UUID.randomUUID();
        var playerHand = List.of(UUID.randomUUID());

        // when / then
        assertThatExceptionOfType(CardNotInHandException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, notInHand, CardType.JAM, null, null, null, playerHand));
    }

    @Test
    @DisplayName("submitSpecial — jammed player — throws JammedPlayerException")
    void submitSpecialJammedPlayerThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();

        // when / then
        assertThatExceptionOfType(JammedPlayerException.class)
                .isThrownBy(() -> round.submitSpecial(
                        PLAYER_A, Faction.ERASERS, SpecialAction.ANNIHILATE, null, null, null, true));
    }

    @Test
    @DisplayName("submitSpecial — missing faction — throws FactionRequiredException")
    void submitSpecialMissingFactionThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();

        // when / then
        assertThatExceptionOfType(FactionRequiredException.class)
                .isThrownBy(
                        () -> round.submitSpecial(PLAYER_A, null, SpecialAction.ANNIHILATE, null, null, null, false));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submitSpecial — round is CLOSED — throws ActionRoundClosedException")
    void submitSpecialOnClosedRoundThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.close("ALL_SUBMITTED");

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class)
                .isThrownBy(() ->
                        round.submitSpecial(PLAYER_A, Faction.PROPHETS, SpecialAction.SEAL, null, null, null, false));
    }

    @Test
    @DisplayName("submitSpecial — player already submitted — throws DuplicateSubmissionException")
    void submitSpecialDuplicateSubmissionThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        round.submitSpecial(PLAYER_A, Faction.PROPHETS, SpecialAction.SEAL, null, null, null, false);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class)
                .isThrownBy(() -> round.submitSpecial(
                        PLAYER_A, Faction.PROPHETS, SpecialAction.REWRITE, null, null, null, false));
    }

    @Test
    @DisplayName("submitSpecial — happy path — registers SpecialActionPlayed, returns false when others pending")
    void submitSpecialHappyPath() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();

        // when
        var allSubmitted = round.submitSpecial(
                PLAYER_A, Faction.PROPHETS, SpecialAction.SEAL, UUID.randomUUID(), UUID.randomUUID(), null, false);

        // then
        assertThat(allSubmitted).isFalse();
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOf(SpecialActionPlayed.class);
        assertThat(((SpecialActionPlayed) events.getFirst()).playerId()).isEqualTo(PLAYER_A);
    }

    @Test
    @DisplayName(
            "close ALL_SUBMITTED — no pending players — registers ActionRoundClosed, returns Closed with empty skipped")
    void closeAllSubmittedRegistersClosedEvent() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, null, List.of(cardId));
        round.pullEvents();

        // when
        var outcome = round.close("ALL_SUBMITTED");

        // then
        assertThat(outcome).isInstanceOf(CloseOutcome.Closed.class);
        assertThat(((CloseOutcome.Closed) outcome).skippedPlayerIds()).isEmpty();
        assertThat(round.status()).isEqualTo(RoundStatus.CLOSED);
        assertThat(round.closedReason()).isEqualTo("ALL_SUBMITTED");
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOf(ActionRoundClosed.class);
        assertThat(((ActionRoundClosed) events.getFirst()).closedReason()).isEqualTo("ALL_SUBMITTED");
        assertThat(((ActionRoundClosed) events.getFirst()).totalActions()).isEqualTo(1);
    }

    @Test
    @DisplayName("close TIMER_EXPIRED — one player did not submit — registers PlayerSkipped then ActionRoundClosed")
    void closeTimerExpiredRegistersSkippedAndClosed() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, null, List.of(cardId));
        round.pullEvents();

        // when
        var outcome = round.close("TIMER_EXPIRED");

        // then
        assertThat(((CloseOutcome.Closed) outcome).skippedPlayerIds()).containsExactly(PLAYER_B);
        var events = round.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(PlayerSkipped.class);
        assertThat(((PlayerSkipped) events.get(0)).playerId()).isEqualTo(PLAYER_B);
        assertThat(((PlayerSkipped) events.get(0)).reason()).isEqualTo("TIMER_EXPIRED");
        assertThat(events.get(1)).isInstanceOf(ActionRoundClosed.class);
    }

    @Test
    @DisplayName("close with a non-timer reason — PlayerSkipped carries that reason, not a hardcoded one")
    void closeWithOtherReasonRegistersSkippedWithThatReason() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();

        // when
        round.close("HOST_ABORTED");

        // then
        var events = round.pullEvents();
        assertThat(events)
                .filteredOn(PlayerSkipped.class::isInstance)
                .hasSize(2)
                .allSatisfy(
                        event -> assertThat(((PlayerSkipped) event).reason()).isEqualTo("HOST_ABORTED"));
    }

    @Test
    @DisplayName("close — round already CLOSING — returns AlreadyClosing and registers no new events")
    void closeAlreadyClosingReturnsAlreadyClosing() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.close("TIMER_EXPIRED");
        round.pullEvents();

        // when
        var outcome = round.close("TIMER_EXPIRED");

        // then
        assertThat(outcome).isInstanceOf(CloseOutcome.AlreadyClosing.class);
        assertThat(round.pullEvents()).isEmpty();
    }
}
