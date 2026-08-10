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
 * <p>{@code occurredAt} travels as a non-String ({@code Instant}) header, which the header mapper only
 * reconstructs for packages it trusts — {@code java.time} is not trusted by default.
 * {@link org.springframework.kafka.support.converter.MessagingMessageConverter} (the converter's superclass)
 * builds its own default {@link JsonKafkaHeaderMapper} internally unless one is explicitly set via
 * {@code setHeaderMapper} — a separately declared {@code KafkaHeaderMapper} bean is never wired in on its own,
 * so the trusted-package configuration must be set directly on this converter.
 */
@Configuration
class KafkaConsumerConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter(JsonMapper jsonMapper) {
        var converter = new ByteArrayJacksonJsonMessageConverter(jsonMapper);
        var headerMapper = new JsonKafkaHeaderMapper();
        // Exact package-name match, no wildcard support (JsonKafkaHeaderMapper#trusted does
        // packageName.equals(trustedPackage)) — "java.time.*" silently never matches.
        headerMapper.addTrustedPackages("java.time");
        converter.setHeaderMapper(headerMapper);
        return converter;
    }
}
