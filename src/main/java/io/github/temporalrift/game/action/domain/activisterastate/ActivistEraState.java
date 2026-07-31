package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.AggregateRoot;

/**
 * The era-scoped action state that owns an Activist's declaration and eligibility independently of an individual
 * action-round aggregate.
 */
public final class ActivistEraState extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "ActivistEraState";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final UUID activistPlayerId;
    private final boolean momentumEligible;
    private boolean declarationSucceeded;
    private ActivistDeclarationMode declarationMode;
    private UUID targetEventId;
    private UUID targetOutcomeId;
    private UUID exposedPlayerId;
    private ProbabilityInfluenceSignature exposedSignature;
    private boolean exposeBehaviorChanged;

    public ActivistEraState(UUID id, UUID gameId, int eraNumber, UUID activistPlayerId, boolean momentumEligible) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.activistPlayerId = Objects.requireNonNull(activistPlayerId, "activistPlayerId must not be null");
        this.momentumEligible = momentumEligible;
    }

    private ActivistEraState(UUID id, UUID gameId, int eraNumber, UUID activistPlayerId, PersistedState state) {
        this(id, gameId, eraNumber, activistPlayerId, state.momentumEligible());
        this.declarationSucceeded = state.declaration().succeeded();
        this.declarationMode = state.declaration().mode();
        this.targetEventId = state.declaration().targetEventId();
        this.targetOutcomeId = state.declaration().targetOutcomeId();
        this.exposedPlayerId = state.expose().playerId();
        this.exposedSignature = state.expose().signature();
        this.exposeBehaviorChanged = state.expose().behaviorChanged();
    }

    public static ActivistEraState reconstitute(
            UUID id, UUID gameId, int eraNumber, UUID activistPlayerId, PersistedState state) {
        return new ActivistEraState(id, gameId, eraNumber, activistPlayerId, state);
    }

    public record PersistedState(boolean momentumEligible, Declaration declaration, Expose expose) {
        public PersistedState {
            Objects.requireNonNull(declaration, "declaration must not be null");
            Objects.requireNonNull(expose, "expose must not be null");
        }
    }

    public record Declaration(
            boolean succeeded, ActivistDeclarationMode mode, UUID targetEventId, UUID targetOutcomeId) {}

    public record Expose(UUID playerId, ProbabilityInfluenceSignature signature, boolean behaviorChanged) {}

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public int eraNumber() {
        return eraNumber;
    }

    public UUID activistPlayerId() {
        return activistPlayerId;
    }

    public boolean momentumEligible() {
        return momentumEligible;
    }

    public boolean declarationSucceeded() {
        return declarationSucceeded;
    }

    public ActivistDeclarationMode declarationMode() {
        return declarationMode;
    }

    public void declare(ActivistDeclarationMode mode, UUID targetEventId, UUID targetOutcomeId) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(targetEventId, "targetEventId must not be null");
        Objects.requireNonNull(targetOutcomeId, "targetOutcomeId must not be null");
        if (declarationMode != null) {
            throw new ActivistDeclarationAlreadyRecordedException(activistPlayerId, eraNumber);
        }
        if (mode == ActivistDeclarationMode.MOMENTUM && !momentumEligible) {
            throw new MomentumNotEligibleException(activistPlayerId, eraNumber);
        }
        declarationMode = mode;
        this.targetEventId = targetEventId;
        this.targetOutcomeId = targetOutcomeId;
    }

    public void recordResolution(boolean succeeded) {
        if (declarationMode == null) {
            throw new IllegalStateException("Cannot resolve an Activist declaration that was never recorded");
        }
        declarationSucceeded = succeeded;
    }

    public void expose(UUID targetPlayerId, ProbabilityInfluenceSignature signature) {
        Objects.requireNonNull(targetPlayerId, "targetPlayerId must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        if (exposedPlayerId != null) {
            throw new ExposeAlreadyRecordedException();
        }
        exposedPlayerId = targetPlayerId;
        exposedSignature = signature;
    }

    public boolean recordExposeBehaviorChanged(ProbabilityInfluenceSignature responseSignature) {
        if (exposedSignature == null || responseSignature == null || exposedSignature.equals(responseSignature)) {
            return false;
        }
        if (exposeBehaviorChanged) {
            return false;
        }
        exposeBehaviorChanged = true;
        return true;
    }

    public UUID targetEventId() {
        return targetEventId;
    }

    public UUID targetOutcomeId() {
        return targetOutcomeId;
    }

    public UUID exposedPlayerId() {
        return exposedPlayerId;
    }

    public ProbabilityInfluenceSignature exposedSignature() {
        return exposedSignature;
    }

    public boolean exposeBehaviorChanged() {
        return exposeBehaviorChanged;
    }
}
