package com.temporalrift.game.session.domain.lobby;

public class HostCannotLeaveException extends RuntimeException {

    public HostCannotLeaveException() {
        super("Host cannot leave without transferring or closing lobby");
    }
}
