package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.JsonKafkaHeaderMapper;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka consumer message conversion.
 *
 * <p>Consumers deserialize the raw record value with {@code ByteArrayDeserializer} and rely on a
 * {@link ByteArrayJacksonJsonMessageConverter} to turn the JSON bytes into each {@code @KafkaListener}
 * method's parameter type — the typed event payload for {@code timeline.events} (whose envelope metadata
 * travels in the record headers, see {@code TimelineEventEnvelope}), or {@code InboundEnvelope} for the
 * still-envelope-shaped {@code game.commands}. Spring Boot wires a single {@code RecordMessageConverter}
 * bean into the default listener container factory. It must be built from the application's configured
 * {@link JsonMapper} so it inherits JSR-310 support for the {@code Instant}/{@code OffsetDateTime} body
 * fields — without this the listener method cannot be invoked and every inbound record is routed straight
 * to the dead-letter topic.
 *
 * <p>Every {@code timeline.events} header (including {@code occurredAt}) travels as a plain String —
 * producers call {@code .toString()}/{@code String.valueOf()} on each value before sending, so the header
 * mapper's own type-tagging records {@code java.lang.String}, which is trusted by default. No extra trusted
 * package is needed.
 */
@Configuration
class KafkaConsumerConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter(JsonMapper jsonMapper) {
        var converter = new ByteArrayJacksonJsonMessageConverter(jsonMapper);
        converter.setHeaderMapper(new JsonKafkaHeaderMapper());
        return converter;
    }
}
