package io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.shared.ProblemDetails;
import io.github.temporalrift.game.shared.RestAdviceOrder;

@Order(RestAdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = ScoringController.class)
class ScoringExceptionHandler {

    @ExceptionHandler(ScoringGameNotFoundException.class)
    ProblemDetail handleNotFound(ScoringGameNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ex.getMessage(), "404-01");
    }
}
