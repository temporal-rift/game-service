package io.github.temporalrift.game.session.domain.ending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.session.domain.ending.SpecialEndingPolicy.Standing;
import io.github.temporalrift.game.shared.domain.model.Faction;

class SpecialEndingPolicyTest {

    /** The optional claim rule switched on, so these tests pin the Prophet and Weaver claims themselves. */
    private static final StabilizationThresholds THRESHOLDS = new StabilizationThresholds(3, 2, true);

    /** The default rule: no faction claims stabilization. */
    private static final StabilizationThresholds NO_CLAIMS = new StabilizationThresholds(3, 2, false);

    private static Standing standing(Faction faction, int score, int objectiveProgress) {
        return new Standing(UUID.randomUUID(), faction, score, objectiveProgress, 3, false);
    }

    private static Standing forfeited(Faction faction, int score, int objectiveProgress) {
        return new Standing(UUID.randomUUID(), faction, score, objectiveProgress, 3, true);
    }

    @Nested
    @DisplayName("with the score tiebreak on")
    class ScoreTiebreak {

        @Test
        @DisplayName("a tied highest score at stabilization goes to the most objective progress")
        void stabilizationTieGoesToProgress() {
            var ahead = new Standing(UUID.randomUUID(), Faction.PROPHETS, 12, 2, 3, false);
            var behind = new Standing(UUID.randomUUID(), Faction.ERASERS, 12, 1, 4, false);

            assertThat(SpecialEndingPolicy.stabilizationWinners(List.of(ahead, behind), NO_CLAIMS, true))
                    .containsExactly(ahead.playerId());
        }

