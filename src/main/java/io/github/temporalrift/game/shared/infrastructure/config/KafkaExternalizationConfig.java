package io.github.temporalrift.game.shared.infrastructure.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.zenwave360.modulith.events.scs.config.EnableSpringCloudStreamEventExternalization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Externalizes the ZenWave-generated producers' {@link Message} events to Kafka.
 *
 * <p>{@code @EnableSpringCloudStreamEventExternalization} wires the {@code spring-modulith-events-scs}
 * infrastructure — most importantly the {@code Message}-aware serializer that lets Spring Modulith
 * persist a {@link Message} into the transactional outbox (Modulith's default serializer loses the
 * generic payload type). Without it, the outbox row is silently never written.
 *
 * <p>That serializer autowires a Jackson 2 ({@code com.fasterxml}) {@link JsonMapper}. This app
 * otherwise runs on Jackson 3 ({@code tools.jackson}, Spring Boot 4), so no such bean exists by
 * default — {@link #scsOutboxObjectMapper} provides one (with the JSR-310 time module for the
 * {@code occurredAt}/date-time fields). It is a distinct type from the app's Jackson 3 mapper, so it
 * only affects outbox serialization.
 *
 * <p>{@link #gameEventsExternalizationConfiguration} ({@code @Primary}) overrides the library's default
 * routing, which sends to the per-event binding name. Every session/action/scoring event goes to the
 * single {@code game.events} topic (per event-schema.md), and Spring Cloud Stream's {@code destination}
 * binding property cannot be defaulted globally, so the topic is pinned here rather than configured per
 * generated binding. The gameId partition key is applied by the binder via
 * {@code spring.cloud.stream.kafka.default.producer.messageKeyExpression=headers['gameId']}, so the
 * routing target needs no key. Serialized externalization preserves publication order only within a
 * single service instance; ordering across replicas requires a separate single-writer or ownership
 * guarantee.
 */
@Configuration
@EnableSpringCloudStreamEventExternalization
class KafkaExternalizationConfig {

    private static final String SCS_DESTINATION_HEADER = "spring.cloud.stream.sendto.destination";
    private static final String GAME_EVENTS_TOPIC = "game.events";

    @Bean
    com.fasterxml.jackson.databind.ObjectMapper scsOutboxObjectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    @Bean
    @Primary
    EventExternalizationConfiguration gameEventsExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(KafkaExternalizationConfig::isExternalizable)
                .route(
                        Message.class,
                        message -> RoutingTarget.forTarget(GAME_EVENTS_TOPIC).withoutKey())
                .serializeExternalization(true)
                .build();
    }

    private static boolean isExternalizable(Object event) {
        return event instanceof Message<?> message
                && message.getHeaders().get(SCS_DESTINATION_HEADER, String.class) != null;
    }
}
