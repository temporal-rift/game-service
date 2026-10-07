package io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.shared.infrastructure.adapter.in.rest.ProblemDetails;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.rest.RestAdviceOrder;
import io.github.temporalrift.game.simulation.domain.execution.ClockRegressionException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextConflictException;
import io.github.temporalrift.game.simulation.domain.execution.IdempotencyConflictException;
import io.github.temporalrift.game.simulation.domain.execution.InvalidClockAdvanceException;
import io.github.temporalrift.game.simulation.domain.execution.InvalidExecutionContextException;
import io.github.temporalrift.game.simulation.domain.execution.StaleExecutionRevisionException;

@Order(RestAdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = SimulationControlController.class)
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class SimulationControlExceptionHandler {

    private static final String CLOCK_PATH_SUFFIX = "/clock";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleMalformedBody(Exception ex, HttpServletRequest request) {
        if (request.getRequestURI().endsWith(CLOCK_PATH_SUFFIX)) {
            return ProblemDetails.of(HttpStatus.BAD_REQUEST, "The clock advance is malformed", "INVALID_CLOCK_ADVANCE");
        }
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "The execution context is malformed", "INVALID_EXECUTION_CONTEXT");
    }

    @ExceptionHandler(InvalidExecutionContextException.class)
    ProblemDetail handleInvalidContext(InvalidExecutionContextException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_EXECUTION_CONTEXT");
    }

    @ExceptionHandler(InvalidClockAdvanceException.class)
    ProblemDetail handleInvalidAdvance(InvalidClockAdvanceException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_CLOCK_ADVANCE");
    }

    @ExceptionHandler(ExecutionContextConflictException.class)
    ProblemDetail handleContextConflict(ExecutionContextConflictException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage(), "EXECUTION_CONTEXT_CONFLICT");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage(), "IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(StaleExecutionRevisionException.class)
    ProblemDetail handleStaleRevision(StaleExecutionRevisionException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage(), "STALE_EXECUTION_REVISION");
    }

    @ExceptionHandler(ClockRegressionException.class)
    ProblemDetail handleClockRegression(ClockRegressionException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "CLOCK_REGRESSION");
    }
}
