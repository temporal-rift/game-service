package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

class ScanCardActionTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();
    private static final UUID EVENT_1 = UUID.randomUUID();
    private static final UUID EVENT_2 = UUID.randomUUID();
    private static final UUID EVENT_3 = UUID.randomUUID();
    private static final Set<UUID> CURRENT_ERA_EVENTS = Set.of(EVENT_1, EVENT_2, EVENT_3);

    @Test
    void everyGradeAcceptsItsRequiredCurrentEraSelection() {
        assertThatCode(() -> validate(scan(CardGrade.I, List.of(EVENT_1)))).doesNotThrowAnyException();
        assertThatCode(() -> validate(scan(CardGrade.II, List.of(EVENT_1, EVENT_2))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(scan(CardGrade.III, List.of(EVENT_3, EVENT_1, EVENT_2))))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresListModeAndRejectsMixedScalarCoordinates() {
        var missing = scan(CardGrade.I, null);
        var mixed = new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.SCAN, CardGrade.I, EVENT_1, List.of(EVENT_1), null, null, null);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> missing.validate(1, 1))
                .withMessageContaining("targetEventIds");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> mixed.validate(1, 1))
                .withMessageContaining("scalar");
    }

    @Test
    void rejectsDuplicateAndGradeCountMismatch() {
        var duplicate = scan(CardGrade.II, List.of(EVENT_1, EVENT_1));
        var wrongCount = scan(CardGrade.II, List.of(EVENT_1));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> duplicate.validate(1, 1))
                .withMessageContaining("distinct");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> wrongCount.validate(1, 1))
                .withMessageContaining("exactly 2");
    }

    @Test
    void rejectsUnknownCurrentEraTarget() {
        var unknown = UUID.randomUUID();
        var action = scan(CardGrade.I, List.of(unknown));

        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validate(action))
                .withMessageContaining(unknown.toString());
    }

    @Test
    void gradeThreeRequiresTheCompleteCurrentEraSetNotMerelyThreeIds() {
        var foreignEvent = UUID.randomUUID();
        var action = scan(CardGrade.III, List.of(EVENT_1, EVENT_2, foreignEvent));

        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validate(action))
                .withMessageContaining(foreignEvent.toString());

        var incompleteKnownSet = Set.of(EVENT_1, EVENT_2, EVENT_3, UUID.randomUUID());
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(
                        () -> actionWithEvents(EVENT_1, EVENT_2, EVENT_3).validateCurrentEraTargets(incompleteKnownSet))
                .withMessageContaining("complete current-era event set");
    }

    @Test
    void selectionIsDefensivelyCopiedAndPublishedWithoutScalarEventTarget() {
        var submittedTargets = new ArrayList<>(List.of(EVENT_1, EVENT_2));
        var action = scan(CardGrade.II, submittedTargets);
        submittedTargets.clear();

        assertThat(action.targetEventIds()).containsExactly(EVENT_1, EVENT_2);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> action.targetEventIds().add(EVENT_3));

        var played = (CardPlayed) action.toPlayedEvent(UUID.randomUUID(), 1, 1);
        assertThat(played.targetEventId()).isNull();
        assertThat(played.targetEventIds()).containsExactly(EVENT_1, EVENT_2);
    }

    @Test
    void nonScanCardsRejectListTargetMode() {
        var push = new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.PUSH, CardGrade.I, null, List.of(EVENT_1), null, null, null);

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> push.validate(1, 1));
    }

    private static void validate(SubmittedAction.CardAction action) {
        action.validate(1, 1);
        action.validateCurrentEraTargets(CURRENT_ERA_EVENTS);
    }

    private static SubmittedAction.CardAction scan(CardGrade grade, List<UUID> targets) {
        return new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.SCAN, grade, null, targets, null, null, null);
    }

    private static SubmittedAction.CardAction actionWithEvents(UUID... targets) {
        return scan(CardGrade.III, List.of(targets));
    }
}
