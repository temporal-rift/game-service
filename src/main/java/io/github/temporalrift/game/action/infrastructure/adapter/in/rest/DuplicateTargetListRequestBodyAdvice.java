package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model.SubmitActionRequest;

/** Validates target-list duplicates before generated binding turns the contract's arrays into sets. */
@ControllerAdvice(basePackageClasses = ActionController.class)
class DuplicateTargetListRequestBodyAdvice implements RequestBodyAdvice {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return SubmitActionRequest.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        var body = inputMessage.getBody().readAllBytes();
        var request = OBJECT_MAPPER.readTree(body);
        rejectDuplicateTargetEventIds(request);
        rejectDuplicateTargetPlayerIds(request);
        return new CachedHttpInputMessage(inputMessage, body);
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private static void rejectDuplicateTargetEventIds(JsonNode request) {
        if (request == null) {
            return;
        }
        var targetEventIds = request.path("targetEventIds");
        if (!targetEventIds.isArray()) {
            return;
        }

        var seen = new HashSet<UUID>();
        for (var targetEventId : targetEventIds) {
            if (!targetEventId.isTextual()) {
                continue;
            }
            try {
                if (!seen.add(UUID.fromString(targetEventId.textValue()))) {
                    throw InvalidActionTargetException.scanRequiresDistinctTargets();
                }
            } catch (IllegalArgumentException _) {
                // Generated UUID binding reports malformed target values through the normal request error contract.
            }
        }
    }

    private static void rejectDuplicateTargetPlayerIds(JsonNode request) {
        if (request == null) {
            return;
        }
        var targetPlayerIds = request.path("targetPlayerIds");
        if (!targetPlayerIds.isArray()) {
            return;
        }

        var seen = new HashSet<UUID>();
        for (var targetPlayerId : targetPlayerIds) {
            if (!targetPlayerId.isTextual()) {
                continue;
            }
            try {
                if (!seen.add(UUID.fromString(targetPlayerId.textValue()))) {
                    throw InvalidActionTargetException.nullifyRequiresDistinctTargets();
                }
            } catch (IllegalArgumentException _) {
                // Generated UUID binding reports malformed target values through the normal request error contract.
            }
        }
    }

    private record CachedHttpInputMessage(HttpInputMessage delegate, byte[] body) implements HttpInputMessage {

        @Override
        public java.io.InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CachedHttpInputMessage(var otherDelegate, var otherBody)
                    && Objects.equals(delegate, otherDelegate)
                    && Arrays.equals(body, otherBody);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate, Arrays.hashCode(body));
        }

        @Override
        public String toString() {
            return "CachedHttpInputMessage[delegate=" + delegate + ", body=" + Arrays.toString(body) + "]";
        }
    }
}
