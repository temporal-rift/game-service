package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

/** The ordered terminal resolutions emitted after a timeline era is fully resolved. */
public record EraResolutionCompleted(UUID gameId, int eraNumber, List<TerminalResolution> terminalResolutions) {

    public record TerminalResolution(
            UUID eventId, int revealIndex, TerminalState terminalState, UUID winningOutcomeId) {}

    public enum TerminalState {
        OUTCOME_APPLIED,
        CASCADED
    }
}
