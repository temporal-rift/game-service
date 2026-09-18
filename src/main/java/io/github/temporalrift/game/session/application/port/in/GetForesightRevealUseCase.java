package io.github.temporalrift.game.session.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetForesightRevealUseCase {

    /**
     * Returns the caller's own preview for the era, or empty when none was revealed to them.
     * Never reveals another viewer's preview.
     */
    Optional<Result> handle(Query query);

    record Query(UUID gameId, int eraNumber, UUID callerPlayerId) {}

    record Result(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            int nextEraNumber,
            List<EventPreview> revealedEvents,
            String emptyReason) {}

    record EventPreview(UUID catalogEventId, String title, List<OutcomePreview> outcomes) {}

    record OutcomePreview(UUID catalogOutcomeId, String description) {}
}
