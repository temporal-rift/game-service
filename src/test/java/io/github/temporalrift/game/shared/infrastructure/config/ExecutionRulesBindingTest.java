package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.config.ScoreRulesProperties;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.config.VictoryRulesProperties;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.infrastructure.adapter.out.config.SessionRulesProperties;
import io.github.temporalrift.game.shared.domain.model.InvalidExecutionRulesException;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;
import io.github.temporalrift.game.simulation.domain.execution.EffectiveRules;

class ExecutionRulesBindingTest {

    private final jakarta.validation.Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private ExecutionRulesBinding<SessionRulesProperties> sessionBinding() {
        return new ExecutionRulesBinding<>(SessionRulesProperties.class, "game.rules", validator);
    }

    private static Map<String, String> bindable(Map<String, Object> rules) {
        return new EffectiveRules(rules).bindable();
    }

    private static ExecutionRules.Rules rulesOf(int winScore) {
        var rules = new EffectiveRules(ExecutionRulesFixture.rulesWith("game.rules.win-score-threshold", winScore));
        return new ExecutionRules.Rules(rules.digest(), rules.bindable());
    }

    @Test
    @DisplayName("the rules of a context bind to the values they carry")
    void bind_completeRules_yieldsTheirValues() {
        var rules =
                sessionBinding().bind(bindable(ExecutionRulesFixture.rulesWith("game.rules.win-score-threshold", 18)));

        assertThat(rules.winScoreThreshold()).isEqualTo(18);
        assertThat(rules.cardsPerHand()).isEqualTo(5);
        assertThat(rules.actionRoundTimerSeconds(4)).isEqualTo(45);
        assertThat(rules.sealMaxUsesPerGame()).isEqualTo(2);
        assertThat(rules.cardGradeWeights()).hasSize(3);
    }

