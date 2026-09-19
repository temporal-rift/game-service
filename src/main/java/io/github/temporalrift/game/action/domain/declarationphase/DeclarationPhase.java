package io.github.temporalrift.game.action.domain.declarationphase;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.shared.domain.AggregateRoot;

/**
 * The explicit timed declaration opportunity between hand selection and Action Round 1. Exactly one
 * phase exists per game and era; it opens when every player holds a terminal final hand and closes on
 * timer expiry, at which point the era advances to Round 1.
 */
public class DeclarationPhase extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "DeclarationPhase";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final Instant expiresAt;
    private DeclarationPhaseStatus status;

    public DeclarationPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt) {
        this(id, gameId, eraNumber, expiresAt, DeclarationPhaseStatus.OPEN);
    }

    private DeclarationPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt, DeclarationPhaseStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static DeclarationPhase reconstitute(
            UUID id, UUID gameId, int eraNumber, Instant expiresAt, DeclarationPhaseStatus status) {
        return new DeclarationPhase(id, gameId, eraNumber, expiresAt, status);
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