        @Test
        @DisplayName("progress is compared as a fraction of each threshold")
        void progressComparedAsFraction() {
            var twoOfFour = new Standing(UUID.randomUUID(), Faction.ERASERS, 9, 2, 4, false);
            var twoOfThree = new Standing(UUID.randomUUID(), Faction.REVISIONISTS, 9, 2, 3, false);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(twoOfFour, twoOfThree), true))
                    .containsExactly(twoOfThree.playerId());
        }

        @Test
        @DisplayName("equal score and equal progress still share")
        void fullTieShares() {
            var first = new Standing(UUID.randomUUID(), Faction.PROPHETS, 10, 1, 3, false);
            var second = new Standing(UUID.randomUUID(), Faction.WEAVERS, 10, 1, 3, false);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(first, second), true))
                    .containsExactlyInAnyOrder(first.playerId(), second.playerId());
        }

        @Test
        @DisplayName("with the tiebreak off a tied highest score shares")
        void tiebreakOffShares() {
            var ahead = new Standing(UUID.randomUUID(), Faction.PROPHETS, 12, 2, 3, false);
            var behind = new Standing(UUID.randomUUID(), Faction.ERASERS, 12, 1, 4, false);

            assertThat(SpecialEndingPolicy.stabilizationWinners(List.of(ahead, behind), NO_CLAIMS, false))
                    .containsExactlyInAnyOrder(ahead.playerId(), behind.playerId());
        }
    }

    @Nested
    class Stabilization {

        @Test
        @DisplayName("default rule: a Prophet with enough written resolutions loses to a higher score")
        void defaultRule_prophetLosesToHigherScore() {
            var prophet = standing(Faction.PROPHETS, 8, 3);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(List.of(prophet, activist), NO_CLAIMS, false);

            assertThat(winners).containsExactly(activist.playerId());
            assertThat(SpecialEndingPolicy.qualifiesForStabilization(prophet, NO_CLAIMS))
                    .isFalse();
        }

        @Test
        @DisplayName("default rule: a Weaver with an intact chain loses to a higher score")
        void defaultRule_weaverLosesToHigherScore() {
            var weaver = standing(Faction.WEAVERS, 9, 3);
            var eraser = standing(Faction.ERASERS, 11, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(List.of(weaver, eraser), NO_CLAIMS, false);

            assertThat(winners).containsExactly(eraser.playerId());
        }

        @Test
        @DisplayName("default rule: tied highest scores share stabilization")
        void defaultRule_tiedHighestScoresShare() {
            var prophet = standing(Faction.PROPHETS, 12, 3);
            var activist = standing(Faction.ACTIVISTS, 12, 2);
            var eraser = standing(Faction.ERASERS, 7, 1);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(prophet, activist, eraser), NO_CLAIMS, false);

            assertThat(winners).containsExactlyInAnyOrder(prophet.playerId(), activist.playerId());
        }

        @Test
        @DisplayName("3 players: Prophet with enough written resolutions wins over a higher score")
        void threePlayers_qualifyingProphetWins() {
            var prophet = standing(Faction.PROPHETS, 8, 3);
            var eraser = standing(Faction.ERASERS, 18, 4);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(prophet, eraser, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(prophet.playerId());
        }

        @Test
        @DisplayName("3 players: Prophet below the threshold falls back to the highest score")
        void threePlayers_prophetBelowThreshold_highestScoreWins() {
            var prophet = standing(Faction.PROPHETS, 8, 2);
            var eraser = standing(Faction.ERASERS, 18, 4);
            var revisionist = standing(Faction.REVISIONISTS, 12, 1);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(prophet, eraser, revisionist), THRESHOLDS, false);

            assertThat(winners).containsExactly(eraser.playerId());
        }

        @Test
        @DisplayName("3 players without Prophet or Weaver: highest score wins")
        void threePlayers_noProphetOrWeaver_highestScoreWins() {
            var eraser = standing(Faction.ERASERS, 10, 3);
            var revisionist = standing(Faction.REVISIONISTS, 15, 2);
            var activist = standing(Faction.ACTIVISTS, 11, 2);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(eraser, revisionist, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(revisionist.playerId());
        }

        @Test
        @DisplayName("4 players: Weaver one link short of completion wins")
        void fourPlayers_weaverOneLinkShortWins() {
            var weaver = standing(Faction.WEAVERS, 9, 2);
            var prophet = standing(Faction.PROPHETS, 14, 1);
            var eraser = standing(Faction.ERASERS, 16, 3);
            var activist = standing(Faction.ACTIVISTS, 12, 0);

            var winners = SpecialEndingPolicy.stabilizationWinners(
                    List.of(weaver, prophet, eraser, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(weaver.playerId());
        }

        @Test
        @DisplayName("4 players: Weaver with a single intact link does not qualify")
        void fourPlayers_weaverSingleLink_highestScoreWins() {
            var weaver = standing(Faction.WEAVERS, 9, 1);
            var revisionist = standing(Faction.REVISIONISTS, 14, 2);
            var eraser = standing(Faction.ERASERS, 16, 3);
            var activist = standing(Faction.ACTIVISTS, 12, 0);

            var winners = SpecialEndingPolicy.stabilizationWinners(
                    List.of(weaver, revisionist, eraser, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(eraser.playerId());
        }

        @Test
        @DisplayName("5 players: qualifying Prophet and Weaver share the win")
        void fivePlayers_prophetAndWeaverShare() {
            var prophet = standing(Faction.PROPHETS, 10, 4);
            var weaver = standing(Faction.WEAVERS, 7, 2);
            var eraser = standing(Faction.ERASERS, 18, 3);
            var revisionist = standing(Faction.REVISIONISTS, 16, 2);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(
                    List.of(prophet, weaver, eraser, revisionist, activist), THRESHOLDS, false);

            assertThat(winners).containsExactlyInAnyOrder(prophet.playerId(), weaver.playerId());
        }

        @Test
        @DisplayName("5 players: no qualifier and a tied highest score shares the win")
        void fivePlayers_noQualifier_tiedHighestScoreShares() {
            var prophet = standing(Faction.PROPHETS, 17, 2);
            var weaver = standing(Faction.WEAVERS, 7, 1);
            var eraser = standing(Faction.ERASERS, 17, 3);
            var revisionist = standing(Faction.REVISIONISTS, 16, 2);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(
                    List.of(prophet, weaver, eraser, revisionist, activist), THRESHOLDS, false);

            assertThat(winners).containsExactlyInAnyOrder(prophet.playerId(), eraser.playerId());
        }

        @Test
        @DisplayName("Eraser, Revisionist, and Activist progress never qualifies")
        void otherFactions_neverQualify() {
            for (var faction : List.of(Faction.ERASERS, Faction.REVISIONISTS, Faction.ACTIVISTS)) {
                assertThat(SpecialEndingPolicy.qualifiesForStabilization(standing(faction, 0, 99), THRESHOLDS))
                        .isFalse();
            }
        }

        @Test
        @DisplayName("abandoned Prophet meeting the threshold does not win; remaining players are ranked")
        void abandonedProphet_doesNotWin() {
            var prophet = forfeited(Faction.PROPHETS, 8, 3);
            var eraser = standing(Faction.ERASERS, 18, 4);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(prophet, eraser, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(eraser.playerId());
            assertThat(SpecialEndingPolicy.qualifiesForStabilization(prophet, THRESHOLDS))
                    .isFalse();
        }

        @Test
        @DisplayName("abandoned leader does not win the highest-score fallback")
        void abandonedLeader_doesNotWinFallback() {
            var eraser = forfeited(Faction.ERASERS, 18, 0);
            var revisionist = standing(Faction.REVISIONISTS, 12, 0);
            var activist = standing(Faction.ACTIVISTS, 11, 0);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(eraser, revisionist, activist), THRESHOLDS, false);

            assertThat(winners).containsExactly(revisionist.playerId());
        }
    }

    @Nested
    class Collapse {

        @Test
        @DisplayName("3 players with an Activist: highest score wins regardless of faction")
        void threePlayers_highestScoreWins() {
            var activist = standing(Faction.ACTIVISTS, 6, 2);
            var prophet = standing(Faction.PROPHETS, 11, 4);
            var weaver = standing(Faction.WEAVERS, 9, 2);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(activist, prophet, weaver), false))
                    .containsExactly(prophet.playerId());
        }

        @Test
        @DisplayName("4 players without an Activist: highest score wins")
        void fourPlayers_noActivist_highestScoreWins() {
            var prophet = standing(Faction.PROPHETS, 3, 1);
            var weaver = standing(Faction.WEAVERS, -2, 0);
            var eraser = standing(Faction.ERASERS, 7, 2);
            var revisionist = standing(Faction.REVISIONISTS, 5, 1);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(prophet, weaver, eraser, revisionist), false))
                    .containsExactly(eraser.playerId());
        }

        @Test
        @DisplayName("5 players: tied highest score shares the win, even below zero")
        void fivePlayers_tiedHighestScoreShares() {
            var prophet = standing(Faction.PROPHETS, -1, 0);
            var weaver = standing(Faction.WEAVERS, -4, 0);
            var eraser = standing(Faction.ERASERS, -1, 1);
            var revisionist = standing(Faction.REVISIONISTS, -3, 0);
            var activist = standing(Faction.ACTIVISTS, -6, 0);

            assertThat(SpecialEndingPolicy.collapseWinners(
                            List.of(prophet, weaver, eraser, revisionist, activist), false))
                    .containsExactlyInAnyOrder(prophet.playerId(), eraser.playerId());
        }

        @Test
        @DisplayName("abandoned leader does not win collapse")
        void abandonedLeader_doesNotWinCollapse() {
            var leader = forfeited(Faction.ERASERS, 20, 0);
            var second = standing(Faction.PROPHETS, 14, 0);
            var third = standing(Faction.ACTIVISTS, 9, 0);

            var winners = SpecialEndingPolicy.collapseWinners(List.of(leader, second, third), false);

            assertThat(winners).containsExactly(second.playerId());
        }
    }

    @Test
    @DisplayName("non-positive stabilization thresholds are rejected")
    void thresholds_mustBePositive() {
        assertThatThrownBy(() -> new StabilizationThresholds(0, 2, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StabilizationThresholds(3, 0, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
