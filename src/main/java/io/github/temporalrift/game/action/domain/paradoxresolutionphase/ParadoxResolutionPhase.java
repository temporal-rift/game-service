package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.AggregateRoot;
import io.github.temporalrift.game.shared.domain.model.CardType;

public class ParadoxResolutionPhase extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ParadoxResolutionPhase";

    public static final Set<CardType> ELIGIBLE_CARD_TYPES =
            Set.of(CardType.PUSH, CardType.SUPPRESS, CardType.STABILIZE, CardType.DETONATE);

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final Instant expiresAt;
    private final Set<UUID> affectedEventIds;
    private final Set<UUID> submittedPlayerIds;
    private ParadoxResolutionPhaseStatus status;

    public ParadoxResolutionPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt) {
        this(id, gameId, eraNumber, expiresAt, Set.of());
    }

    public ParadoxResolutionPhase(UUID id, UUID gameId, int eraNumber, Instant expiresAt, Set<UUID> affectedEventIds) {
        this(id, gameId, eraNumber, expiresAt, ParadoxResolutionPhaseStatus.OPEN, affectedEventIds, Set.of());
    }

    private ParadoxResolutionPhase(
            UUID id,
            UUID gameId,
            int eraNumber,
            Instant expiresAt,
            ParadoxResolutionPhaseStatus status,
            Set<UUID> affectedEventIds,
            Set<UUID> submittedPlayerIds) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.affectedEventIds =
                new LinkedHashSet<>(Objects.requireNonNull(affectedEventIds, "affectedEventIds must not be null"));
        this.submittedPlayerIds =
                new LinkedHashSet<>(Objects.requireNonNull(submittedPlayerIds, "submittedPlayerIds must not be null"));
    }

    public static ParadoxResolutionPhase reconstitute(
            UUID id,
            UUID gameId,
            int eraNumber,
            Instant expiresAt,
            ParadoxResolutionPhaseStatus status,
            Set<UUID> affectedEventIds,
            Set<UUID> submittedPlayerIds) {
        return new ParadoxResolutionPhase(
                id, gameId, eraNumber, expiresAt, status, affectedEventIds, submittedPlayerIds);
    }

    public void assertPlayerCanSubmit(UUID playerId, Instant now) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (status != ParadoxResolutionPhaseStatus.OPEN || !now.isBefore(expiresAt)) {
            throw new ParadoxResolutionPhaseNotOpenException(gameId, eraNumber);
        }
        if (submittedPlayerIds.contains(playerId)) {
            throw new DuplicateParadoxResolutionSubmissionException(playerId);
        }
    }

    public void submit(UUID playerId, CardType cardType, Instant now) {
        assertPlayerCanSubmit(playerId, now);
        if (!ELIGIBLE_CARD_TYPES.contains(Objects.requireNonNull(cardType, "cardType must not be null"))) {
            throw new CardNotEligibleForParadoxResolutionException(cardType);
        }
        submittedPlayerIds.add(playerId);
    }

    public void assertAffectedEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (!affectedEventIds.isEmpty() && !affectedEventIds.contains(eventId)) {
            throw new ParadoxResolutionTargetNotAffectedException(eventId);
        }
    }

    public boolean recoverAffectedEventIds(Set<UUID> recoveredAffectedEventIds) {
        Objects.requireNonNull(recoveredAffectedEventIds, "recoveredAffectedEventIds must not be null");
        if (recoveredAffectedEventIds.isEmpty() || !affectedEventIds.isEmpty()) {
            return false;
        }
        affectedEventIds.addAll(recoveredAffectedEventIds);
        return true;
    }

    public void close() {
        status = ParadoxResolutionPhaseStatus.CLOSED;
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

    public ParadoxResolutionPhaseStatus status() {
        return status;
    }

    public Set<UUID> affectedEventIds() {
        return Set.copyOf(affectedEventIds);
    }

    public Set<UUID> submittedPlayerIds() {
        return Set.copyOf(submittedPlayerIds);
    }
}
