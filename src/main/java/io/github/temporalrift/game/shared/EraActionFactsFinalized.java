package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the action module's saga raises this once the era's final action round closes,
 * bundling every scoring-relevant action fact, including the persisted Activist declarations, after
 * the final round closes. Round-three facts come straight from the round's own already-complete
 * {@code submittedActions} list; declarations are read from their era-scoped aggregate. In-process
 * only — never published to Kafka, and the scoring module never needs to know the era's round cap to
 * consume it.
 *
 * <p>Unlike the per-submission {@link ForesightDeclared}/{@link OutcomeAnnihilated} events — dispatched
 * independently and asynchronously, with no ordering guarantee relative to each other or to this event —
 * this bundle lets scoring redundantly (and idempotently) re-apply the final round's facts and mark the
 * era's action facts ready in the very same listener invocation, closing the race where the final
 * round's own projection could otherwise still be in flight when scoring decides the era is ready.
 */
public record EraActionFactsFinalized(
        UUID gameId,
        int eraNumber,
        List<ForesightFact> foresightFacts,
        List<AnnihilationFact> annihilationFacts,
        List<ExposeFact> exposeFacts,
        List<ActivistDeclarationFact> activistDeclarationFacts) {

    public EraActionFactsFinalized(
            UUID gameId,
            int eraNumber,
            List<ForesightFact> foresightFacts,
            List<AnnihilationFact> annihilationFacts,
            List<ExposeFact> exposeFacts) {
        this(gameId, eraNumber, foresightFacts, annihilationFacts, exposeFacts, List.of());
    }

    public EraActionFactsFinalized(
            UUID gameId, int eraNumber, List<ForesightFact> foresightFacts, List<AnnihilationFact> annihilationFacts) {
        this(gameId, eraNumber, foresightFacts, annihilationFacts, List.of(), List.of());
    }

    public record ForesightFact(UUID eventId, UUID outcomeId, UUID playerId) {}

    public record AnnihilationFact(UUID eventId, UUID outcomeId, UUID playerId) {}

    public record ExposeFact(UUID activistPlayerId, UUID targetPlayerId) {}

    public record ActivistDeclarationFact(
            UUID playerId, SpecialAction mode, UUID targetEventId, UUID targetOutcomeId) {}
}
