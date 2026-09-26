package io.github.temporalrift.game.action.domain.actionround;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.shared.domain.event.ForesightDeclared;
import io.github.temporalrift.game.shared.domain.event.OutcomeAnnihilated;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

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

    /**
     * Checks playability including the configured final era. The default accepts every era and exists
     * so callers without rules configuration keep the round-scoped behavior; card actions override it
     * to reject a final-era Stall whose promised next resolution cycle cannot occur.
     */
    default void validate(int eraNumber, int roundNumber, int maxEras) {
        validate(eraNumber, roundNumber);
    }

    /**
     * Checks only the configured final-era rule, without repeating round-scoped structural validation.
     * Command handlers call this before {@code ActionRound.submit} so a final-era Stall fails fast with
     * the round-ineligibility error while all other structural checks stay in the aggregate.
     */
    default void validateFinalEra(int eraNumber, int roundNumber, int maxEras) {}

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
            UUID targetPlayerId,
            List<UUID> targetPlayerIds)
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
            targetPlayerIds = targetPlayerIds == null ? null : List.copyOf(targetPlayerIds);
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
                    targetPlayerId,
                    null);
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
                    null,
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
            } else if (cardType == CardType.NULLIFY) {
                validateNullifyTargetMode();
            } else if (PLAYER_TARGETING_CARD_TYPES.contains(cardType)) {
                validatePlayerTarget();
            } else {
                validateEventTarget();
            }
        }

        @Override
        public void validate(int eraNumber, int roundNumber, int maxEras) {
            validate(eraNumber, roundNumber);
            validateFinalEra(eraNumber, roundNumber, maxEras);
        }

        @Override
        public void validateFinalEra(int eraNumber, int roundNumber, int maxEras) {
            // Stall defers resolution to the next era; in the final era that era never occurs,
            // so the card is unplayable there rather than creating an unresolvable stalled event.
            if (cardType == CardType.STALL && eraNumber >= maxEras) {
                throw new CardNotEligibleForRoundException(cardType, eraNumber, roundNumber);
            }
        }

        private void validatePlayerTarget() {
            if (targetEventId != null || targetEventIds != null || sourceOutcomeId != null || targetOutcomeId != null) {
                throw InvalidActionTargetException.cardCannotTargetEvent(cardType);
            }
            if (targetPlayerIds != null) {
                throw InvalidActionTargetException.cardCannotUsePlayerTargetList(cardType);
            }
            if (targetPlayerId == null) {
                throw InvalidActionTargetException.cardRequiresTargetPlayer(cardType);
            }
            if (targetPlayerId.equals(playerId)) {
                throw InvalidActionTargetException.cardCannotTargetSelf(cardType);
            }
        }

        private void validateNullifyTargetMode() {
            if (targetEventId != null
                    || targetEventIds != null
                    || targetPlayerId != null
                    || sourceOutcomeId != null
                    || targetOutcomeId != null) {
                throw InvalidActionTargetException.nullifyCannotUseScalarTargets();
            }
            if (targetPlayerIds == null || targetPlayerIds.isEmpty()) {
                throw InvalidActionTargetException.nullifyRequiresTargetPlayers();
            }
            if (Set.copyOf(targetPlayerIds).size() != targetPlayerIds.size()) {
                throw InvalidActionTargetException.nullifyRequiresDistinctTargets();
            }
            var requiredCount = switch (grade) {
                case I -> 1;
                case II -> 2;
                case III -> throw InvalidActionTargetException.nullifyUnsupportedGrade(grade);
            };
            if (targetPlayerIds.size() != requiredCount) {
                throw InvalidActionTargetException.nullifyRequiresTargetCount(grade, requiredCount);
            }
            if (targetPlayerIds.contains(playerId)) {
                throw InvalidActionTargetException.cardCannotTargetSelf(cardType);
            }
        }

        private void validateEventTarget() {
            if (targetPlayerId != null || targetPlayerIds != null) {
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
            if (targetEventId != null
                    || targetPlayerId != null
                    || targetPlayerIds != null
                    || sourceOutcomeId != null
                    || targetOutcomeId != null) {
                throw InvalidActionTargetException.scanCannotUseScalarTargets();
            }
            if (Set.copyOf(targetEventIds).size() != targetEventIds.size()) {
                throw InvalidActionTargetException.scanRequiresDistinctTargets();
            }
            var requiredCount = switch (grade) {
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
                    targetPlayerId,
                    targetPlayerIds);
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
            UUID sourceEventId,
            UUID sourceOutcomeId,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId)
            implements SubmittedAction {

        @Override
        public void validate(int eraNumber, int roundNumber, int maxEras) {
            validate(eraNumber, roundNumber);
            validateFinalEra(eraNumber, roundNumber, maxEras);
        }

        @Override
        public void validateFinalEra(int eraNumber, int roundNumber, int maxEras) {
            // Cascade only erases again when its event carries into a next era, which the final era never has.
            if (specialAction == SpecialAction.CASCADE && eraNumber >= maxEras) {
                throw new SpecialActionNotEligibleForEraException(specialAction, eraNumber);
            }
        }

        @Override
        public void validate(int eraNumber, int roundNumber) {
            if (sourceEventId != null || sourceOutcomeId != null) {
                throw InvalidActionTargetException.specialActionCannotHaveSource(specialAction);
            }
            // Obscure covers the following round of the same era, which Round 3 does not have.
            if (specialAction == SpecialAction.OBSCURE && roundNumber == 3) {
                throw new SpecialActionNotEligibleForRoundException(specialAction, eraNumber, roundNumber);
            }
            switch (specialAction) {
                case RALLY, MOMENTUM -> throw new DeclarationSpecialActionRequiredException(specialAction);
                case FORESIGHT, ANNIHILATE, SEAL, REWRITE, MIMIC, CASCADE, THREAD, REWEAVE -> requireEventAndOutcome();
                case FULFILLMENT -> requireEvent();
                case CORRUPT -> requireOpponent();
                case EXPOSE -> requireExposeTarget();
                case OBSCURE, TAPESTRY -> {
                    // No target; TAPESTRY's chain prerequisites are validated by timeline-service's chain saga.
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
                    sourceEventId,
                    sourceOutcomeId,
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
