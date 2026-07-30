package io.github.temporalrift.game.action.domain.activisterastate;

/** Raised when an Activist attempts to use Expose more than once in the same era. */
public final class ExposeAlreadyRecordedException extends RuntimeException {

    public ExposeAlreadyRecordedException() {
        super("Expose was already recorded for this Activist in the current era");
    }
}
