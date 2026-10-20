package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.kafka.support.converter.RecordMessageConverter;

import io.github.temporalrift.game.shared.domain.port.out.GameScope;

/** Wires the inbound delivery entry that stands in for Kafka listener containers under the in-memory profile. */
@Configuration(proxyBeanMethods = false)
@Profile("in-memory")
class SimulationMessagingConfiguration {

    @Bean
    static KafkaListenerRegistry kafkaListenerRegistry(Environment environment) {
        return new KafkaListenerRegistry(environment);
    }

    @Bean("simulationEventDelivery")
    SimulationEventDelivery simulationEventDelivery(
            KafkaListenerRegistry registry, RecordMessageConverter converter, GameScope scope) {
        return new SimulationEventDelivery(registry, converter, scope);
    }
}
