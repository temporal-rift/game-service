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

    private static final StabilizationThresholds THRESHOLDS = new StabilizationThresholds(3, 2);

    private static Standing standing(Faction faction, int score, int objectiveProgress) {
        return new Standing(UUID.randomUUID(), faction, score, objectiveProgress);
    }

    @Nested
    class Stabilization {

        @Test
        @DisplayName("3 players: Prophet with enough written resolutions wins over a higher score")
        void threePlayers_qualifyingProphetWins() {
            var prophet = standing(Faction.PROPHETS, 8, 3);
            var eraser = standing(Faction.ERASERS, 18, 4);
            var activist = standing(Faction.ACTIVISTS, 12, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(List.of(prophet, eraser, activist), THRESHOLDS);

            assertThat(winners).containsExactly(prophet.playerId());
        }

        @Test
        @DisplayName("3 players: Prophet below the threshold falls back to the highest score")
        void threePlayers_prophetBelowThreshold_highestScoreWins() {
            var prophet = standing(Faction.PROPHETS, 8, 2);
            var eraser = standing(Faction.ERASERS, 18, 4);
            var revisionist = standing(Faction.REVISIONISTS, 12, 1);

            var winners = SpecialEndingPolicy.stabilizationWinners(List.of(prophet, eraser, revisionist), THRESHOLDS);

            assertThat(winners).containsExactly(eraser.playerId());
        }

        @Test
        @DisplayName("3 players without Prophet or Weaver: highest score wins")
        void threePlayers_noProphetOrWeaver_highestScoreWins() {
            var eraser = standing(Faction.ERASERS, 10, 3);
            var revisionist = standing(Faction.REVISIONISTS, 15, 2);
            var activist = standing(Faction.ACTIVISTS, 11, 2);

            var winners = SpecialEndingPolicy.stabilizationWinners(List.of(eraser, revisionist, activist), THRESHOLDS);

            assertThat(winners).containsExactly(revisionist.playerId());
        }

        @Test
        @DisplayName("4 players: Weaver one link short of completion wins")
        void fourPlayers_weaverOneLinkShortWins() {
            var weaver = standing(Faction.WEAVERS, 9, 2);
            var prophet = standing(Faction.PROPHETS, 14, 1);
            var eraser = standing(Faction.ERASERS, 16, 3);
            var activist = standing(Faction.ACTIVISTS, 12, 0);

            var winners =
                    SpecialEndingPolicy.stabilizationWinners(List.of(weaver, prophet, eraser, activist), THRESHOLDS);

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
                    List.of(weaver, revisionist, eraser, activist), THRESHOLDS);

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
                    List.of(prophet, weaver, eraser, revisionist, activist), THRESHOLDS);

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
                    List.of(prophet, weaver, eraser, revisionist, activist), THRESHOLDS);

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
    }

    @Nested
    class Collapse {

        @Test
        @DisplayName("3 players with an Activist: highest score wins regardless of faction")
        void threePlayers_highestScoreWins() {
            var activist = standing(Faction.ACTIVISTS, 6, 2);
            var prophet = standing(Faction.PROPHETS, 11, 4);
            var weaver = standing(Faction.WEAVERS, 9, 2);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(activist, prophet, weaver)))
                    .containsExactly(prophet.playerId());
        }

        @Test
        @DisplayName("4 players without an Activist: highest score wins")
        void fourPlayers_noActivist_highestScoreWins() {
            var prophet = standing(Faction.PROPHETS, 3, 1);
            var weaver = standing(Faction.WEAVERS, -2, 0);
            var eraser = standing(Faction.ERASERS, 7, 2);
            var revisionist = standing(Faction.REVISIONISTS, 5, 1);

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(prophet, weaver, eraser, revisionist)))
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

            assertThat(SpecialEndingPolicy.collapseWinners(List.of(prophet, weaver, eraser, revisionist, activist)))
                    .containsExactlyInAnyOrder(prophet.playerId(), eraser.playerId());
        }
    }

    @Test
    @DisplayName("non-positive stabilization thresholds are rejected")
    void thresholds_mustBePositive() {
        assertThatThrownBy(() -> new StabilizationThresholds(0, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StabilizationThresholds(3, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
