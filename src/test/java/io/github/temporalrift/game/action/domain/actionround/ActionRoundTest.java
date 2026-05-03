package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.action.ActionRoundStarted;
import io.github.temporalrift.events.action.CardPlayed;
import io.github.temporalrift.events.action.PlayerSkipped;
import io.github.temporalrift.events.action.SpecialActionPlayed;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.domain.CardNotInHandException;

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
                UUID.randomUUID(), GAME_ID, ERA, ROUND, RoundStatus.OPEN, List.of(PLAYER_A), List.of());

        // then
        assertThat(round.pullEvents()).isEmpty();
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
                PLAYER_A, cardId, CardType.PUSH, UUID.randomUUID(), UUID.randomUUID(), List.of(cardId));

        // then
        assertThat(allSubmitted).isFalse();
        assertThat(round.pendingPlayerIds()).doesNotContain(PLAYER_A).contains(PLAYER_B);
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOf(CardPlayed.class);
        assertThat(((CardPlayed) events.getFirst()).playerId()).isEqualTo(PLAYER_A);
        assertThat(((CardPlayed) events.getFirst()).cardType()).isEqualTo(CardType.PUSH);
    }

    @Test
    @DisplayName("submitCard — last player submits — returns true")
    void submitCardLastPlayerReturnsTrue() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var cardId = UUID.randomUUID();

        // when
        var allSubmitted = round.submitCard(PLAYER_A, cardId, CardType.SUPPRESS, null, null, List.of(cardId));

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

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, List.of(cardId)));
    }

    @Test
    @DisplayName("submitCard — round is CLOSING — throws ActionRoundClosedException")
    void submitCardOnClosingRoundThrows() {
        // given
        var round = ActionRound.reconstitute(
                UUID.randomUUID(), GAME_ID, ERA, ROUND, RoundStatus.CLOSING, List.of(PLAYER_A), List.of());
        var cardId = UUID.randomUUID();

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, List.of(cardId)));
    }

    @Test
    @DisplayName("submitCard — player already submitted — throws DuplicateSubmissionException")
    void submitCardDuplicateSubmissionThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var cardId2 = UUID.randomUUID();
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, List.of(cardId, cardId2));

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class)
                .isThrownBy(() -> round.submitCard(PLAYER_A, cardId2, CardType.SUPPRESS, null, null, List.of(cardId2)));
    }

    @Test
    @DisplayName("submitCard — card not in hand — throws CardNotInHandException")
    void submitCardNotInHandThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var notInHand = UUID.randomUUID();

        // when / then
        assertThatExceptionOfType(CardNotInHandException.class)
                .isThrownBy(() ->
                        round.submitCard(PLAYER_A, notInHand, CardType.JAM, null, null, List.of(UUID.randomUUID())));
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
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, List.of(cardId));
        round.pullEvents();

        // when
        var outcome = round.close("ALL_SUBMITTED");

        // then
        assertThat(outcome).isInstanceOf(CloseOutcome.Closed.class);
        assertThat(((CloseOutcome.Closed) outcome).skippedPlayerIds()).isEmpty();
        assertThat(round.status()).isEqualTo(RoundStatus.CLOSED);
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
        round.submitCard(PLAYER_A, cardId, CardType.PUSH, null, null, List.of(cardId));
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
