package io.github.temporalrift.game.action.domain.actionround;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import io.github.temporalrift.game.action.domain.event.ActionRoundStarted;
import io.github.temporalrift.game.action.domain.event.PlayerPassed;
import io.github.temporalrift.game.action.domain.event.PlayerSkipped;
import io.github.temporalrift.game.shared.domain.AggregateRoot;
import io.github.temporalrift.game.shared.domain.event.ActionRoundClosed;

public class ActionRound extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ActionRound";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final int roundNumber;
    private final int timerSeconds;
    private final List<UUID> pendingPlayerIds;
    private final List<SubmittedAction> submittedActions;
    private final List<UUID> passedPlayerIds;
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
        this.passedPlayerIds = new ArrayList<>();
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
        this.passedPlayerIds = new ArrayList<>(state.passedPlayerIds());
    }

    public static ActionRound reconstitute(UUID id, ActionRoundConfig config, PersistedState state) {
        return new ActionRound(id, config, state);
    }

    /** The mutable, persisted part of an {@code ActionRound}'s state, as loaded from storage. */
    public record PersistedState(
            RoundStatus status,
            String closedReason,
            List<UUID> pendingPlayerIds,
            List<SubmittedAction> submittedActions,
            List<UUID> passedPlayerIds) {

        public PersistedState(
                RoundStatus status,
                String closedReason,
                List<UUID> pendingPlayerIds,
                List<SubmittedAction> submittedActions) {
            this(status, closedReason, pendingPlayerIds, submittedActions, List.of());
        }
    }

    /**
     * Accepts one player's submission for this round. {@code ActionRound} enforces only round-level
     * invariants (open, not a duplicate submission); the submission validates its own fields via
     * {@link SubmittedAction#validate(int, int)}. Cross-aggregate eligibility (faction allows this special,
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
        action.validate(eraNumber, roundNumber);

        pendingPlayerIds.remove(action.playerId());
        submittedActions.add(action);
        registerEvent(action.toPlayedEvent(gameId, eraNumber, roundNumber));

        return allSubmitted();
    }

    /**
     * Accepts one player's explicit pass: it consumes their slot exactly like a submission, so it counts
     * toward closing the round early, but records no action and spends no card. Jam does not restrict it.
     * Only an in-process {@link PlayerPassed} is registered; the pass surfaces publicly at close as a skip.
     */
    public boolean pass(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (this.status != RoundStatus.OPEN) {
            throw new ActionRoundClosedException();
        }
        if (!this.pendingPlayerIds.contains(playerId)) {
            throw new DuplicateSubmissionException(playerId);
        }

        pendingPlayerIds.remove(playerId);
        passedPlayerIds.add(playerId);
        registerEvent(new PlayerPassed(gameId, eraNumber, roundNumber, playerId));

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
        // A pass resolves as the same neutral skip a timer expiry produces: passers and timed-out players
        // are merged into one list, ordered by id alone so its order cannot tell the two apart.
        var skippedPlayerIds = Stream.concat(passedPlayerIds.stream(), pendingPlayerIds.stream())
                .sorted()
                .toList();
        skippedPlayerIds.forEach(
                skippedId -> registerEvent(new PlayerSkipped(gameId, eraNumber, roundNumber, skippedId, closedReason)));
        pendingPlayerIds.clear();

        status = RoundStatus.CLOSED;
        var cancelledPlayerIds = RoundCancellation.cancelledPlayerIds(submittedActions);
        submittedActions.stream()
                .filter(action -> !cancelledPlayerIds.contains(action.playerId()))
                .map(action -> action.scoringFact(gameId, eraNumber))
                .flatMap(java.util.Optional::stream)
                .forEach(this::registerEvent);
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

    public List<UUID> passedPlayerIds() {
        return Collections.unmodifiableList(passedPlayerIds);
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
