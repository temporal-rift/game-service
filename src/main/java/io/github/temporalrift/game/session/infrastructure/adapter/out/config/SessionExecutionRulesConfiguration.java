package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRules;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionRulesValidator;
import io.github.temporalrift.game.shared.infrastructure.config.ExecutionRulesBinding;

/** In an isolated simulation deployment the session rules are those of the open execution context. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class SessionExecutionRulesConfiguration {

    @Bean
    @Primary
    SessionGameRulesPort executionSessionRules(
            ExecutionRules active, SessionRulesProperties deployment, Validator validator) {
        return ExecutionRulesBinding.servedFrom(SessionGameRulesPort.class, active, binding(validator), deployment);
    }

    @Bean
    ExecutionRulesValidator sessionExecutionRulesValidator(Validator validator) {
        return binding(validator).validator();
    }

    private static ExecutionRulesBinding<SessionRulesProperties> binding(Validator validator) {
        return new ExecutionRulesBinding<>(SessionRulesProperties.class, "game.rules", validator);
    }
}
