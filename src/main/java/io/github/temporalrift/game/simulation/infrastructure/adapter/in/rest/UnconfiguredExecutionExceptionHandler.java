package io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.shared.infrastructure.adapter.in.rest.ProblemDetails;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.rest.RestAdviceOrder;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;

/** Gameplay of an isolated deployment cannot start before its execution is configured, on any route. */
@Order(RestAdviceOrder.MODULE)
@RestControllerAdvice
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class UnconfiguredExecutionExceptionHandler {

    @ExceptionHandler(ExecutionNotConfiguredException.class)
    ProblemDetail handleNotConfigured(ExecutionNotConfiguredException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage(), "EXECUTION_NOT_CONFIGURED");
    }
}
