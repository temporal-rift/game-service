package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.domain.model.InvalidExecutionRulesException;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;
import io.github.temporalrift.game.shared.infrastructure.config.ExecutionRulesFixture;
import io.github.temporalrift.game.simulation.domain.execution.EffectiveRules;

class SessionExecutionRulesConfigurationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final SessionExecutionRulesConfiguration configuration = new SessionExecutionRulesConfiguration();

    private static ExecutionRules contextWith(Map<String, Object> rules) {
        var effective = new EffectiveRules(rules);
        var open = Optional.of(new ExecutionRules.Rules(effective.digest(), effective.bindable()));
        return () -> open;
    }

    @Test
    @DisplayName("with no context open, the deployment's victory rules apply")
    void victoryRules_noContext_servesTheDeploymentValue() {
        ExecutionRules none = Optional::empty;
        var deployment = new SessionVictoryRulesProperties(true, true);

        var served = configuration.executionSessionVictoryRules(none, deployment, validator);

        assertThat(served.normalVictory()).isTrue();
        assertThat(served.scoreTiebreak()).isTrue();
    }

    @Test
    @DisplayName("an open context that switches normal victory off is served instead of the deployment")
    void victoryRules_contextSwitchesNormalVictoryOff_servesTheContextValue() {
        var active = contextWith(ExecutionRulesFixture.rulesWith("game.rules.victory.normal-victory", false));
        var deployment = new SessionVictoryRulesProperties(true, true);

        var served = configuration.executionSessionVictoryRules(active, deployment, validator);

        assertThat(served.normalVictory()).isFalse();
    }

    @Test
    @DisplayName("an open context that omits the switch keeps normal victory on")
    void victoryRules_contextOmitsTheSwitch_keepsNormalVictoryOn() {
        var active = contextWith(ExecutionRulesFixture.rules());
        var deployment = new SessionVictoryRulesProperties(true, false);

        var served = configuration.executionSessionVictoryRules(active, deployment, validator);

        assertThat(served.normalVictory()).isTrue();
    }

    @Test
    @DisplayName("an open context's session rules are served instead of the deployment's")
    void sessionRules_contextOpen_servesTheContextValue() {
        var active = contextWith(ExecutionRulesFixture.rulesWith("game.rules.win-score-threshold", 18));

        var served = configuration.executionSessionRules(active, null, validator);

        assertThat(served.winScoreThreshold()).isEqualTo(18);
    }

    @Test
    @DisplayName("the session and victory validators accept complete rules and name a missing session rule")
    void validators_checkTheRulesTheModuleUses() {
        var sessionValidator = configuration.sessionExecutionRulesValidator(validator);
        var victoryValidator = configuration.sessionVictoryExecutionRulesValidator(validator);
        var complete = new EffectiveRules(ExecutionRulesFixture.rules()).bindable();
        var withoutHand =
                new EffectiveRules(ExecutionRulesFixture.rulesWithout("game.rules.cards-per-hand")).bindable();

        sessionValidator.validate(complete);
        victoryValidator.validate(complete);

        assertThatThrownBy(() -> sessionValidator.validate(withoutHand))
                .isInstanceOf(InvalidExecutionRulesException.class);
    }
}
