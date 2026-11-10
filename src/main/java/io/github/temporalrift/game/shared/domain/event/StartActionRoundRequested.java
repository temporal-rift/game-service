package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module's era-advancement saga requests that the action module opens a
 * new action round. Lives in {@code game.shared} - the neutral shared kernel - so referencing it never
 * creates a Spring Modulith module cycle. See {@link EventsDrawn} for the rationale.
 *
 * <p>{@code playerIds} are all participants, who still drive the round's pending list and timer.
 * {@code activePlayerIds} are those who have not abandoned: only the session module knows abandonment, and
 * the action module addresses its per-player round-open facts to them. An event persisted before this field
 * existed has no value and is read as "everyone is active".
 */
public record StartActionRoundRequested(
        UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds, List<UUID> activePlayerIds) {

    public StartActionRoundRequested {
        activePlayerIds = activePlayerIds == null ? playerIds : activePlayerIds;
    }
}
