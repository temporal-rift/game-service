package io.github.temporalrift.game.simulation.domain.port.out;

public interface GameplayDiscard {

    /**
     * Removes all gameplay state of the deployment: games, sagas, timers, continuations, outbox records and the
     * claims of handled events. Only the simulation control state is kept.
     */
    void discardAll();
}
