package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.ScoreRulesTestValues;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

class ScoreRulesPropertiesTest {

    @Test
    void exposesConfiguredScoreDelta() {
        var scoreDeltas = completeScoreDeltas();
        scoreDeltas.put(ScoreReason.CHAIN_COMPLETED, 12);

        var properties = new ScoreRulesProperties(scoreDeltas);

        assertThat(properties.pointsDelta(ScoreReason.CHAIN_COMPLETED)).isEqualTo(12);
    }

    @Test
    void defaultScoreTableMatchesCurrentValues() {
        var properties = new ScoreRulesProperties(completeScoreDeltas());

        for (var reason : ScoreReason.values()) {
            assertThat(properties.pointsDelta(reason)).isEqualTo(ScoreRulesTestValues.pointsDelta(reason));
        }
    }

    @Test
    void rejectsMissingScoreReason() {
        var scoreDeltas = completeScoreDeltas();
        scoreDeltas.remove(ScoreReason.CHAIN_COMPLETED);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreRulesProperties(scoreDeltas))
                .withMessageContaining("CHAIN_COMPLETED");
    }

    @Test
    void rejectsNullScoreDelta() {
        var scoreDeltas = completeScoreDeltas();
        scoreDeltas.put(ScoreReason.CHAIN_COMPLETED, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreRulesProperties(scoreDeltas))
                .withMessageContaining("CHAIN_COMPLETED");
    }

    private static EnumMap<ScoreReason, Integer> completeScoreDeltas() {
        var values = new EnumMap<ScoreReason, Integer>(ScoreReason.class);
        for (var reason : ScoreReason.values()) {
            values.put(reason, ScoreRulesTestValues.pointsDelta(reason));
        }
        return values;
    }
}
