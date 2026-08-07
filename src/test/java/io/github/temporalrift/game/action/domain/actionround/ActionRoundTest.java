package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;
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

    static SubmittedAction.CardAction card(UUID playerId, CardType cardType) {
        return new SubmittedAction.CardAction(playerId, UUID.randomUUID(), cardType, null, null, null);
    }

    static SubmittedAction.CardAction card(
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {
        return new SubmittedAction.CardAction(
                playerId, cardInstanceId, cardType, targetEventId, sourceOutcomeId, targetOutcomeId);
    }

    static SubmittedAction.SpecialActionSubmission special(
            UUID playerId,
            Faction faction,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {
        return new SubmittedAction.SpecialActionSubmission(
                playerId, faction, specialAction, targetEventId, targetOutcomeId, targetPlayerId);
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
    @DisplayName("constructor with initialSubmittedActions — removes those players from pending, exposes the "
            + "replayed action, and registers ActionRoundStarted then SpecialActionPlayed")
    void constructorWithInitialSubmittedActionsReplaysDeclarations() {
        // given
        var declaration =
                special(PLAYER_A, Faction.ACTIVISTS, SpecialAction.RALLY, UUID.randomUUID(), UUID.randomUUID(), null);

        // when
        var round = new ActionRound(
                UUID.randomUUID(),
                GAME_ID,
                ERA,
                ROUND,
                List.of(PLAYER_A, PLAYER_B),
                TIMER_SECONDS,
                List.of(declaration));

        // then
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_B);
        assertThat(round.submittedActions()).containsExactly(declaration);
        var events = round.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ActionRoundStarted.class);
        assertThat(events.get(1)).isInstanceOfSatisfying(SpecialActionPlayed.class, played -> {
            assertThat(played.playerId()).isEqualTo(PLAYER_A);
            assertThat(played.specialAction()).isEqualTo(SpecialAction.RALLY);
        });
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
                new ActionRound.PersistedState(RoundStatus.OPEN, TIMER_SECONDS, null, List.of(PLAYER_A), List.of()));

        // then
        assertThat(round.pullEvents()).isEmpty();
        assertThat(round.timerSeconds()).isEqualTo(TIMER_SECONDS);
        assertThat(round.closedReason()).isNull();
    }

    @Test
    @DisplayName("submit — CardAction — removes player from pending, registers CardPlayed, returns false when "
            + "others still pending")
    void submitCardRemovesPlayerAndRegistersEvent() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();

        // when
        var allSubmitted =
                round.submit(card(PLAYER_A, cardId, CardType.PUSH, UUID.randomUUID(), null, UUID.randomUUID()));

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
    @DisplayName("submit — CardAction Swing preserves source and destination outcomes")
    void submitCardSwingPreservesSourceAndDestinationOutcomes() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();

        // when
        round.submit(card(PLAYER_A, cardId, CardType.SWING, eventId, sourceOutcomeId, targetOutcomeId));

        // then
        assertThat(round.submittedActions())
                .singleElement()
                .isInstanceOfSatisfying(SubmittedAction.CardAction.class, c -> {
                    assertThat(c.sourceOutcomeId()).isEqualTo(sourceOutcomeId);
                    assertThat(c.targetOutcomeId()).isEqualTo(targetOutcomeId);
                });
        assertThat(round.pullEvents()).singleElement().isInstanceOfSatisfying(CardPlayed.class, cardPlayed -> {
            assertThat(cardPlayed.targetEventId()).isEqualTo(eventId);
            assertThat(cardPlayed.sourceOutcomeId()).isEqualTo(sourceOutcomeId);
            assertThat(cardPlayed.targetOutcomeId()).isEqualTo(targetOutcomeId);
        });
    }

    @Test
    @DisplayName("submit — CardAction Swing without source outcome — throws InvalidActionTargetException")
    void submitCardSwingWithoutSourceOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var action = card(PLAYER_A, cardId, CardType.SWING, targetEventId, null, targetOutcomeId);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — CardAction Swing without target outcome — throws InvalidActionTargetException")
    void submitCardSwingWithoutTargetOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var action = card(PLAYER_A, cardId, CardType.SWING, targetEventId, sourceOutcomeId, null);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — CardAction Swing with same source and target outcome — throws InvalidActionTargetException")
    void submitCardSwingWithSameSourceAndTargetOutcomeThrows() {
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var cardId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var action = card(PLAYER_A, cardId, CardType.SWING, targetEventId, outcomeId, outcomeId);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — CardAction — last player submits — returns true")
    void submitCardLastPlayerReturnsTrue() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();

        // when
        var allSubmitted = round.submit(card(PLAYER_A, CardType.SUPPRESS));

        // then
        assertThat(allSubmitted).isTrue();
    }

    @Test
    @DisplayName("submit — round is CLOSED — throws ActionRoundClosedException")
    void submitOnClosedRoundThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.close("ALL_SUBMITTED");
        var action = card(PLAYER_A, CardType.PUSH);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — round is CLOSING — throws ActionRoundClosedException")
    void submitOnClosingRoundThrows() {
        // given
        var round = ActionRound.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                ERA,
                ROUND,
                new ActionRound.PersistedState(
                        RoundStatus.CLOSING, TIMER_SECONDS, "TIMER_EXPIRED", List.of(PLAYER_A), List.of()));
        var action = card(PLAYER_A, CardType.PUSH);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — player already submitted — throws DuplicateSubmissionException")
    void submitDuplicateSubmissionThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        round.submit(card(PLAYER_A, CardType.PUSH));
        var action = card(PLAYER_A, CardType.SUPPRESS);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — player not pending in this round — throws DuplicateSubmissionException")
    void submitUnknownPlayerThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = card(PLAYER_B, CardType.PUSH);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> round.submit(action));
    }

    @Test
    @DisplayName("submit — SpecialActionSubmission — happy path — registers SpecialActionPlayed, returns false "
            + "when others pending")
    void submitSpecialHappyPath() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();

        // when
        var allSubmitted = round.submit(
                special(PLAYER_A, Faction.PROPHETS, SpecialAction.SEAL, UUID.randomUUID(), UUID.randomUUID(), null));

        // then
        assertThat(allSubmitted).isFalse();
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOf(SpecialActionPlayed.class);
        assertThat(((SpecialActionPlayed) events.getFirst()).playerId()).isEqualTo(PLAYER_A);
    }

    @Test
    @DisplayName("submit — RALLY — throws DeclarationSpecialActionRequiredException")
    void submitSpecialRallyThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.ACTIVISTS, SpecialAction.RALLY, null, null, null);

        // when / then
        assertThatExceptionOfType(DeclarationSpecialActionRequiredException.class)
                .isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — MOMENTUM — throws DeclarationSpecialActionRequiredException")
    void submitSpecialMomentumThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.ACTIVISTS, SpecialAction.MOMENTUM, null, null, null);

        // when / then
        assertThatExceptionOfType(DeclarationSpecialActionRequiredException.class)
                .isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — FORESIGHT without a target — throws InvalidActionTargetException")
    void submitSpecialForesightWithoutTargetThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.PROPHETS, SpecialAction.FORESIGHT, null, null, null);

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — REWRITE without a target — throws InvalidActionTargetException")
    void submitSpecialRewriteWithoutTargetThrows() {
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.REVISIONISTS, SpecialAction.REWRITE, null, null, null);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
    }

    @Test
    @DisplayName("submit — SEAL without a target — throws InvalidActionTargetException")
    void submitSpecialSealWithoutTargetThrows() {
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
    }

    @Test
    @DisplayName("submit — MIMIC with a target — registers private special action")
    void submitSpecialMimicWithTargetRegistersSpecialAction() {
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();

        round.submit(special(PLAYER_A, Faction.REVISIONISTS, SpecialAction.MIMIC, eventId, outcomeId, null));

        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOfSatisfying(SpecialActionPlayed.class, played -> {
            assertThat(played.specialAction()).isEqualTo(SpecialAction.MIMIC);
            assertThat(played.targetEventId()).isEqualTo(eventId);
            assertThat(played.targetOutcomeId()).isEqualTo(outcomeId);
        });
    }

    @Test
    @DisplayName("submit — ANNIHILATE without a targetOutcomeId — throws InvalidActionTargetException")
    void submitSpecialAnnihilateWithoutTargetOutcomeThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var targetEventId = UUID.randomUUID();
        var action = special(PLAYER_A, Faction.ERASERS, SpecialAction.ANNIHILATE, targetEventId, null, null);

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — FORESIGHT with a target — registers SpecialActionPlayed and ForesightDeclared")
    void submitSpecialForesightWithTargetRegistersForesightDeclared() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();

        // when
        round.submit(special(PLAYER_A, Faction.PROPHETS, SpecialAction.FORESIGHT, eventId, outcomeId, null));

        // then
        var events = round.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(SpecialActionPlayed.class);
        assertThat(events.get(1)).isInstanceOfSatisfying(ForesightDeclared.class, declared -> {
            assertThat(declared.gameId()).isEqualTo(GAME_ID);
            assertThat(declared.eraNumber()).isEqualTo(ERA);
            assertThat(declared.eventId()).isEqualTo(eventId);
            assertThat(declared.outcomeId()).isEqualTo(outcomeId);
            assertThat(declared.playerId()).isEqualTo(PLAYER_A);
        });
    }

    @Test
    @DisplayName("submit — ANNIHILATE with a target — registers SpecialActionPlayed and OutcomeAnnihilated")
    void submitSpecialAnnihilateWithTargetRegistersOutcomeAnnihilated() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();

        // when
        round.submit(special(PLAYER_A, Faction.ERASERS, SpecialAction.ANNIHILATE, eventId, outcomeId, null));

        // then
        var events = round.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(SpecialActionPlayed.class);
        assertThat(events.get(1)).isInstanceOfSatisfying(OutcomeAnnihilated.class, annihilated -> {
            assertThat(annihilated.gameId()).isEqualTo(GAME_ID);
            assertThat(annihilated.eraNumber()).isEqualTo(ERA);
            assertThat(annihilated.eventId()).isEqualTo(eventId);
            assertThat(annihilated.outcomeId()).isEqualTo(outcomeId);
            assertThat(annihilated.playerId()).isEqualTo(PLAYER_A);
        });
    }

    @Test
    @DisplayName("submit — FULFILLMENT without a targetEventId — throws InvalidActionTargetException")
    void submitSpecialFulfillmentWithoutTargetThrows() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.PROPHETS, SpecialAction.FULFILLMENT, null, null, null);

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — FULFILLMENT with a targetEventId — registers SpecialActionPlayed only")
    void submitSpecialFulfillmentWithTargetRegistersSpecialActionOnly() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        var eventId = UUID.randomUUID();

        // when
        round.submit(special(PLAYER_A, Faction.PROPHETS, SpecialAction.FULFILLMENT, eventId, null, null));

        // then
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOfSatisfying(SpecialActionPlayed.class, played -> {
            assertThat(played.specialAction()).isEqualTo(SpecialAction.FULFILLMENT);
            assertThat(played.targetEventId()).isEqualTo(eventId);
        });
    }

    @Test
    @DisplayName("submit — CORRUPT without a targetPlayerId — throws InvalidActionTargetException")
    void submitSpecialCorruptWithoutTargetPlayerThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.ERASERS, SpecialAction.CORRUPT, null, null, null);

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A, PLAYER_B);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — CORRUPT targeting self — throws InvalidActionTargetException")
    void submitSpecialCorruptTargetingSelfThrows() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();
        var action = special(PLAYER_A, Faction.ERASERS, SpecialAction.CORRUPT, null, null, PLAYER_A);

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> round.submit(action));
        assertThat(round.pendingPlayerIds()).containsExactly(PLAYER_A, PLAYER_B);
        assertThat(round.submittedActions()).isEmpty();
        assertThat(round.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("submit — CORRUPT targeting a valid opponent — registers SpecialActionPlayed only")
    void submitSpecialCorruptWithValidTargetRegistersSpecialActionOnly() {
        // given
        var round = openRound(List.of(PLAYER_A, PLAYER_B));
        round.pullEvents();

        // when
        round.submit(special(PLAYER_A, Faction.ERASERS, SpecialAction.CORRUPT, null, null, PLAYER_B));

        // then
        var events = round.pullEvents();
        assertThat(events).singleElement().isInstanceOfSatisfying(SpecialActionPlayed.class, played -> {
            assertThat(played.specialAction()).isEqualTo(SpecialAction.CORRUPT);
            assertThat(played.targetPlayerId()).isEqualTo(PLAYER_B);
        });
    }

    @Test
    @DisplayName(
            "close ALL_SUBMITTED — no pending players — registers ActionRoundClosed, returns Closed with empty skipped")
    void closeAllSubmittedRegistersClosedEvent() {
        // given
        var round = openRound(List.of(PLAYER_A));
        round.pullEvents();
        round.submit(card(PLAYER_A, CardType.PUSH));
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
        round.submit(card(PLAYER_A, CardType.PUSH));
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
