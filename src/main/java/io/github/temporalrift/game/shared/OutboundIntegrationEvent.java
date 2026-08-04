package io.github.temporalrift.game.shared;

import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Durable, serialization-stable event handed from a generated contract producer to the application-owned outbox
 * relay.
 *
 * <p>{@code channel} is a logical AsyncAPI channel, not a Spring Cloud Stream binding or Kafka topic. The relay owns
 * the one-to-one mapping from that channel to the application-local output binding.
 */
public record OutboundIntegrationEvent(
        String channel, String eventType, String key, JsonNode payload, Map<String, Object> headers) {}
