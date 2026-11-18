package io.github.temporalrift.game.action.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.UnknownActionTargetException;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Accepts a player's single submission for an open action round: a card, a faction special, or a pass.
 *
 * <p>Every kind consumes the player's one submission slot. The returned {@link Result#roundClosed()} flag only
 * reports whether this submission emptied the in-memory pending list; the actual round close is still owned by
 * {@code ActionRoundSaga}, so the timer path and the last-submission path converge through the same locking logic.
 */
public interface SubmitRoundActionUseCase {

    /** Validates and stores the submission, then publishes its in-process events. */
    Result handle(Command command);

    /** One submission for a specific action round. */
    sealed interface Command permits PlayCard, PlaySpecial, Pass {

        UUID gameId();

        int eraNumber();

        int roundNumber();

        UUID playerId();
    }

    /** Plays a card from the player's hand. */
    record PlayCard(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            UUID targetEventId,
            List<UUID> targetEventIds,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId,
            List<UUID> targetPlayerIds,
            CardCategory disguiseCategory)
            implements Command {

        public PlayCard {
            if (targetEventIds != null && targetEventIds.stream().anyMatch(Objects::isNull)) {
                throw new UnknownActionTargetException(null);
            }
            targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
            if (targetPlayerIds != null && targetPlayerIds.stream().anyMatch(Objects::isNull)) {
                throw new UnknownActionTargetException(null);
            }
            targetPlayerIds = targetPlayerIds == null ? null : List.copyOf(targetPlayerIds);
        }

        public PlayCard(
                UUID gameId,
                int eraNumber,
                int roundNumber,
                UUID playerId,
                UUID cardInstanceId,
                UUID targetEventId,
                UUID sourceOutcomeId,
                UUID targetOutcomeId,
                UUID targetPlayerId) {
            this(
                    gameId,
                    eraNumber,
                    roundNumber,
                    playerId,
                    cardInstanceId,
                    targetEventId,
                    null,
                    sourceOutcomeId,
                    targetOutcomeId,
                    targetPlayerId,
                    null,
                    null);
        }
    }

    /** Plays one of the player's faction specials. */
    record PlaySpecial(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            SpecialAction specialAction,
            UUID sourceEventId,
            UUID sourceOutcomeId,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId)
            implements Command {}

    /**
     * Passes the round: spends no card and is published only at close, as the same neutral skip a timer expiry
     * produces.
     */
    record Pass(UUID gameId, int eraNumber, int roundNumber, UUID playerId) implements Command {

        public Pass {
            Objects.requireNonNull(gameId, "gameId must not be null");
            Objects.requireNonNull(playerId, "playerId must not be null");
        }
    }

    /** Result returned after the submission is stored and its in-process events are published. */
    record Result(UUID gameId, int eraNumber, int roundNumber, UUID playerId, boolean roundClosed) {}
}
