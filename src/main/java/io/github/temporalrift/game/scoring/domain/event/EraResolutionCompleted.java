package io.github.temporalrift.game.scoring.domain.event;

import java.util.List;
import java.util.UUID;

/** Durable terminal-resolution barrier received from the timeline service. */
public record EraResolutionCompleted(UUID gameId, int eraNumber, List<TerminalResolution> terminalResolutions) {

    public record TerminalResolution(
            UUID eventId, int revealIndex, TerminalState terminalState, UUID winningOutcomeId) {}

    public enum TerminalState {
        OUTCOME_APPLIED,
        CASCADED,
        STALLED
    }
}
