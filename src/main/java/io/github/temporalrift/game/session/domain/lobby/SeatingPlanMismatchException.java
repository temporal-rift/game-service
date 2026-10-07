package io.github.temporalrift.game.session.domain.lobby;

public class SeatingPlanMismatchException extends RuntimeException {

    public SeatingPlanMismatchException() {
        super("The lobby players do not match the pre-agreed seating plan");
    }
}
