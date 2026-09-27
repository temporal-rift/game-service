package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Cross-module event: the action module's saga raises this once the era's final action round closes,
 * bundling every scoring-relevant action fact, including the persisted Activist declarations, after
 * the final round closes. Round-three facts come straight from the round's own already-complete
 * {@code submittedActions} list; declarations are read from their era-scoped aggregate. In-process
 * only — never published to Kafka, and the scoring module never needs to know the era's round cap to
 * consume it.
 *
 * <p>Unlike the per-submission {@link ForesightDeclared} event — dispatched independently and
 * asynchronously, with no ordering guarantee relative to this event —
 * this bundle lets scoring redundantly (and idempotently) re-apply the final round's facts and mark the
 * era's action facts ready in the very same listener invocation, closing the race where the final
 * round's own projection could otherwise still be in flight when scoring decides the era is ready.
 * {@code disclosedPlayerIds} lists the players a public fact named as acting for their faction this era
 * (declarations of record and revealed Exposes), so they are recorded before the era's scores are evaluated.
 */
public record EraActionFactsFinalized(
        UUID gameId,
        int eraNumber,
        List<ForesightFact> foresightFacts,
        List<ExposeFact> exposeFacts,
        List<ActivistDeclarationFact> activistDeclarationFacts,
        List<RevisionistFact> revisionistFacts,
        List<FulfillmentFact> fulfillmentFacts,
        List<CorruptCorrelationFact> corruptCorrelationFacts,
        List<UUID> disclosedPlayerIds) {

    public EraActionFactsFinalized(
            UUID gameId,
            int eraNumber,
            List<ForesightFact> foresightFacts,
            List<ExposeFact> exposeFacts,
            List<ActivistDeclarationFact> activistDeclarationFacts,
            List<RevisionistFact> revisionistFacts,
            List<FulfillmentFact> fulfillmentFacts,
            List<CorruptCorrelationFact> corruptCorrelationFacts) {
        this(
                gameId,
                eraNumber,
                foresightFacts,
                exposeFacts,
                activistDeclarationFacts,
                revisionistFacts,
                fulfillmentFacts,
                corruptCorrelationFacts,
                List.of());
    }

    public EraActionFactsFinalized(
            UUID gameId,
            int eraNumber,
            List<ForesightFact> foresightFacts,
            List<ExposeFact> exposeFacts,
            List<ActivistDeclarationFact> activistDeclarationFacts,
            List<RevisionistFact> revisionistFacts,
            List<FulfillmentFact> fulfillmentFacts) {
        this(
                gameId,
                eraNumber,
                foresightFacts,
                exposeFacts,
                activistDeclarationFacts,
                revisionistFacts,
                fulfillmentFacts,
                List.of());
    }

    public EraActionFactsFinalized(
            UUID gameId, int eraNumber, List<ForesightFact> foresightFacts, List<ExposeFact> exposeFacts) {
        this(gameId, eraNumber, foresightFacts, exposeFacts, List.of(), List.of(), List.of());
    }

    public EraActionFactsFinalized(
            UUID gameId,
            int eraNumber,
            List<ForesightFact> foresightFacts,
            List<ExposeFact> exposeFacts,
            List<ActivistDeclarationFact> activistDeclarationFacts) {
        this(gameId, eraNumber, foresightFacts, exposeFacts, activistDeclarationFacts, List.of(), List.of());
    }

    public EraActionFactsFinalized(
            UUID gameId,
            int eraNumber,
            List<ForesightFact> foresightFacts,
            List<ExposeFact> exposeFacts,
            List<ActivistDeclarationFact> activistDeclarationFacts,
            List<RevisionistFact> revisionistFacts) {
        this(gameId, eraNumber, foresightFacts, exposeFacts, activistDeclarationFacts, revisionistFacts, List.of());
    }

    public record ForesightFact(UUID eventId, UUID outcomeId, UUID playerId) {}

    public record ExposeFact(UUID activistPlayerId, UUID targetPlayerId) {}

    public record ActivistDeclarationFact(
            UUID playerId, SpecialAction mode, UUID targetEventId, UUID targetOutcomeId) {}

    /**
     * Private action-to-scoring attribution; it is intentionally never sent through Kafka.
     *
     * <p>A player starts the era with no preference. The latest {@code REWRITE} in the era is that
     * player's single declaration and replaces any earlier one; with no {@code REWRITE} there is no
     * eligible preference to score.
     */
    public record RevisionistFact(UUID playerId, SpecialAction action, UUID targetEventId, UUID targetOutcomeId) {}

    /** Private action-to-scoring attribution; it is intentionally never sent through Kafka. */
    public record FulfillmentFact(UUID playerId, UUID targetEventId) {}

    /**
     * A candidate Eraser Corrupt correlated to the target's probability-shift card, within the same round
     * as both submissions. {@code tookEffect} is resolved later, outside this bundle — see
     * {@code CorruptCorrelationFact} in the scoring module and {@code EraScoreEvaluator.eraserDecisions}.
     */
    public record CorruptCorrelationFact(
            UUID corruptingPlayerId,
            UUID targetPlayerId,
            UUID cardInstanceId,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {}
}
