package io.github.temporalrift.game.action.domain.actionround;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;
import io.github.temporalrift.game.shared.SpecialAction;

/**
 * One player's submission for a round. Each variant owns its own structural validation and knows how
 * to represent itself as the round-level "played" event and, where applicable, a private
 * scoring-internal fact. {@link ActionRound} enforces round-level invariants (open, not a duplicate
 * submission) and delegates everything action-specific here — it does not need to know the rules of
 * every card or special action.
 *
 * <p>Validation here is limited to what a submission can verify about itself in isolation (its own
 * fields). Cross-aggregate eligibility (does this player's faction allow this special, are they
 * jammed) is resolved by the command handler before constructing the submission — {@code ActionRound}
 * has no visibility into {@code PlayerState} to verify those itself.
 */
public sealed interface SubmittedAction permits SubmittedAction.CardAction, SubmittedAction.SpecialActionSubmission {

    UUID playerId();

    /** Checks this submission's own structural invariants; throws if violated. */
    void validate();

    /** The round-level event recorded for every submission, regardless of type. */
    Object toPlayedEvent(UUID gameId, int eraNumber, int roundNumber);

    /**
     * A private, scoring-internal fact this submission produces, if any. Never published to Kafka —
     * see {@code ActionRoundEventPublication}.
     */
    Optional<Object> scoringFact(UUID gameId, int eraNumber);

    record CardAction(
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId)
            implements SubmittedAction {

        /** The only card types whose resolution (timeline-service's {@code applyShift}) needs two outcomes. */
        private static final Set<CardType> TWO_OUTCOME_CARD_TYPES = Set.of(CardType.SWING, CardType.COLLIDE);

        @Override
        public void validate() {
            if (cardType == CardType.STABILIZE || cardType == CardType.DETONATE) {
                throw new CardNotEligibleForActionRoundException(cardType);
            }
            if (!TWO_OUTCOME_CARD_TYPES.contains(cardType)) {
                return;
            }
            if (sourceOutcomeId == null) {
                throw InvalidActionTargetException.requiresSourceOutcome(cardType);
            }
            if (targetOutcomeId == null) {
                throw InvalidActionTargetException.requiresTargetOutcome(cardType);
            }
            if (sourceOutcomeId.equals(targetOutcomeId)) {
                throw InvalidActionTargetException.requiresDistinctOutcomes(cardType);
            }
        }

        @Override
        public Object toPlayedEvent(UUID gameId, int eraNumber, int roundNumber) {
            return new CardPlayed(
                    gameId,
                    eraNumber,
                    roundNumber,
                    playerId,
                    cardInstanceId,
                    cardType,
                    targetEventId,
                    sourceOutcomeId,
                    targetOutcomeId);
        }

        @Override
        public Optional<Object> scoringFact(UUID gameId, int eraNumber) {
            return Optional.empty();
        }
    }

    record SpecialActionSubmission(
            UUID playerId,
            Faction faction,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId)
            implements SubmittedAction {

        @Override
        public void validate() {
            switch (specialAction) {
                case RALLY, MOMENTUM -> throw new DeclarationSpecialActionRequiredException(specialAction);
                case FORESIGHT, ANNIHILATE, SEAL, REWRITE, MIMIC -> requireEventAndOutcome();
                case FULFILLMENT -> requireEvent();
                case CORRUPT -> requireOpponent();
                case CASCADE, OBSCURE, THREAD, TAPESTRY, UNRAVEL, EXPOSE -> {
                    // No additional target requirement enforced yet for these special actions.
                }
            }
        }

        private void requireEventAndOutcome() {
            if (targetEventId == null || targetOutcomeId == null) {
                throw InvalidActionTargetException.specialActionRequiresTarget(specialAction);
            }
        }

        private void requireEvent() {
            if (targetEventId == null) {
                throw InvalidActionTargetException.specialActionRequiresTargetEvent(specialAction);
            }
        }

        private void requireOpponent() {
            if (targetPlayerId == null) {
                throw InvalidActionTargetException.specialActionRequiresTargetPlayer(specialAction);
            }
            if (targetPlayerId.equals(playerId)) {
                throw InvalidActionTargetException.corruptCannotTargetSelf();
            }
        }

        @Override
        public Object toPlayedEvent(UUID gameId, int eraNumber, int roundNumber) {
            return new SpecialActionPlayed(
                    gameId,
                    eraNumber,
                    roundNumber,
                    playerId,
                    faction,
                    specialAction,
                    targetEventId,
                    targetOutcomeId,
                    targetPlayerId);
        }

        @Override
        public Optional<Object> scoringFact(UUID gameId, int eraNumber) {
            return switch (specialAction) {
                case FORESIGHT ->
                    Optional.of(new ForesightDeclared(gameId, eraNumber, targetEventId, targetOutcomeId, playerId));
                case ANNIHILATE ->
                    Optional.of(new OutcomeAnnihilated(gameId, eraNumber, targetEventId, targetOutcomeId, playerId));
                default -> Optional.empty();
            };
        }
    }
}
