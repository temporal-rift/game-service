package io.github.temporalrift.game.action.infrastructure.adapter.in.rest;

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

@RestControllerAdvice(basePackageClasses = ActionController.class)
class ActionExceptionHandler {

    @ExceptionHandler({RoundNotFoundException.class, PlayerStateNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ActionRoundClosedException.class, DuplicateSubmissionException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
        CardNotInHandException.class,
        FactionRequiredException.class,
        JammedPlayerException.class,
        InvalidActionTargetException.class
    })
    ProblemDetail handleUnprocessable(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }
}
