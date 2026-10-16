package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Stops startup, before any bean is built, when the in-memory profile lacks its simulation switch or event sink. */
@Configuration(proxyBeanMethods = false)
@Profile("in-memory")
final class InMemoryProfileGuard {

    private InMemoryProfileGuard() {}

    static final String SIMULATION_PROPERTY = "game.simulation.enabled";
    static final String EVENT_SINK_BEAN = "simulationEventSink";

    @Bean
    static BeanFactoryPostProcessor inMemoryProfileRequirements(Environment environment) {
        return beanFactory -> {
            if (!Boolean.TRUE.equals(environment.getProperty(SIMULATION_PROPERTY, Boolean.class))) {
                throw new InMemoryProfileException(
                        SIMULATION_PROPERTY, "Profile 'in-memory' requires " + SIMULATION_PROPERTY + "=true");
            }
            if (!beanFactory.containsBean(EVENT_SINK_BEAN)) {
                throw new InMemoryProfileException(
                        EVENT_SINK_BEAN,
                        "Profile 'in-memory' requires the host to supply a bean named '" + EVENT_SINK_BEAN + "'");
            }
        };
    }
}
