package io.github.temporalrift.game.simulation.domain.port.out;

public interface OutboxProbe {

    int pendingPublications();
}
