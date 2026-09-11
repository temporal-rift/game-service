package io.github.temporalrift.game.action.domain.actionround;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.CardGrade;
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
 * <p>Validation here is limited to what a submission can verify about itself and the round context
 * {@link ActionRound} passes in (its own fields, plus the era/round number). Cross-aggregate eligibility
 * (does this player's faction allow this special, are they jammed) is resolved by the command handler
 * before constructing the submission — {@code ActionRound} has no visibility into {@code PlayerState} to
 * verify those itself.
 */
public sealed interface SubmittedAction permits SubmittedAction.CardAction, SubmittedAction.SpecialActionSubmission {

    UUID playerId();

    /**
     * Checks this submission's own structural invariants and, for card actions, its round-scoped
     * playability given the round the submission was made in; throws if violated.
     */
    void validate(int eraNumber, int roundNumber);

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
            CardGrade grade,
            UUID targetEventId,
            List<UUID> targetEventIds,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId)
            implements SubmittedAction {

        /** The only card types whose resolution (timeline-service's {@code applyShift}) needs two outcomes. */
        private static final Set<CardType> TWO_OUTCOME_CARD_TYPES = Set.of(CardType.SWING, CardType.COLLIDE);

        /** No following round or remaining era action left to use these cards' intel/effect in Round 3. */
        private static final Set<CardType> ROUND_THREE_INELIGIBLE_CARD_TYPES =
                Set.of(CardType.JAM, CardType.SCAN, CardType.INTERCEPT);

        /** These disrupt another player directly rather than a future event. */
        private static final Set<CardType> PLAYER_TARGETING_CARD_TYPES =
                Set.of(CardType.NULLIFY, CardType.REDIRECT, CardType.AMPLIFY, CardType.JAM, CardType.INTERCEPT);

        public CardAction {
            targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
        }

        public CardAction(
                UUID playerId,
                UUID cardInstanceId,
                CardType cardType,
                CardGrade grade,
                UUID targetEventId,
                UUID sourceOutcomeId,
                UUID targetOutcomeId,
                UUID targetPlayerId) {
            this(
                    playerId,
                    cardInstanceId,
                    cardType,
                    grade,
                    targetEventId,
                    null,
                    sourceOutcomeId,
                    targetOutcomeId,
                    targetPlayerId);
        }

        public CardAction(
                UUID playerId,
                UUID cardInstanceId,
                CardType cardType,
                UUID targetEventId,
                UUID sourceOutcomeId,
                UUID targetOutcomeId) {
            this(
                    playerId,
                    cardInstanceId,
                    cardType,
                    CardGrade.I,
                    targetEventId,
                    null,
                    sourceOutcomeId,
                    targetOutcomeId,
                    null);
        }

        @Override
        public void validate(int eraNumber, int roundNumber) {
            if (cardType == CardType.STABILIZE || cardType == CardType.DETONATE) {
                throw new CardNotEligibleForActionRoundException(cardType);
            }
            // TRACE's "last round" lookback reads across era boundaries (Era N Round 1 reads Era N-1 Round 3);
            // only the very first round of the game has no predecessor round for it to read.
            if (cardType == CardType.TRACE && eraNumber == 1 && roundNumber == 1) {
                throw new CardNotEligibleForRoundException(cardType, eraNumber, roundNumber);
            }
            if (cardType == CardType.TRACE && !cardType.supportedGrades().contains(grade)) {
                throw InvalidActionTargetException.traceUnsupportedGrade(grade);
            }
            if (ROUND_THREE_INELIGIBLE_CARD_TYPES.contains(cardType) && roundNumber == 3) {
                throw new CardNotEligibleForRoundException(cardType, eraNumber, roundNumber);
            }
            if (cardType == CardType.SCAN) {
                validateScanTargetMode();
            } else if (PLAYER_TARGETING_CARD_TYPES.contains(cardType)) {
                validatePlayerTarget();
            } else {
                validateEventTarget();
            }
        }

        private void validatePlayerTarget() {
            if (targetEventId != null || targetEventIds != null || sourceOutcomeId != null || targetOutcomeId != null) {
                throw InvalidActionTargetException.cardCannotTargetEvent(cardType);
            }
            if (targetPlayerId == null) {
                throw InvalidActionTargetException.cardRequiresTargetPlayer(cardType);
            }
            if (targetPlayerId.equals(playerId)) {
                throw InvalidActionTargetException.cardCannotTargetSelf(cardType);
            }
        }

        private void validateEventTarget() {
            if (targetPlayerId != null) {
                throw InvalidActionTargetException.cardCannotTargetPlayer(cardType);
            }
            if (targetEventIds != null) {
                throw InvalidActionTargetException.cardCannotTargetEvent(cardType);
            }
            if (targetEventId == null) {
                throw InvalidActionTargetException.cardRequiresTargetEvent(cardType);
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

        private void validateScanTargetMode() {
            if (targetEventIds == null || targetEventIds.isEmpty()) {
                throw InvalidActionTargetException.scanRequiresTargetEvents();
            }
            if (targetEventId != null || targetPlayerId != null || sourceOutcomeId != null || targetOutcomeId != null) {
                throw InvalidActionTargetException.scanCannotUseScalarTargets();
            }
            if (Set.copyOf(targetEventIds).size() != targetEventIds.size()) {
                throw InvalidActionTargetException.scanRequiresDistinctTargets();
            }
            var requiredCount =
                    switch (grade) {
                        case I -> 1;
                        case II -> 2;
                        case III -> 3;
                    };
            if (targetEventIds.size() != requiredCount) {
                throw InvalidActionTargetException.scanRequiresTargetCount(grade, requiredCount);
            }
        }

        /** Validates the SCAN selection against the definitions loaded for this game's current era. */
        public void validateCurrentEraTargets(Set<UUID> currentEraEventIds) {
            if (cardType != CardType.SCAN) {
                return;
            }
            validateScanTargetMode();
            targetEventIds.stream()
                    .filter(target -> !currentEraEventIds.contains(target))
                    .findFirst()
                    .ifPresent(target -> {
                        throw new UnknownActionTargetException(target);
                    });
            if (grade == CardGrade.III && !Set.copyOf(targetEventIds).equals(currentEraEventIds)) {
                throw InvalidActionTargetException.scanRequiresCompleteCurrentEra();
            }
        }

        public boolean isDirectProbabilityInfluenceOn(UUID eventId) {
            return eventId.equals(targetEventId)
                    && (cardType == CardType.PUSH || cardType == CardType.SUPPRESS || cardType == CardType.SWING);
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
                    grade,
                    targetEventId,
                    targetEventIds,
                    sourceOutcomeId,
                    targetOutcomeId,
                    targetPlayerId);
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
        public void validate(int eraNumber, int roundNumber) {
            switch (specialAction) {
                case RALLY, MOMENTUM -> throw new DeclarationSpecialActionRequiredException(specialAction);
                case FORESIGHT, ANNIHILATE, SEAL, REWRITE, MIMIC -> requireEventAndOutcome();
                case FULFILLMENT -> requireEvent();
                case CORRUPT -> requireOpponent();
                case EXPOSE -> requireExposeTarget();
                case CASCADE, OBSCURE, THREAD, TAPESTRY, UNRAVEL -> {
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
            if (targetEventId != null || targetOutcomeId != null) {
                throw InvalidActionTargetException.specialActionCannotTargetEvent(specialAction);
            }
            if (targetPlayerId == null) {
                throw InvalidActionTargetException.specialActionRequiresTargetPlayer(specialAction);
            }
            if (targetPlayerId.equals(playerId)) {
                throw InvalidActionTargetException.corruptCannotTargetSelf();
            }
        }

        private void requireExposeTarget() {
            if (targetEventId != null || targetOutcomeId != null) {
                throw InvalidActionTargetException.specialActionCannotTargetEvent(specialAction);
            }
            if (targetPlayerId == null) {
                throw InvalidActionTargetException.specialActionRequiresTargetPlayer(specialAction);
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
