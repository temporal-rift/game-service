package io.github.temporalrift.game.shared.infrastructure.config;

/** The in-memory profile was activated without what it requires from the host. */
public final class InMemoryProfileException extends IllegalStateException {

    private final String requirement;

    InMemoryProfileException(String requirement, String message) {
        super(message);
        this.requirement = requirement;
    }

    String requirement() {
        return requirement;
    }
}
