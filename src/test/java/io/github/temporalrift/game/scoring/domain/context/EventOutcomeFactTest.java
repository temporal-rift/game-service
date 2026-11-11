package io.github.temporalrift.game.scoring.domain.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("EventOutcomeFact — written outcome shield")
class EventOutcomeFactTest {

    static final UUID EVENT = UUID.randomUUID();
    static final UUID WRITTEN = UUID.randomUUID();
    static final UUID ERASER = UUID.randomUUID();

    @ParameterizedTest(name = "written in round {0}, erased in round {1} — shielded {2}")
    @CsvSource({"1, 1, true", "1, 2, true", "2, 2, true", "1, 3, true", "3, 2, false", "2, 1, false"})
    void shieldsAnErasureAtOrAfterTheWriting(int writeRound, int annihilationRound, boolean shielded) {
        var event = new EventOutcomeFact(EVENT, null, WRITTEN, writeRound);

        assertThat(event.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, annihilationRound, true, true)))
                .isEqualTo(shielded);
    }

    @Test
    void shieldsATrailingWrittenOutcome() {
        var event = new EventOutcomeFact(EVENT, null, WRITTEN, 2);

        assertThat(event.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, 2, true, false)))
                .isTrue();
    }

    @Test
    void doesNotShieldAnotherOutcome() {
        var event = new EventOutcomeFact(EVENT, null, WRITTEN, 1);

        assertThat(event.shields(new AnnihilationFact(EVENT, UUID.randomUUID(), ERASER, 2, true, true)))
                .isFalse();
    }

    @Test
    void doesNotShieldAnotherEvent() {
        var event = new EventOutcomeFact(EVENT, null, WRITTEN, 1);

        assertThat(event.shields(new AnnihilationFact(UUID.randomUUID(), WRITTEN, ERASER, 2, true, true)))
                .isFalse();
    }

    @Test
    void doesNotShieldAnAnnihilateThatErasedNothing() {
        var event = new EventOutcomeFact(EVENT, null, WRITTEN, 1);

        assertThat(event.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, 2, false, true)))
                .isFalse();
    }

    @Test
    void doesNotShieldWithoutAWriting() {
        var event = new EventOutcomeFact(EVENT, null, null, null);

        assertThat(event.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, 2, true, true)))
                .isFalse();
    }

    @Test
    void doesNotShieldWhenARoundIsUnknown() {
        var legacyWriting = new EventOutcomeFact(EVENT, null, WRITTEN, null);
        var writing = new EventOutcomeFact(EVENT, null, WRITTEN, 1);

        assertThat(legacyWriting.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, 2, true, true)))
                .isFalse();
        assertThat(writing.shields(new AnnihilationFact(EVENT, WRITTEN, ERASER, null, true, true)))
                .isFalse();
    }

    @Test
    void contextReportsShieldedErasuresAndWritings() {
        var shielded = new AnnihilationFact(EVENT, WRITTEN, ERASER, 2, true, true);
        var otherEvent = UUID.randomUUID();
        var unshielded = new AnnihilationFact(otherEvent, UUID.randomUUID(), ERASER, 2, true, true);
        var context = new EraScoringContext(
                UUID.randomUUID(),
                1,
                List.of(new PlayerFaction(ERASER, io.github.temporalrift.game.shared.domain.model.Faction.ERASERS)),
                List.of(
                        new EventOutcomeFact(EVENT, null, WRITTEN, 1),
                        new EventOutcomeFact(otherEvent, null, UUID.randomUUID(), 1)),
                List.of(),
                List.of(),
                List.of(shielded, unshielded),
                List.of(),
                List.of(),
                List.of(),
                Set.of());

        assertThat(context.isShielded(shielded)).isTrue();
        assertThat(context.isShielded(unshielded)).isFalse();
        assertThat(context.isWritingShielded(EVENT)).isTrue();
        assertThat(context.isWritingShielded(otherEvent)).isFalse();
    }
}
