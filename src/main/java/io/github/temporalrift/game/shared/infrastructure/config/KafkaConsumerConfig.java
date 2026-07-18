package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka consumer message conversion.
 *
 * <p>Consumers deserialize the raw record value with {@code ByteArrayDeserializer} and rely on a
 * {@link ByteArrayJacksonJsonMessageConverter} to turn the JSON bytes into each {@code @KafkaListener}
 * method's parameter type (e.g. {@code InboundEnvelope}). Spring Boot wires a single
 * {@code RecordMessageConverter} bean into the default listener container factory. It must be built
 * from the application's configured {@link JsonMapper} so it inherits JSR-310 support for the
 * {@code Instant}/{@code OffsetDateTime} fields on the envelope — without this the listener method
 * cannot be invoked and every inbound record is routed straight to the dead-letter topic.
 */
@Configuration
class KafkaConsumerConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter(JsonMapper jsonMapper) {
        return new ByteArrayJacksonJsonMessageConverter(jsonMapper);
    }
}
