package io.github.temporalrift.game.session.infrastructure.adapter.in.rest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.DisconnectedPlayersException;
import io.github.temporalrift.game.session.domain.lobby.LobbyAlreadyStartedException;
import io.github.temporalrift.game.session.domain.lobby.LobbyFullException;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.NotEnoughPlayersException;
import io.github.temporalrift.game.session.domain.lobby.NotLobbyHostException;
import io.github.temporalrift.game.session.domain.lobby.PlayerAlreadyInLobbyException;
import io.github.temporalrift.game.session.domain.lobby.PlayerNotInLobbyException;
import io.github.temporalrift.game.shared.RestAdviceOrder;

@Order(RestAdviceOrder.MODULE)
@RestControllerAdvice(basePackageClasses = SessionController.class)
class SessionExceptionHandler {

    @ExceptionHandler(LobbyNotFoundException.class)
    ProblemDetail handleLobbyNotFound(LobbyNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "404-01");
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail handleGameNotFound(GameNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "404-02");
    }

    @ExceptionHandler(PlayerNotInLobbyException.class)
    ProblemDetail handlePlayerNotInLobby(PlayerNotInLobbyException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "404-03");
    }

    @ExceptionHandler(LobbyAlreadyStartedException.class)
    ProblemDetail handleLobbyAlreadyStarted(LobbyAlreadyStartedException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "409-01");
    }

    @ExceptionHandler(PlayerAlreadyInLobbyException.class)
    ProblemDetail handlePlayerAlreadyInLobby(PlayerAlreadyInLobbyException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "409-02");
    }

    @ExceptionHandler(DisconnectedPlayersException.class)
    ProblemDetail handleDisconnectedPlayers(DisconnectedPlayersException ex) {
        var problemDetail = problem(HttpStatus.CONFLICT, ex.getMessage(), "409-03");
        problemDetail.setProperty("disconnectedPlayerIds", ex.disconnectedPlayerIds());
        return problemDetail;
    }

    @ExceptionHandler(LobbyFullException.class)
    ProblemDetail handleLobbyFull(LobbyFullException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-01");
    }

    @ExceptionHandler(NotEnoughPlayersException.class)
    ProblemDetail handleNotEnoughPlayers(NotEnoughPlayersException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "422-02");
    }

    @ExceptionHandler(NotLobbyHostException.class)
    ProblemDetail handleNotHost(NotLobbyHostException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage(), "403-02");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code);
        return problemDetail;
    }
}
