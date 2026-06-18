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

import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.WinConditionMet;
import io.github.temporalrift.events.shared.Faction;

@DisplayName("PlayerScore")
class PlayerScoreTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int ERA = 1;
    static final int WIN_SCORE_THRESHOLD = 20;

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
    @DisplayName("new score starts at zero without domain events")
    void newScoreStartsAtZero() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS);

        assertThat(score.totalScore()).isZero();
        assertThat(score.history()).isEmpty();
        assertThat(score.winConditionRecorded()).isFalse();
        assertThat(score.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute restores state without registering events")
    void reconstituteRegistersNoEvents() {
        var entry = new ScoreEntry(ERA, ScoreReason.CHAIN_COMPLETED, 10, 18);

        var score = PlayerScore.reconstitute(
                UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.WEAVERS, 18, false, List.of(entry));

        assertThat(score.totalScore()).isEqualTo(18);
        assertThat(score.history()).containsExactly(entry);
        assertThat(score.pullEvents()).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} scores {2}")
    @MethodSource("scoringRules")
    @DisplayName("applies documented faction scoring rule values")
    void appliesDocumentedScoringRules(Faction faction, ScoreReason reason, int pointsDelta) {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, faction);

        var entry = score.apply(ERA, reason, WIN_SCORE_THRESHOLD);

        assertThat(entry.pointsDelta()).isEqualTo(pointsDelta);
        assertThat(entry.newTotal()).isEqualTo(pointsDelta);
        assertThat(score.totalScore()).isEqualTo(pointsDelta);
        assertThat(score.history()).containsExactly(entry);
    }

    @Test
    @DisplayName("apply registers ScoresUpdated with score update details")
    void applyRegistersScoresUpdated() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.PROPHETS);

        score.apply(ERA, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, WIN_SCORE_THRESHOLD);

        var events = score.pullEvents();
        assertThat(events).singleElement().isInstanceOf(ScoresUpdated.class);
        var event = (ScoresUpdated) events.getFirst();
        assertThat(event.gameId()).isEqualTo(GAME_ID);
        assertThat(event.eraNumber()).isEqualTo(ERA);
        assertThat(event.updates()).singleElement().satisfies(update -> {
            assertThat(update.playerId()).isEqualTo(PLAYER_ID);
            assertThat(update.faction()).isEqualTo(Faction.PROPHETS);
            assertThat(update.pointsDelta()).isEqualTo(4);
            assertThat(update.reason()).isEqualTo("EVENT_RESOLVED_AS_WRITTEN");
            assertThat(update.newTotal()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("apply rejects a reason that belongs to another faction")
    void applyRejectsReasonForWrongFaction() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ERASERS);

        assertThatExceptionOfType(InvalidScoreReasonException.class)
                .isThrownBy(() -> score.apply(ERA, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, WIN_SCORE_THRESHOLD));

        assertThat(score.totalScore()).isZero();
        assertThat(score.history()).isEmpty();
        assertThat(score.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("apply allows negative scoring adjustments")
    void applyAllowsNegativeAdjustments() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.WEAVERS);

        score.apply(ERA, ScoreReason.CHAIN_BROKEN, WIN_SCORE_THRESHOLD);

        assertThat(score.totalScore()).isEqualTo(-3);
    }

    @Test
    @DisplayName("apply rejects non-positive era numbers")
    void applyRejectsNonPositiveEra() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ACTIVISTS);

        assertThatExceptionOfType(InvalidScoreEraException.class)
                .isThrownBy(() -> score.apply(0, ScoreReason.DECLARED_OUTCOME_WON, WIN_SCORE_THRESHOLD));
    }

    @Test
    @DisplayName("apply rejects non-positive win score thresholds")
    void applyRejectsNonPositiveWinScoreThreshold() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ACTIVISTS);

        assertThatExceptionOfType(InvalidWinScoreThresholdException.class)
                .isThrownBy(() -> score.apply(ERA, ScoreReason.DECLARED_OUTCOME_WON, 0));
    }

    @Test
    @DisplayName("crossing the winning threshold registers WinConditionMet once")
    void crossingWinningThresholdRegistersWinConditionMetOnce() {
        var score = PlayerScore.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                PLAYER_ID,
                Faction.WEAVERS,
                18,
                false,
                List.of(new ScoreEntry(ERA, ScoreReason.CHAIN_LINK_ADDED, 2, 18)));

        score.apply(2, ScoreReason.CHAIN_LINK_ADDED, WIN_SCORE_THRESHOLD);
        var firstEvents = score.pullEvents();
        score.apply(3, ScoreReason.CHAIN_LINK_ADDED, WIN_SCORE_THRESHOLD);
        var secondEvents = score.pullEvents();

        assertThat(firstEvents).hasSize(2);
        assertThat(firstEvents.get(1)).isInstanceOfSatisfying(WinConditionMet.class, event -> {
            assertThat(event.gameId()).isEqualTo(GAME_ID);
            assertThat(event.winnerId()).isEqualTo(PLAYER_ID);
            assertThat(event.faction()).isEqualTo("WEAVERS");
            assertThat(event.finalScore()).isEqualTo(20);
            assertThat(event.winType()).isEqualTo("SCORE_THRESHOLD");
        });
        assertThat(secondEvents).singleElement().isInstanceOf(ScoresUpdated.class);
    }

    @Test
    @DisplayName("history is immutable to callers")
    void historyIsImmutable() {
        var score = new PlayerScore(UUID.randomUUID(), GAME_ID, PLAYER_ID, Faction.ACTIVISTS);
        score.apply(ERA, ScoreReason.DECLARED_OUTCOME_WON, WIN_SCORE_THRESHOLD);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> score.history().add(new ScoreEntry(ERA, ScoreReason.DECLARED_OUTCOME_WON, 4, 8)));
    }
}
