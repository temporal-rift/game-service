package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

/**
 * Pins the configured defaults to the design target that every faction objective is met before the score entries
 * advancing it alone reach the win threshold.
 */
class FactionObjectiveBalanceTest {

    static VictoryRulesProperties victory;
    static ScoreRulesProperties scores;
    static int winThreshold;

    @BeforeAll
    static void bindTestConfiguration() throws IOException {
        var sources = new MutablePropertySources();
        new YamlPropertySourceLoader()
                .load("application-test", new ClassPathResource("application-test.yml"))
                .forEach(sources::addLast);
        var binder = new Binder(ConfigurationPropertySources.from(sources));
        victory = binder.bindOrCreate("game.rules.victory", VictoryRulesProperties.class);
        scores = binder.bind("game.rules.scoring", ScoreRulesProperties.class).get();
        winThreshold =
                binder.bind("game.rules.win-score-threshold", Integer.class).get();
    }

    @Test
    @DisplayName("configured objective thresholds and declaration scores match the balanced defaults")
    void configuredValues() {
        assertThat(winThreshold).isEqualTo(20);
        assertThat(victory.prophetWrittenResolutions()).isEqualTo(4);
        assertThat(scores.pointsDelta(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY))
                .isEqualTo(6);
        assertThat(scores.pointsDelta(ScoreReason.DECLARED_OUTCOME_WON)).isEqualTo(5);
    }

    @Test
    @DisplayName("eraser annihilations meet the objective below the threshold")
    void eraser() {
        assertThat(victory.eraserAnnihilations() * points(ScoreReason.ANNIHILATED_OUTCOME))
                .isLessThan(winThreshold);
    }

    @Test
    @DisplayName("prophet ordinary written wins meet the objective below the threshold")
    void prophet() {
        assertThat(victory.prophetWrittenResolutions() * points(ScoreReason.EVENT_RESOLVED_AS_WRITTEN))
                .isLessThan(winThreshold);
    }

    @Test
    @DisplayName("prophet Fulfillment bets reach the threshold before the objective")
    void prophetFulfillment() {
        int writtenWins = 3;
        int oneOrdinaryTwoFulfillments =
                points(ScoreReason.EVENT_RESOLVED_AS_WRITTEN) + 2 * points(ScoreReason.FULFILLMENT_SUCCEEDED);

        assertThat(oneOrdinaryTwoFulfillments).isGreaterThanOrEqualTo(winThreshold);
        assertThat(writtenWins).isLessThan(victory.prophetWrittenResolutions());
    }

    @Test
    @DisplayName("revisionist winning eras meet the objective below the threshold")
    void revisionist() {
        assertThat(victory.revisionistSuccessfulEras() * points(ScoreReason.SECRET_OUTCOME_WON))
                .isLessThan(winThreshold);
    }

    @Test
    @DisplayName("weaver links and completion meet the objective below the threshold")
    void weaver() {
        assertThat(victory.weaverChainLength() * points(ScoreReason.CHAIN_LINK_ADDED)
                        + points(ScoreReason.CHAIN_COMPLETED))
                .isLessThan(winThreshold);
    }

    @Test
    @DisplayName("an all-Rally activist streak meets the objective below the threshold")
    void activist() {
        int bestDeclaration =
                Math.max(points(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY), points(ScoreReason.DECLARED_OUTCOME_WON));

        assertThat(victory.activistConsecutiveDeclarations() * bestDeclaration).isLessThan(winThreshold);
    }

    // Rally must pay more on a likely win; Momentum above four fifths of Rally keeps its +10-point opening worth
    // more in expectation whenever the declared outcome's chance of winning is below roughly 40%.
    @Test
    @DisplayName("Rally pays more than Momentum but not by enough to dominate it")
    void rallyAndMomentum() {
        int rally = points(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY);
        int momentum = points(ScoreReason.DECLARED_OUTCOME_WON);

        assertThat(rally).isGreaterThan(momentum);
        assertThat(momentum * 5).isGreaterThan(rally * 4);
    }

    private static int points(ScoreReason reason) {
        return scores.pointsDelta(reason);
    }
}
