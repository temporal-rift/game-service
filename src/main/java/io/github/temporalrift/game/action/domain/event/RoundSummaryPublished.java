package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

public record RoundSummaryPublished(UUID gameId, int eraNumber, int roundNumber, List<ActionSummary> actionSummaries)
        implements ActionEventPayload {

    public record ActionSummary(UUID playerId, String actionCategory, String actionFamily, boolean skipped) {}
}
