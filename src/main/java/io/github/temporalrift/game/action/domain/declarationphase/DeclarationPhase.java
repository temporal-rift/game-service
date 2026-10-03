package io.github.temporalrift.game.action.domain.declarationphase;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.shared.domain.AggregateRoot;

/**
 * The explicit timed declaration opportunity between hand selection and Action Round 1. Exactly one
 * phase exists per game and era; Round 1 follows completion of its eligible decisions or expiry.
 */
public class DeclarationPhase extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "DeclarationPhase";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final Instant expiresAt;
    private final Map<UUID, DeclarationDecision> decisions;
    private DeclarationPhaseStatus status;

    public DeclarationPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt) {
        this(id, gameId, eraNumber, expiresAt, DeclarationPhaseStatus.OPEN, Map.of());
    }

    public DeclarationPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt, List<UUID> eligiblePlayerIds) {
        this(id, gameId, eraNumber, expiresAt);
        eligiblePlayerIds.forEach(
                playerId -> decisions.put(Objects.requireNonNull(playerId), DeclarationDecision.PENDING));
    }

    private DeclarationPhase(
            UUID id,
            UUID gameId,
            int eraNumber,
            Instant expiresAt,
            DeclarationPhaseStatus status,
            Map<UUID, DeclarationDecision> decisions) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.decisions = new HashMap<>(Map.copyOf(decisions));
    }

    public static DeclarationPhase reconstitute(
            UUID id, UUID gameId, int eraNumber, Instant expiresAt, DeclarationPhaseStatus status) {
        return reconstitute(id, gameId, eraNumber, expiresAt, status, Map.of());
    }

    public static DeclarationPhase reconstitute(
            UUID id,
            UUID gameId,
            int eraNumber,
            Instant expiresAt,
            DeclarationPhaseStatus status,
            Map<UUID, DeclarationDecision> decisions) {
        return new DeclarationPhase(id, gameId, eraNumber, expiresAt, status, decisions);
    }

    public Map<UUID, DeclarationDecision> decisions() {
        return Map.copyOf(decisions);
    }

    public boolean hasDeclined(UUID playerId) {
        return decisions.get(playerId) == DeclarationDecision.DECLINED;
    }

    public void assertPending(UUID playerId, Instant now) {
        var decision = decisions.get(playerId);
        if (decision == DeclarationDecision.DECLARED || decision == DeclarationDecision.DECLINED) {
            throw new DeclarationAlreadyDecidedException(playerId, eraNumber);
        }
        assertOpen(now);
        if (decision != DeclarationDecision.PENDING) {
            throw new DeclarationWindowClosedException(gameId, eraNumber);
        }
    }

    /** Returns true only when this decision completes and closes the phase. */
    public boolean decide(UUID playerId, DeclarationDecision decision, Instant now) {
        if (decision == DeclarationDecision.PENDING) {
            throw new IllegalArgumentException("A terminal declaration decision is required");
        }
        assertPending(playerId, now);
        decisions.put(playerId, Objects.requireNonNull(decision));
        return closeIfComplete();
    }

    public boolean closeIfComplete() {
        if (status != DeclarationPhaseStatus.OPEN || decisions.containsValue(DeclarationDecision.PENDING)) {
            return false;
        }
        status = DeclarationPhaseStatus.CLOSED;
        return true;
    }

    public void assertOpen(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != DeclarationPhaseStatus.OPEN || !now.isBefore(expiresAt)) {
            throw new DeclarationWindowClosedException(gameId, eraNumber);
        }
    }

    /**
     * Closes the phase, returning whether this call performed the close. Concurrent expiry paths
     * converge: only the first closer observes {@code true} and may start Round 1.
     */
    public boolean closeIfOpen(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != DeclarationPhaseStatus.OPEN || now.isBefore(expiresAt)) {
            return false;
        }
        status = DeclarationPhaseStatus.CLOSED;
        return true;
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

    public Instant expiresAt() {
        return expiresAt;
    }

    public DeclarationPhaseStatus status() {
        return status;
    }
}
