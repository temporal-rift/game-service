package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.shared.RestAdviceOrder;

@Order(RestAdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = ActionController.class)
class ActionExceptionHandler {

    @ExceptionHandler({RoundNotFoundException.class, PlayerStateNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "404-01");
    }

    @ExceptionHandler(ActionRoundClosedException.class)
    ProblemDetail handleRoundClosed(ActionRoundClosedException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "409-01");
    }

    @ExceptionHandler(DuplicateSubmissionException.class)
    ProblemDetail handleDuplicateSubmission(DuplicateSubmissionException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "409-02");
    }

    @ExceptionHandler(CardNotInHandException.class)
    ProblemDetail handleCardNotInHand(CardNotInHandException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-01");
    }

    @ExceptionHandler(JammedPlayerException.class)
    ProblemDetail handleJammedPlayer(JammedPlayerException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-02");
    }

    @ExceptionHandler(InvalidActionTargetException.class)
    ProblemDetail handleInvalidTarget(InvalidActionTargetException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-03");
    }

    @ExceptionHandler(FactionRequiredException.class)
    ProblemDetail handleFactionRequired(FactionRequiredException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-04");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
