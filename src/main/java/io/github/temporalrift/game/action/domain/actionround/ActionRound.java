package io.github.temporalrift.game.action.domain.actionround;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.action.ActionRoundStarted;
import io.github.temporalrift.events.action.CardPlayed;
import io.github.temporalrift.events.action.PlayerSkipped;
import io.github.temporalrift.events.action.SpecialActionPlayed;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.shared.AggregateRoot;

public class ActionRound extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ActionRound";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final int roundNumber;
    private final List<UUID> pendingPlayerIds;
    private final List<SubmittedAction> submittedActions;
    private RoundStatus status;

    public ActionRound(
            UUID id, UUID gameId, int eraNumber, int roundNumber, List<UUID> pendingPlayerIds, int timerSeconds) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.roundNumber = roundNumber;
        this.pendingPlayerIds =
                new ArrayList<>(Objects.requireNonNull(pendingPlayerIds, "pendingPlayerIds must not be null"));
        this.submittedActions = new ArrayList<>();
        this.status = RoundStatus.OPEN;
        registerEvent(
                new ActionRoundStarted(gameId, eraNumber, roundNumber, timerSeconds, List.copyOf(pendingPlayerIds)));
    }

    private ActionRound(
            UUID id,
            UUID gameId,
            int eraNumber,
            int roundNumber,
            RoundStatus status,
            List<UUID> pendingPlayerIds,
            List<SubmittedAction> submittedActions) {
        this.id = id;
        this.gameId = gameId;
        this.eraNumber = eraNumber;
        this.roundNumber = roundNumber;
        this.status = status;
        this.pendingPlayerIds = new ArrayList<>(pendingPlayerIds);
        this.submittedActions = new ArrayList<>(submittedActions);
    }

    public static ActionRound reconstitute(
            UUID id,
            UUID gameId,
            int eraNumber,
            int roundNumber,
            RoundStatus status,
            List<UUID> pendingPlayerIds,
            List<SubmittedAction> submittedActions) {
        return new ActionRound(id, gameId, eraNumber, roundNumber, status, pendingPlayerIds, submittedActions);
    }

    public boolean submitCard(
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID targetOutcomeId,
            List<UUID> playerHand) {
        if (this.status != RoundStatus.OPEN) {
            throw new ActionRoundClosedException();
        }
        if (!this.pendingPlayerIds.contains(playerId)) {
            throw new DuplicateSubmissionException(playerId);
        }
        if (!playerHand.contains(cardInstanceId)) {
            throw new CardNotInHandException(cardInstanceId);
        }

        pendingPlayerIds.remove(playerId);
        submittedActions.add(
                new SubmittedAction.CardAction(playerId, cardInstanceId, cardType, targetEventId, targetOutcomeId));
        registerEvent(new CardPlayed(
                gameId, eraNumber, roundNumber, playerId, cardInstanceId, cardType, targetEventId, targetOutcomeId));

        return allSubmitted();
    }

    public boolean submitSpecial(
            UUID playerId,
            Faction faction,
            SpecialAction specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId,
            boolean isJammed) {
        if (this.status != RoundStatus.OPEN) {
            throw new ActionRoundClosedException();
        }
        if (!this.pendingPlayerIds.contains(playerId)) {
            throw new DuplicateSubmissionException(playerId);
        }
        if (isJammed) {
            throw new JammedPlayerException(playerId);
        }

        pendingPlayerIds.remove(playerId);
        submittedActions.add(new SubmittedAction.SpecialActionSubmission(
                playerId, faction, specialAction, targetEventId, targetOutcomeId, targetPlayerId));
        registerEvent(new SpecialActionPlayed(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                faction,
                specialAction,
                targetEventId,
                targetOutcomeId,
                targetPlayerId));

        return allSubmitted();
    }

    public CloseOutcome close(String closedReason) {
        if (this.status != RoundStatus.OPEN) {
            return new CloseOutcome.AlreadyClosing();
        }

        // CLOSING is not the concurrency guard.
        // The pessimistic lock in tryClose already prevents two transactions from reaching this line simultaneously.
        // The two-step exists solely to make close() a no-op if called twice on the same in-memory instance,
        // the second call hits the status check above and returns AlreadyClosing without re-registering events.
        // Never persisted — save() writes CLOSED after this returns.
        status = RoundStatus.CLOSING;
        var skippedPlayerIds = List.copyOf(pendingPlayerIds);
        skippedPlayerIds.forEach(skippedId ->
                registerEvent(new PlayerSkipped(gameId, eraNumber, roundNumber, skippedId, "TIMER_EXPIRED")));
        pendingPlayerIds.clear();

        status = RoundStatus.CLOSED;
        registerEvent(
                new ActionRoundClosed(gameId, eraNumber, roundNumber, closedReason, this.submittedActions.size()));

        return new CloseOutcome.Closed(skippedPlayerIds);
    }

    private boolean allSubmitted() {
        return pendingPlayerIds.isEmpty();
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public int eraNumber() {
        return eraNumber;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public List<UUID> pendingPlayerIds() {
        return Collections.unmodifiableList(pendingPlayerIds);
    }

    public List<SubmittedAction> submittedActions() {
        return Collections.unmodifiableList(submittedActions);
    }

    public RoundStatus status() {
        return status;
    }
}