    @Test
    @DisplayName("a rule the game uses but the context lacks is rejected by its key")
    void bind_missingRule_namesTheKey() {
        var properties = bindable(ExecutionRulesFixture.rulesWithout("game.rules.cards-per-hand"));

        assertThatThrownBy(() -> sessionBinding().bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .extracting(failure -> ((InvalidExecutionRulesException) failure).key())
                .isEqualTo("game.rules.cards-per-hand");
    }

    @Test
    @DisplayName("a missing map rule is rejected by its key")
    void bind_missingMapRule_namesTheKey() {
        var rules = ExecutionRulesFixture.rules();
        rules.keySet().removeIf(key -> key.startsWith("game.rules.card-grade-weights."));
        var properties = bindable(rules);

        assertThatThrownBy(() -> sessionBinding().bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .hasMessageContaining("game.rules.card-grade-weights");
    }

    @Test
    @DisplayName("a value out of range is rejected by its key")
    void bind_valueOutOfRange_namesTheKey() {
        var properties = bindable(ExecutionRulesFixture.rulesWith("game.rules.win-score-threshold", 0));

        assertThatThrownBy(() -> sessionBinding().bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .extracting(failure -> ((InvalidExecutionRulesException) failure).key())
                .isEqualTo("game.rules.win-score-threshold");
    }

    @Test
    @DisplayName("a deal smaller than the hand is rejected")
    void bind_dealSmallerThanHand_isRejected() {
        var properties = bindable(ExecutionRulesFixture.rulesWith("game.rules.cards-per-deal", 4));

        assertThatThrownBy(() -> sessionBinding().bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .hasMessageContaining("cards-per-deal must be greater than or equal to cards-per-hand");
    }

    @Test
    @DisplayName("keys the rules type does not use are ignored")
    void bind_unusedKeys_areIgnored() {
        var properties = bindable(ExecutionRulesFixture.rulesWith("game.rules.probability.amplify-multiplier.II", 2));

        assertThat(sessionBinding().bind(properties).winScoreThreshold()).isEqualTo(20);
    }

    @Test
    @DisplayName("scoring and victory rules bind from their own prefixes")
    void bind_scoringAndVictory_bindFromTheirPrefixes() {
        var properties = bindable(ExecutionRulesFixture.rules());

        var score = new ExecutionRulesBinding<>(ScoreRulesProperties.class, "game.rules.scoring", validator)
                .bind(properties);
        var victory = new ExecutionRulesBinding<>(VictoryRulesProperties.class, "game.rules.victory", validator)
                .bind(properties);

        assertThat(score.pointsDelta(ScoreReason.CHAIN_COMPLETED)).isEqualTo(10);
        assertThat(victory.eraserAnnihilations()).isEqualTo(4);
    }

    @Test
    @DisplayName("a missing score delta is rejected by its key")
    void bind_missingScoreDelta_namesTheKey() {
        var properties =
                bindable(ExecutionRulesFixture.rulesWithout("game.rules.scoring.score-deltas.CHAIN_COMPLETED"));
        var binding = new ExecutionRulesBinding<>(ScoreRulesProperties.class, "game.rules.scoring", validator);

        assertThatThrownBy(() -> binding.bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .hasMessageContaining("CHAIN_COMPLETED");
    }

    @Test
    @DisplayName("scoring rules absent altogether are rejected by their key")
    void bind_noScoreDeltas_namesTheKey() {
        var rules = ExecutionRulesFixture.rules();
        rules.keySet().removeIf(key -> key.startsWith("game.rules.scoring."));
        var properties = bindable(rules);
        var binding = new ExecutionRulesBinding<>(ScoreRulesProperties.class, "game.rules.scoring", validator);

        assertThatThrownBy(() -> binding.bind(properties))
                .isInstanceOf(InvalidExecutionRulesException.class)
                .hasMessageContaining("game.rules.scoring.score-deltas");
    }

    @Test
    @DisplayName("a port is served from the deployment until a context is open, then from the context's rules")
    void servedFrom_followsTheOpenContext() {
        var deployment = sessionBinding().bind(bindable(ExecutionRulesFixture.rules()));
        var active = new AtomicReference<Optional<ExecutionRules.Rules>>(Optional.empty());
        var served =
                ExecutionRulesBinding.servedFrom(SessionGameRulesPort.class, active::get, sessionBinding(), deployment);

        assertThat(served.winScoreThreshold()).isEqualTo(20);

        active.set(Optional.of(rulesOf(18)));
        assertThat(served.winScoreThreshold()).isEqualTo(18);

        active.set(Optional.of(rulesOf(22)));
        assertThat(served.winScoreThreshold()).isEqualTo(22);

        active.set(Optional.empty());
        assertThat(served.winScoreThreshold()).isEqualTo(20);
    }

    @Test
    @DisplayName("the bound rules are reused while the digest is unchanged")
    void servedFrom_sameDigest_bindsOnce() {
        var deployment = sessionBinding().bind(bindable(ExecutionRulesFixture.rules()));
        var counting = spy(sessionBinding());
        var served = ExecutionRulesBinding.servedFrom(
                SessionGameRulesPort.class, () -> Optional.of(rulesOf(18)), counting, deployment);

        served.winScoreThreshold();
        served.cardsPerHand();
        served.maxEras();

        verify(counting, times(1)).bind(anyMap());
    }

    @Test
    @DisplayName("a served port can be used as any object")
    void servedFrom_objectMethods() {
        var deployment = sessionBinding().bind(bindable(ExecutionRulesFixture.rules()));
        var served = ExecutionRulesBinding.servedFrom(
                SessionGameRulesPort.class, Optional::empty, sessionBinding(), deployment);

        assertThat(served).isEqualTo(served).isNotEqualTo(deployment);
        assertThat(served.hashCode()).isEqualTo(System.identityHashCode(served));
        assertThat(served.toString()).contains("SessionGameRulesPort");
    }
}
