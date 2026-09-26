package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.ActionFamily;
import io.github.temporalrift.game.shared.domain.model.CardCategory;

public record RoundSummaryPublished(UUID gameId, int eraNumber, int roundNumber, List<ActionSummary> actionSummaries)
        implements ActionEventPayload {

    /** Public view of one player's action: {@code actionCategory} is null for specials, both are null for skips. */
    public record ActionSummary(
            UUID playerId, CardCategory actionCategory, ActionFamily actionFamily, boolean skipped) {}
}
