package io.github.temporalrift.contracttest;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.infrastructure.adapter.out.persistence.ActionInMemoryRepositories;
import io.github.temporalrift.game.action.infrastructure.adapter.out.persistence.ActionPersistenceAdapters;
import io.github.temporalrift.game.scoring.application.query.PlayerScoreQueryService;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.config.ScoreRulesProperties;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence.ScoringInMemoryRepositories;
import io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence.ScoringPersistenceAdapters;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.infrastructure.adapter.out.persistence.SessionInMemoryRepositories;
import io.github.temporalrift.game.session.infrastructure.adapter.out.persistence.SessionPersistenceAdapters;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryInfrastructure;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.SharedInMemoryRepositories;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.SharedPersistenceAdapters;
import io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence.SimulationInMemoryAdapters;

/** The persistence adapters over the in-memory store; outside the application package so its scan skips it. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableConfigurationProperties(ScoreRulesProperties.class)
@Import({
    InMemoryInfrastructure.class,
    SimulationInMemoryAdapters.class,
    JacksonAutoConfiguration.class,
    ActionPersistenceAdapters.class,
    ActionInMemoryRepositories.class,
    ScoringInMemoryRepositories.class,
    SessionInMemoryRepositories.class,
    SharedInMemoryRepositories.class,
    ScoringPersistenceAdapters.class,
    SessionPersistenceAdapters.class,
    SharedPersistenceAdapters.class,
    PlayerScoreQueryService.class
})
public class InMemoryPersistenceTestConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventSerializer eventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer() {
            @Override
            public Object serialize(Object event) {
                return objectMapper.writeValueAsString(event);
            }

            @Override
            public <T> T deserialize(Object serialized, Class<T> type) {
                return objectMapper.readValue(serialized.toString(), type);
            }
        };
    }

    @Bean
    SessionEventPublisher sessionEventPublisher() {
        return event -> {};
    }
}
