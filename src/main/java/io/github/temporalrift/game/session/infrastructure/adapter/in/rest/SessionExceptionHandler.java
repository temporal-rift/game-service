package io.github.temporalrift.game.session.infrastructure.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyAlreadyStartedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyFullException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;
import io.github.temporalrift.game.session.domain.lobby.PlayerNotInLobbyException;

@RestControllerAdvice(basePackageClasses = SessionController.class)
class SessionExceptionHandler {

    @ExceptionHandler({LobbyNotFoundException.class, PlayerNotInLobbyException.class, GameNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LobbyAlreadyStartedException.class)
    ProblemDetail handleConflict(LobbyAlreadyStartedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({LobbyFullException.class, NotEnoughPlayersException.class})
    ProblemDetail handleUnprocessable(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(NotLobbyHostException.class)
    ProblemDetail handleForbidden(NotLobbyHostException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}
