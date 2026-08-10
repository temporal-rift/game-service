package io.github.temporalrift.game.action.domain.actionround;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.AggregateRoot;

public class ActionRound extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ActionRound";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final int roundNumber;
    private final int timerSeconds;
    private final List<UUID> pendingPlayerIds;
    private final List<SubmittedAction> submittedActions;
    private RoundStatus status;
    private String closedReason;

    public ActionRound(UUID id, ActionRoundConfig config, List<UUID> pendingPlayerIds) {
        this(id, config, ActionRoundParticipants.pending(pendingPlayerIds));
    }

    public ActionRound(UUID id, ActionRoundConfig config, ActionRoundParticipants participants) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(config, "config must not be null");
        this.gameId = config.gameId();
        this.eraNumber = config.eraNumber();
        this.roundNumber = config.roundNumber();
        this.timerSeconds = config.timerSeconds();
        this.pendingPlayerIds = new ArrayList<>(participants.pendingPlayerIds());
        this.submittedActions = new ArrayList<>(participants.submittedActions());
        this.submittedActions.forEach(action -> this.pendingPlayerIds.remove(action.playerId()));
        this.status = RoundStatus.OPEN;
        this.closedReason = null;
        registerEvent(new ActionRoundStarted(
                gameId, eraNumber, roundNumber, timerSeconds, List.copyOf(this.pendingPlayerIds)));
        // participants.submittedActions() carry Activist RALLY/MOMENTUM declarations made and validated
        // earlier, by RecordActivistDeclarationCommandHandler against ActivistEraStateRepository, before
        // this round existed — round 1 replays them as already-submitted so the player isn't asked again.
        // submit() is skipped deliberately: SpecialActionSubmission.validate() rejects RALLY/MOMENTUM
        // unconditionally (see its switch), since a declaration is never made through submit().
        this.submittedActions.forEach(action -> registerEvent(action.toPlayedEvent(gameId, eraNumber, roundNumber)));
    }

    private ActionRound(UUID id, ActionRoundConfig config, PersistedState state) {
        this.id = id;
        this.gameId = config.gameId();
        this.eraNumber = config.eraNumber();
        this.roundNumber = config.roundNumber();
        this.timerSeconds = config.timerSeconds();
        this.status = state.status();
        this.closedReason = state.closedReason();
        this.pendingPlayerIds = new ArrayList<>(state.pendingPlayerIds());
        this.submittedActions = new ArrayList<>(state.submittedActions());
    }

    public static ActionRound reconstitute(UUID id, ActionRoundConfig config, PersistedState state) {
        return new ActionRound(id, config, state);
    }

    /** The mutable, persisted part of an {@code ActionRound}'s state, as loaded from storage. */
    public record PersistedState(
            RoundStatus status,
            String closedReason,
            List<UUID> pendingPlayerIds,
            List<SubmittedAction> submittedActions) {}

    /**
     * Accepts one player's submission for this round. {@code ActionRound} enforces only round-level
     * invariants (open, not a duplicate submission); the submission validates its own fields via
     * {@link SubmittedAction#validate()}. Cross-aggregate eligibility (faction allows this special,
     * player is not jammed) is the caller's responsibility — {@code ActionRound} has no visibility into
     * {@code PlayerState} to verify those itself.
     */
    public boolean submit(SubmittedAction action) {
        if (this.status != RoundStatus.OPEN) {
            throw new ActionRoundClosedException();
        }
        if (!this.pendingPlayerIds.contains(action.playerId())) {
            throw new DuplicateSubmissionException(action.playerId());
        }
        action.validate();

        pendingPlayerIds.remove(action.playerId());
        submittedActions.add(action);
        registerEvent(action.toPlayedEvent(gameId, eraNumber, roundNumber));
        action.scoringFact(gameId, eraNumber).ifPresent(this::registerEvent);

        return allSubmitted();
    }

    public CloseOutcome close(String closedReason) {
        if (this.status != RoundStatus.OPEN) {
            return new CloseOutcome.AlreadyClosing();
        }
        this.closedReason = closedReason;

        // CLOSING is not the concurrency guard.
        // The pessimistic lock in tryClose already prevents two transactions from reaching this line simultaneously.
        // The two-step exists solely to make close() a no-op if called twice on the same in-memory instance,
        // the second call hits the status check above and returns AlreadyClosing without re-registering events.
        // Never persisted — save() writes CLOSED after this returns.
        status = RoundStatus.CLOSING;
        var skippedPlayerIds = List.copyOf(pendingPlayerIds);
        skippedPlayerIds.forEach(
                skippedId -> registerEvent(new PlayerSkipped(gameId, eraNumber, roundNumber, skippedId, closedReason)));
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

    public int timerSeconds() {
        return timerSeconds;
    }

    public String closedReason() {
        return closedReason;
    }
}
