package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Explains how to satisfy the in-memory profile's requirements. */
public final class InMemoryProfileFailureAnalyzer extends AbstractFailureAnalyzer<InMemoryProfileException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InMemoryProfileException cause) {
        var action = InMemoryProfileGuard.SIMULATION_PROPERTY.equals(cause.requirement())
                ? "Set " + InMemoryProfileGuard.SIMULATION_PROPERTY
                        + "=true, or do not activate the 'in-memory' profile."
                : "Start the application under a parent context that defines a Consumer<Map<String, Object>>"
                        + " bean named '"
                        + InMemoryProfileGuard.EVENT_SINK_BEAN + "'.";
        return new FailureAnalysis(cause.getMessage(), action, cause);
    }
}
