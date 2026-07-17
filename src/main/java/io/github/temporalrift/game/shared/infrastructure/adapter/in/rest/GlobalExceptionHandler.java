package io.github.temporalrift.game.shared.infrastructure.adapter.in.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.temporalrift.game.shared.RestAdviceOrder;

/**
 * Last-resort advice: anything not mapped by a module advice becomes a sanitized 500 instead of the
 * container default, so internal exception details never depend on framework configuration.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so standard Spring MVC exceptions (malformed
 * body, unsupported media type, missing path variable, ...) keep their proper 4xx mappings instead
 * of being swallowed by the {@code Exception} catch-all below.
 */
@Order(RestAdviceOrder.GLOBAL_FALLBACK)
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached the REST layer", ex);
        // Deliberately not ex.getMessage(): unexpected exceptions may carry internal details.
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
