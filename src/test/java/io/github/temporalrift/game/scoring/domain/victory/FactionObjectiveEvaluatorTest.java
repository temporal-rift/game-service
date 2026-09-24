package io.github.temporalrift.game.scoring.domain.victory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;
import io.github.temporalrift.game.shared.domain.model.Faction;

class FactionObjectiveEvaluatorTest {

    static final VictoryRulesPort RULES = new VictoryRulesPort() {
        @Override
        public int eraserAnnihilations() {
            return 4;
        }

        @Override
        public int prophetWrittenResolutions() {
            return 5;
        }

        @Override
        public int revisionistSuccessfulEras() {
            return 3;
        }

        @Override
        public int weaverChainLength() {
            return 3;
        }

        @Override
        public int activistConsecutiveDeclarations() {
            return 3;
        }
    };

    @Test
    @DisplayName("eraser with four annihilations meets the objective")
    void eraser_fourAnnihilations_objectiveMet() {
        var score = score(Faction.ERASERS);
        for (int era = 1; era <= 4; era++) {
            score.apply(era, ScoreReason.ANNIHILATED_OUTCOME, 3);
        }

        var progress = FactionObjectiveEvaluator.evaluate(score, 4, RULES);

        assertThat(progress.objectiveMet()).isTrue();
        assertThat(progress.progressCount()).isEqualTo(4);
        assertThat(progress.threshold()).isEqualTo(4);
    }

    @Test
    @DisplayName("eraser below the annihilation threshold does not meet the objective")
    void eraser_threeAnnihilations_objectiveNotMet() {
        var score = score(Faction.ERASERS);
        for (int era = 1; era <= 3; era++) {
            score.apply(era, ScoreReason.ANNIHILATED_OUTCOME, 3);
        }

        assertThat(FactionObjectiveEvaluator.evaluate(score, 3, RULES).objectiveMet())
                .isFalse();
    }

    @Test
    @DisplayName("prophet counts written resolutions and fulfillments together")
    void prophet_mixedWrittenAndFulfillment_objectiveMet() {
        var score = score(Faction.PROPHETS);
        score.apply(1, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
        score.apply(1, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
        score.apply(2, ScoreReason.FULFILLMENT_SUCCEEDED, 8);
        score.apply(3, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
        score.apply(3, ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN, -2);
        score.apply(4, ScoreReason.FULFILLMENT_SUCCEEDED, 8);

        var progress = FactionObjectiveEvaluator.evaluate(score, 4, RULES);

        assertThat(progress.progressCount()).isEqualTo(5);
        assertThat(progress.objectiveMet()).isTrue();
    }

    @Test
    @DisplayName("revisionist counts distinct successful eras, not entries")
    void revisionist_threeDistinctEras_objectiveMet() {
        var score = score(Faction.REVISIONISTS);
        score.apply(1, ScoreReason.SECRET_OUTCOME_WON, 4);
        score.apply(2, ScoreReason.SECRET_OUTCOME_WON, 4);
        score.apply(2, ScoreReason.MIMIC_CONTRIBUTED_TO_WIN, 2);
        score.apply(3, ScoreReason.SECRET_OUTCOME_WON, 4);

        var progress = FactionObjectiveEvaluator.evaluate(score, 3, RULES);

        assertThat(progress.progressCount()).isEqualTo(3);
        assertThat(progress.objectiveMet()).isTrue();
    }

    @Test
    @DisplayName("weaver with a completed chain meets the objective at full length")
    void weaver_chainCompleted_objectiveMet() {
        var score = score(Faction.WEAVERS);
        score.apply(1, ScoreReason.CHAIN_LINK_ADDED, 2);
        score.apply(2, ScoreReason.CHAIN_LINK_ADDED, 2);
        score.apply(3, ScoreReason.CHAIN_COMPLETED, 10);

        var progress = FactionObjectiveEvaluator.evaluate(score, 3, RULES);

        assertThat(progress.objectiveMet()).isTrue();
        assertThat(progress.progressCount()).isEqualTo(3);
        assertThat(progress.threshold()).isEqualTo(3);
    }

    @Test
    @DisplayName("weaver without a completion does not meet the objective")
    void weaver_noCompletion_objectiveNotMet() {
        var score = score(Faction.WEAVERS);
        score.apply(1, ScoreReason.CHAIN_LINK_ADDED, 2);

        var progress = FactionObjectiveEvaluator.evaluate(score, 1, RULES);

        assertThat(progress.objectiveMet()).isFalse();
        assertThat(progress.progressCount()).isZero();
    }

    @Test
    @DisplayName("activist with three consecutive successes ending at the current era meets the objective")
    void activist_threeConsecutiveSuccesses_objectiveMet() {
        var score = score(Faction.ACTIVISTS);
        score.apply(1, ScoreReason.DECLARED_OUTCOME_WON, 4);
        score.apply(2, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8);
        score.apply(3, ScoreReason.DECLARED_OUTCOME_WON, 4);

        var progress = FactionObjectiveEvaluator.evaluate(score, 3, RULES);

        assertThat(progress.progressCount()).isEqualTo(3);
        assertThat(progress.objectiveMet()).isTrue();
    }

    @Test
    @DisplayName("activist streak broken before the current era does not meet the objective")
    void activist_streakNotEndingAtCurrentEra_objectiveNotMet() {
        var score = score(Faction.ACTIVISTS);
        score.apply(1, ScoreReason.DECLARED_OUTCOME_WON, 4);
        score.apply(2, ScoreReason.DECLARED_OUTCOME_WON, 4);
        score.apply(3, ScoreReason.DECLARED_OUTCOME_WON, 4);
        score.apply(5, ScoreReason.EXPOSE_CHANGED_PLAYER_BEHAVIOR, 2);

        assertThat(FactionObjectiveEvaluator.evaluate(score, 5, RULES).objectiveMet())
                .isFalse();
    }

    @Test
    @DisplayName("activist rally and momentum successes count toward the same streak")
    void activist_mixedModes_singleStreak() {
        var score = score(Faction.ACTIVISTS);
        score.apply(1, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8);
        score.apply(2, ScoreReason.DECLARED_OUTCOME_WON, 4);
        score.apply(3, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8);

        assertThat(FactionObjectiveEvaluator.evaluate(score, 3, RULES).objectiveMet())
                .isTrue();
    }

    @Test
    void activist_stalledEraBreaksStreakEvenIfTheCarriedEventWinsLater() {
        var score = score(Faction.ACTIVISTS);
        score.apply(1, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8);
        score.apply(2, ScoreReason.DECLARED_OUTCOME_WON, 4);

        assertThat(FactionObjectiveEvaluator.evaluate(score, 3, RULES).progressCount())
                .isZero();

        score.apply(4, ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY, 8);
        assertThat(FactionObjectiveEvaluator.evaluate(score, 4, RULES).progressCount())
                .isEqualTo(1);
        assertThat(FactionObjectiveEvaluator.evaluate(score, 5, RULES).progressCount())
                .isZero();
    }

    private static PlayerScore score(Faction faction) {
        return new PlayerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), faction);
    }
}
