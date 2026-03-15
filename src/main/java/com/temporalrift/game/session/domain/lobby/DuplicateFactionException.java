package com.temporalrift.game.session.domain.lobby;

public class DuplicateFactionException extends RuntimeException {
    public DuplicateFactionException() {
        super("Two players cannot share a faction");
    }
}

