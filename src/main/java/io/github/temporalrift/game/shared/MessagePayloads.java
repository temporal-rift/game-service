package io.github.temporalrift.game.shared;

import org.springframework.messaging.Message;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a raw {@code Message} body into a target payload type.
 *
 * <p>With a {@code Message<Object>} listener parameter, Spring Kafka's message converter has no concrete target
 * type to convert the body to, so {@code message.getPayload()} stays the undeserialized {@code byte[]} record value
 * at runtime — {@code ObjectMapper#convertValue} on a {@code byte[]} source treats it as base64-encoded binary, not
 * JSON text, and fails. Real records need {@code readValue}; already-typed payloads (built directly as a record via
 * {@code MessageBuilder}) need {@code convertValue}.
 */
public final class MessagePayloads {

    private MessagePayloads() {}

    public static <T> T read(ObjectMapper objectMapper, Message<?> message, Class<T> type) {
        var payload = message.getPayload();
        return payload instanceof byte[] bytes
                ? objectMapper.readValue(bytes, type)
                : objectMapper.convertValue(payload, type);
    }
}
