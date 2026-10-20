package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.github.temporalrift.game.scoring.domain.port.out.ScoreRulesPort;
import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRulesValidator;
import io.github.temporalrift.game.shared.infrastructure.config.ExecutionRulesBinding;

/** In an isolated simulation deployment the scoring and victory rules are those of the open execution context. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class ScoringExecutionRulesConfiguration {

    @Bean
    @Primary
    ScoreRulesPort executionScoreRules(ExecutionRules active, ScoreRulesProperties deployment, Validator validator) {
        return ExecutionRulesBinding.servedFrom(ScoreRulesPort.class, active, scoreBinding(validator), deployment);
    }

    @Bean
    @Primary
    VictoryRulesPort executionVictoryRules(
            ExecutionRules active, VictoryRulesProperties deployment, Validator validator) {
        return ExecutionRulesBinding.servedFrom(VictoryRulesPort.class, active, victoryBinding(validator), deployment);
    }

    @Bean
    ExecutionRulesValidator scoreExecutionRulesValidator(Validator validator) {
        return scoreBinding(validator).validator();
    }

    @Bean
    ExecutionRulesValidator victoryExecutionRulesValidator(Validator validator) {
        return victoryBinding(validator).validator();
    }

    private static ExecutionRulesBinding<ScoreRulesProperties> scoreBinding(Validator validator) {
        return new ExecutionRulesBinding<>(ScoreRulesProperties.class, "game.rules.scoring", validator);
    }

    private static ExecutionRulesBinding<VictoryRulesProperties> victoryBinding(Validator validator) {
        return new ExecutionRulesBinding<>(VictoryRulesProperties.class, "game.rules.victory", validator);
    }
}
