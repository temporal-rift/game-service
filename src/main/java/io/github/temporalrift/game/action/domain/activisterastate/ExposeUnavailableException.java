package io.github.temporalrift.game.action.domain.activisterastate;

/** Raised when Expose is attempted outside Action Round 2. */
public final class ExposeUnavailableException extends RuntimeException {

    public ExposeUnavailableException() {
        super("Expose is available only during Action Round 2");
    }
}
