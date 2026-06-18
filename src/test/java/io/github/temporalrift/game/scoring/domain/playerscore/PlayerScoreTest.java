package io.github.temporalrift.game.scoring.domain.playerscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.temporalrift.events.shared.Faction;

@DisplayName("PlayerScore")
class PlayerScoreTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int ERA = 1;

    static Stream<Arguments> scoringRules() {
        return Stream.of(
                Arguments.of(Faction.ERASERS, ScoreReason.ANNIHILATED_OUTCOME, 3),
                Arguments.of(Faction.ERASERS, ScoreReason.CORRUPTED_OPPONENT_CARD, 2),
                Arguments.of(Faction.ERASERS, ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES, 5),
                Arguments.of(Faction.PROPHETS, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4),
                Arguments.of(Faction.PROPHETS, ScoreReason.FULFILLMENT_SUCCEEDED, 8),
                Arguments.of(Faction.PROPHETS, ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN, -2),
                Arguments.of(Faction.REVISIONISTS, ScoreReason.SECRET_OUTCOME_WON, 4),
                Arguments.of(Faction.REVISIONISTS, ScoreReason.FACTION_UNIDENTIFIED, 6),
                Arguments.of(Faction.REVISIONISTS, ScoreReason.MIMIC_CONTRIBUTED_TO_WIN, 2),
                Arguments.of(Faction.WEAVERS, ScoreReason.CHAIN_LINK_ADDED, 2),
                Arguments.of(Faction.WEAVERS, ScoreReason.CHAIN_COMPLETED, 10),
                Arguments.of(Faction.WEAVERS, ScoreReason.CHAIN_BROKEN, -3),
                Arguments.of(Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8),
                Arguments.of(Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON, 4),
                Arguments.of(Faction.ACTIVISTS, ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR, 2));
    }

    @Test
    @DisplayName("new score starts at zero")
    void newScoreStartsAtZero() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS);

        assertThat(score.totalScore()).isZero();
        assertThat(score.history()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute restores persisted state")
    void reconstituteRestoresState() {
        var entry = new ScoreEntry(ERA, ScoreReason.CHAIN_COMPLETED, 10, 18);

        var score =
                PlayerScore.reconstitute(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.WEAVERS, 18, List.of(entry));

        assertThat(score.totalScore()).isEqualTo(18);
        assertThat(score.history()).containsExactly(entry);
    }

    @ParameterizedTest(name = "{0} {1} scores {2}")
    @MethodSource("scoringRules")
    @DisplayName("applies documented faction scoring rule values")
    void appliesDocumentedScoringRules(Faction faction, ScoreReason reason, int pointsDelta) {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, faction);

        var entry = score.apply(ERA, reason);

        assertThat(entry.pointsDelta()).isEqualTo(pointsDelta);
        assertThat(entry.newTotal()).isEqualTo(pointsDelta);
        assertThat(score.totalScore()).isEqualTo(pointsDelta);
        assertThat(score.history()).containsExactly(entry);
    }

    @Test
    @DisplayName("apply rejects a reason that belongs to another faction")
    void applyRejectsReasonForWrongFaction() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS);

        assertThatExceptionOfType(InvalidScoreReasonException.class)
                .isThrownBy(() -> score.apply(ERA, ScoreReason.EVENT_RESOLVED_AS_WRITTEN));

        assertThat(score.totalScore()).isZero();
        assertThat(score.history()).isEmpty();
    }

    @Test
    @DisplayName("apply allows negative scoring adjustments")
    void applyAllowsNegativeAdjustments() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.WEAVERS);

        score.apply(ERA, ScoreReason.CHAIN_BROKEN);

        assertThat(score.totalScore()).isEqualTo(-3);
    }

    @Test
    @DisplayName("apply accumulates score and history entries")
    void applyAccumulatesScoreAndHistory() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS);

        var first = score.apply(ERA, ScoreReason.ANNIHILATED_OUTCOME);
        var second = score.apply(ERA, ScoreReason.CORRUPTED_OPPONENT_CARD);

        assertThat(score.totalScore()).isEqualTo(5);
        assertThat(first.newTotal()).isEqualTo(3);
        assertThat(second.newTotal()).isEqualTo(5);
        assertThat(score.history()).containsExactly(first, second);
    }

    @Test
    @DisplayName("apply rejects non-positive era numbers")
    void applyRejectsNonPositiveEra() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ACTIVISTS);

        assertThatExceptionOfType(InvalidScoreEraException.class)
                .isThrownBy(() -> score.apply(0, ScoreReason.DECLARED_OUTCOME_WON));
    }

    @Test
    @DisplayName("score entry rejects missing reason")
    void scoreEntryRejectsMissingReason() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ScoreEntry(ERA, null, 4, 4))
                .withMessage("reason must not be null");
    }

    @Test
    @DisplayName("history is immutable to callers")
    void historyIsImmutable() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ACTIVISTS);
        score.apply(ERA, ScoreReason.DECLARED_OUTCOME_WON);
        var history = score.history();
        var entry = new ScoreEntry(ERA, ScoreReason.DECLARED_OUTCOME_WON, 4, 8);

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> history.add(entry));
    }
}
