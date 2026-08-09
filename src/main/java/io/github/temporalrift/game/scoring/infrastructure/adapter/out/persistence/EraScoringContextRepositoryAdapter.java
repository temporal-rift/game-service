package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.domain.context.ActionScoringFact;
import io.github.temporalrift.game.scoring.domain.context.AnnihilationFact;
import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.CorruptCorrelationFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.FulfillmentDeclarationFact;
import io.github.temporalrift.game.scoring.domain.context.ParadoxCascadeScoringFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

@Component
class EraScoringContextRepositoryAdapter implements EraScoringContextRepository {

    private static final Logger log = LoggerFactory.getLogger(EraScoringContextRepositoryAdapter.class);

    private final ScoringContextPlayerJpaRepository playerJpaRepository;
    private final ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository;
    private final ScoringContextChainFactJpaRepository chainFactJpaRepository;
    private final ScoringContextEventOutcomeJpaRepository eventOutcomeJpaRepository;
    private final ScoringContextAnnihilatedOutcomeJpaRepository annihilatedOutcomeJpaRepository;
    private final ScoringTimelineOutcomeInboxJpaRepository outcomeInboxJpaRepository;
    private final ScoringContextActionFactsReadyJpaRepository actionFactsReadyJpaRepository;
    private final ScoringContextActivistDeclarationJpaRepository activistDeclarationJpaRepository;
    private final ScoringContextActionFactJpaRepository actionFactJpaRepository;
    private final ScoringContextRevisionistActionJpaRepository revisionistActionJpaRepository;
    private final ScoringTimelineResolutionBarrierJpaRepository resolutionBarrierJpaRepository;
    private final ScoringContextFulfillmentDeclarationJpaRepository fulfillmentDeclarationJpaRepository;
    private final ScoringContextCorruptCorrelationJpaRepository corruptCorrelationJpaRepository;
    private final ScoringContextParadoxCascadeFactJpaRepository paradoxCascadeFactJpaRepository;
    private final ObjectMapper objectMapper;

    EraScoringContextRepositoryAdapter(
            ScoringContextPlayerJpaRepository playerJpaRepository,
            ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository,
            ScoringContextChainFactJpaRepository chainFactJpaRepository,
            ScoringContextEventOutcomeJpaRepository eventOutcomeJpaRepository,
            ScoringContextAnnihilatedOutcomeJpaRepository annihilatedOutcomeJpaRepository,
            ScoringTimelineOutcomeInboxJpaRepository outcomeInboxJpaRepository,
            ScoringContextActionFactsReadyJpaRepository actionFactsReadyJpaRepository,
            ScoringContextActivistDeclarationJpaRepository activistDeclarationJpaRepository,
            ScoringContextActionFactJpaRepository actionFactJpaRepository,
            ScoringContextRevisionistActionJpaRepository revisionistActionJpaRepository,
            ScoringTimelineResolutionBarrierJpaRepository resolutionBarrierJpaRepository,
            ScoringContextFulfillmentDeclarationJpaRepository fulfillmentDeclarationJpaRepository,
            ScoringContextCorruptCorrelationJpaRepository corruptCorrelationJpaRepository,
            ScoringContextParadoxCascadeFactJpaRepository paradoxCascadeFactJpaRepository,
            ObjectMapper objectMapper) {
        this.playerJpaRepository = playerJpaRepository;
        this.eraOutcomeExpectationJpaRepository = eraOutcomeExpectationJpaRepository;
        this.chainFactJpaRepository = chainFactJpaRepository;
        this.eventOutcomeJpaRepository = eventOutcomeJpaRepository;
        this.annihilatedOutcomeJpaRepository = annihilatedOutcomeJpaRepository;
        this.outcomeInboxJpaRepository = outcomeInboxJpaRepository;
        this.actionFactsReadyJpaRepository = actionFactsReadyJpaRepository;
        this.activistDeclarationJpaRepository = activistDeclarationJpaRepository;
        this.actionFactJpaRepository = actionFactJpaRepository;
        this.revisionistActionJpaRepository = revisionistActionJpaRepository;
        this.resolutionBarrierJpaRepository = resolutionBarrierJpaRepository;
        this.fulfillmentDeclarationJpaRepository = fulfillmentDeclarationJpaRepository;
        this.corruptCorrelationJpaRepository = corruptCorrelationJpaRepository;
        this.paradoxCascadeFactJpaRepository = paradoxCascadeFactJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public EraScoringContext getRequired(UUID gameId, int eraNumber) {
        var players = playerJpaRepository.findAllByGameId(gameId).stream()
                .map(entity -> new PlayerFaction(entity.getPlayerId(), Faction.valueOf(entity.getFaction())))
                .toList();
        if (players.isEmpty()) {
            throw new EraScoringContextNotFoundException(gameId, eraNumber);
        }

        var eventOutcomes = buildEventOutcomeFacts(gameId, eraNumber);

        var unconsumedChainFacts = chainFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId);
        var chainFacts = unconsumedChainFacts.stream()
                .map(entity -> new ChainScoringFact(
                        entity.getPlayerId(),
                        entity.getChainId(),
                        ScoreReason.valueOf(entity.getReason()),
                        entity.getEraNumber()))
                .toList();
        unconsumedChainFacts.forEach(entity -> entity.setConsumed(true));
        chainFactJpaRepository.saveAll(unconsumedChainFacts);

        var unconsumedActionFacts = actionFactJpaRepository.findAllUnconsumedWithLock(gameId, eraNumber);
        var actionFacts = unconsumedActionFacts.stream()
                .map(entity -> new ActionScoringFact(
                        entity.getPlayerId(),
                        Faction.valueOf(entity.getFaction()),
                        ScoreReason.valueOf(entity.getReason())))
                .toList();
        unconsumedActionFacts.forEach(entity -> entity.setConsumed(true));
        actionFactJpaRepository.saveAll(unconsumedActionFacts);

        var annihilationFacts = annihilatedOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, eraNumber).stream()
                .map(entity -> new AnnihilationFact(entity.getEventId(), entity.getOutcomeId(), entity.getPlayerId()))
                .toList();

        var fulfillmentDeclarations =
                fulfillmentDeclarationJpaRepository.findAllByGameIdAndEraNumber(gameId, eraNumber).stream()
                        .map(entity -> new FulfillmentDeclarationFact(entity.getPlayerId(), entity.getTargetEventId()))
                        .toList();

        var corruptCorrelations =
                corruptCorrelationJpaRepository.findAllByGameIdAndEraNumber(gameId, eraNumber).stream()
                        .map(entity -> new CorruptCorrelationFact(
                                entity.getCorruptingPlayerId(),
                                entity.getTargetPlayerId(),
                                entity.getCardInstanceId(),
                                entity.getTargetEventId(),
                                entity.getSourceOutcomeId(),
                                entity.getTargetOutcomeId(),
                                entity.getTookEffect()))
                        .toList();

        var unconsumedParadoxCascadeFacts =
                paradoxCascadeFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId);
        var paradoxCascadeFacts = unconsumedParadoxCascadeFacts.stream()
                .map(entity -> new ParadoxCascadeScoringFact(
                        entity.getParadoxId(),
                        entity.getAffectedEventId(),
                        entity.getDetonatedByPlayerIds(),
                        entity.getEraNumber()))
                .toList();
        unconsumedParadoxCascadeFacts.forEach(entity -> entity.setConsumed(true));
        paradoxCascadeFactJpaRepository.saveAll(unconsumedParadoxCascadeFacts);

        return new EraScoringContext(
                gameId,
                eraNumber,
                players,
                eventOutcomes,
                actionFacts,
                chainFacts,
                annihilationFacts,
                fulfillmentDeclarations,
                corruptCorrelations,
                paradoxCascadeFacts);
    }

    private List<EventOutcomeFact> buildEventOutcomeFacts(UUID gameId, int eraNumber) {
        var baselines = eventOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, eraNumber);
        if (baselines.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> annihilatedCountsByEvent =
                annihilatedOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, eraNumber).stream()
                        .collect(Collectors.groupingBy(
                                ScoringContextAnnihilatedOutcomeJpaEntity::getEventId, Collectors.counting()));

        // endingOutcomeCount is derived from this distinct-annihilation ledger, not from any
        // OutcomeApplied.finalProbabilities payload — that list can still include annihilated outcomes.
        Map<UUID, UUID> winningOutcomesByEvent =
                outcomeInboxJpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(gameId, eraNumber).stream()
                        .collect(Collectors.toMap(
                                ScoringTimelineOutcomeInboxJpaEntity::getEventId,
                                ScoringTimelineOutcomeInboxJpaEntity::getWinningOutcomeId));

        return baselines.stream()
                .map(baseline -> {
                    var annihilatedCount = annihilatedCountsByEvent
                            .getOrDefault(baseline.getEventId(), 0L)
                            .intValue();
                    return new EventOutcomeFact(
                            baseline.getEventId(),
                            winningOutcomesByEvent.get(baseline.getEventId()),
                            baseline.getWrittenOutcomeId(),
                            baseline.getStartingOutcomeCount(),
                            baseline.getStartingOutcomeCount() - annihilatedCount);
                })
                .toList();
    }

    @Override
    public int expectedOutcomeCount(UUID gameId, int eraNumber) {
        return eraOutcomeExpectationJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .map(ScoringContextEraOutcomeExpectationJpaEntity::getExpectedOutcomeCount)
                .orElseThrow(() -> new EraScoringContextNotFoundException(gameId, eraNumber));
    }

    @Override
    @Transactional
    public void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction) {
        // Native ON CONFLICT, not check-then-insert: Modulith listeners are at-least-once and a
        // concurrent redelivery must not create a duplicate (game_id, player_id) row.
        playerJpaRepository.upsert(UUID.randomUUID(), gameId, playerId, faction.name());
    }

    @Override
    @Transactional
    public void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount) {
        eraOutcomeExpectationJpaRepository.upsert(UUID.randomUUID(), gameId, eraNumber, expectedOutcomeCount);
    }

    @Override
    public void recordChainFact(UUID gameId, UUID playerId, UUID chainId, ScoreReason reason, int eraNumber) {
        var entity = new ScoringContextChainFactJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(gameId);
        entity.setPlayerId(playerId);
        entity.setChainId(chainId);
        entity.setReason(reason.name());
        entity.setEraNumber(eraNumber);
        entity.setConsumed(false);
        chainFactJpaRepository.save(entity);
    }

    @Override
    public void recordParadoxCascadeFact(
            UUID gameId, int eraNumber, UUID paradoxId, UUID affectedEventId, List<UUID> detonatedByPlayerIds) {
        var entity = new ScoringContextParadoxCascadeFactJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(gameId);
        entity.setEraNumber(eraNumber);
        entity.setParadoxId(paradoxId);
        entity.setAffectedEventId(affectedEventId);
        entity.setDetonatedByPlayerIds(detonatedByPlayerIds);
        entity.setConsumed(false);
        paradoxCascadeFactJpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void upsertEventOutcomeBaseline(UUID gameId, int eraNumber, UUID eventId, int startingOutcomeCount) {
        eventOutcomeJpaRepository.upsertBaseline(UUID.randomUUID(), gameId, eraNumber, eventId, startingOutcomeCount);
    }

    @Override
    // REQUIRES_NEW, not the default REQUIRED: onEraActionFactsFinalized calls this and then
    // markActionFactsReady/tryComplete() in the same @ApplicationModuleListener transaction. If
    // tryComplete() throws — e.g. a transient EraScoringContextNotFoundException while EventsDrawn's
    // own listener is still landing — that must not roll back a fact this method already wrote.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertWrittenOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId) {
        eventOutcomeJpaRepository.insertWrittenOutcomeIfFirst(
                UUID.randomUUID(), gameId, eraNumber, eventId, outcomeId, playerId);
    }

    @Override
    // REQUIRES_NEW for the same reason as upsertWrittenOutcome above.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnnihilatedOutcome(UUID gameId, int eraNumber, UUID eventId, UUID outcomeId, UUID playerId) {
        annihilatedOutcomeJpaRepository.insertIfAbsent(
                UUID.randomUUID(), gameId, eraNumber, eventId, outcomeId, playerId);
    }

    @Override
    @Transactional
    public void recordActionFact(UUID gameId, int eraNumber, UUID playerId, Faction faction, ScoreReason reason) {
        insertActionFact(gameId, eraNumber, playerId, faction, reason);
    }

    private void insertActionFact(UUID gameId, int eraNumber, UUID playerId, Faction faction, ScoreReason reason) {
        actionFactJpaRepository.insertIfAbsent(
                UUID.randomUUID(), gameId, eraNumber, playerId, faction.name(), reason.name());
    }

    @Override
    @Transactional
    public void upsertActivistDeclaration(ActivistDeclarationRecorded declaration) {
        activistDeclarationJpaRepository.insertIfAbsent(
                UUID.randomUUID(),
                declaration.gameId(),
                declaration.eraNumber(),
                declaration.playerId(),
                declaration.mode().name(),
                declaration.targetEventId(),
                declaration.targetOutcomeId());
    }

    @Override
    @Transactional
    public void saveEraResolutionCompleted(EraResolutionCompleted resolution) {
        resolutionBarrierJpaRepository.insertIfAbsent(
                UUID.randomUUID(),
                resolution.gameId(),
                resolution.eraNumber(),
                objectMapper.writeValueAsString(resolution));
    }

    @Override
    public boolean eraResolutionCompleted(UUID gameId, int eraNumber) {
        return resolutionBarrierJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .isPresent();
    }

    @Override
    public int requiredAppliedOutcomeCount(UUID gameId, int eraNumber) {
        return resolutionBarrierJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .map(ScoringTimelineResolutionBarrierJpaEntity::payload)
                .map(EraResolutionCompleted::terminalResolutions)
                .map(resolutions -> (int) resolutions.stream()
                        .filter(resolution ->
                                resolution.terminalState() == EraResolutionCompleted.TerminalState.OUTCOME_APPLIED)
                        .count())
                .orElse(0);
    }

    @Override
    @Transactional
    public List<ActivistDeclarationResolved> resolveActivistDeclarations(UUID gameId, int eraNumber) {
        var terminalResolutions = resolutionBarrierJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .map(ScoringTimelineResolutionBarrierJpaEntity::payload)
                .map(EraResolutionCompleted::terminalResolutions)
                .orElse(List.of());
        return activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, eraNumber).stream()
                .map(declaration -> terminalResolutions.stream()
                        .filter(resolution -> resolution.eventId().equals(declaration.getTargetEventId()))
                        .findFirst()
                        .flatMap(resolution ->
                                resolution.terminalState() != EraResolutionCompleted.TerminalState.OUTCOME_APPLIED
                                        ? java.util.Optional.of(resolveDeclaration(declaration, null))
                                        : outcomeInboxJpaRepository
                                                .findByGameIdAndEraNumberAndEventId(
                                                        gameId, eraNumber, declaration.getTargetEventId())
                                                .map(outcome -> resolveDeclaration(declaration, outcome))))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Override
    public boolean activistDeclarationsResolved(UUID gameId, int eraNumber) {
        return !activistDeclarationJpaRepository.existsByGameIdAndEraNumberAndResolutionSucceededIsNull(
                gameId, eraNumber);
    }

    private ActivistDeclarationResolved resolveDeclaration(
            ScoringContextActivistDeclarationJpaEntity declaration, ScoringTimelineOutcomeInboxJpaEntity outcome) {
        var succeeded = outcome != null && declaration.getTargetOutcomeId().equals(outcome.getWinningOutcomeId());
        declaration.setResolutionSucceeded(succeeded);
        if (succeeded) {
            var reason = declaration.getMode().equals("RALLY")
                    ? ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY
                    : ScoreReason.DECLARED_OUTCOME_WON;
            insertActionFact(
                    declaration.getGameId(),
                    declaration.getEraNumber(),
                    declaration.getPlayerId(),
                    Faction.ACTIVISTS,
                    reason);
        }
        return new ActivistDeclarationResolved(
                declaration.getGameId(), declaration.getEraNumber(), declaration.getPlayerId(), succeeded);
    }

    @Override
    public boolean actionFactsReady(UUID gameId, int eraNumber) {
        return actionFactsReadyJpaRepository.existsById(new GameEraKey(gameId, eraNumber));
    }

    @Override
    // REQUIRES_NEW, not the default REQUIRED: this must commit and become durable independently of
    // whatever the caller does afterward (e.g. ScoringContextProjectionEventListener.onActionRoundClosed
    // also calls EraScoringCompletionChecker.tryComplete() in the same @ApplicationModuleListener
    // transaction). If tryComplete() throws — e.g. the era's context genuinely is not ready yet — that
    // must not roll back the readiness marker this method just wrote.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markActionFactsReady(UUID gameId, int eraNumber) {
        actionFactsReadyJpaRepository.insertIfAbsent(gameId, eraNumber);
    }

    @Override
    @Transactional
    public void recordRevisionistAction(
            UUID gameId, int eraNumber, UUID playerId, SpecialAction action, UUID targetEventId, UUID targetOutcomeId) {
        revisionistActionJpaRepository.insertIfAbsent(
                UUID.randomUUID(), gameId, eraNumber, playerId, action.name(), targetEventId, targetOutcomeId);
    }

    @Override
    @Transactional
    public void resolveRevisionistActions(UUID gameId, int eraNumber) {
        var terminalResolutions = resolutionBarrierJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .map(ScoringTimelineResolutionBarrierJpaEntity::payload)
                .map(EraResolutionCompleted::terminalResolutions)
                .orElse(List.of());
        for (var action : revisionistActionJpaRepository.findAllUnresolvedWithLock(gameId, eraNumber)) {
            terminalResolutions.stream()
                    .filter(resolution -> resolution.eventId().equals(action.getTargetEventId()))
                    .findFirst()
                    .ifPresent(resolution -> resolveRevisionistAction(action, resolution));
        }
    }

    private void resolveRevisionistAction(
            ScoringContextRevisionistActionJpaEntity action, EraResolutionCompleted.TerminalResolution resolution) {
        if (resolution.terminalState() != EraResolutionCompleted.TerminalState.OUTCOME_APPLIED) {
            action.setResolved(false);
            return;
        }
        outcomeInboxJpaRepository
                .findByGameIdAndEraNumberAndEventId(
                        action.getGameId(), action.getEraNumber(), action.getTargetEventId())
                .ifPresent(outcome -> {
                    action.setResolved(true);
                    if (action.getTargetOutcomeId().equals(outcome.getWinningOutcomeId())) {
                        var reason = SpecialAction.REWRITE.name().equals(action.getAction())
                                ? ScoreReason.SECRET_OUTCOME_WON
                                : ScoreReason.MIMIC_CONTRIBUTED_TO_WIN;
                        insertActionFact(
                                action.getGameId(),
                                action.getEraNumber(),
                                action.getPlayerId(),
                                Faction.REVISIONISTS,
                                reason);
                    }
                });
    }

    @Override
    public boolean revisionistActionsResolved(UUID gameId, int eraNumber) {
        return !revisionistActionJpaRepository.existsByGameIdAndEraNumberAndResolvedIsNull(gameId, eraNumber);
    }

    @Override
    // REQUIRES_NEW for the same reason as upsertWrittenOutcome/recordAnnihilatedOutcome above:
    // onEraActionFactsFinalized calls this and then markActionFactsReady/tryComplete() in the same
    // listener transaction. If tryComplete() throws, that must not roll back a declaration this
    // method already wrote.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFulfillmentDeclaration(UUID gameId, int eraNumber, UUID playerId, UUID targetEventId) {
        fulfillmentDeclarationJpaRepository.insertIfAbsent(
                UUID.randomUUID(), gameId, eraNumber, playerId, targetEventId);
    }

    @Override
    @Transactional
    public void recordCorruptCorrelation(
            UUID gameId,
            int eraNumber,
            UUID corruptingPlayerId,
            UUID targetPlayerId,
            UUID cardInstanceId,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {
        corruptCorrelationJpaRepository.insertIfAbsent(
                UUID.randomUUID(),
                gameId,
                eraNumber,
                corruptingPlayerId,
                targetPlayerId,
                cardInstanceId,
                targetEventId,
                sourceOutcomeId,
                targetOutcomeId);
    }

    @Override
    @Transactional
    public void confirmCorruptInversion(
            UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID cardInstanceId, boolean tookEffect) {
        var updated = corruptCorrelationJpaRepository.confirmInversion(
                gameId, eraNumber, corruptingPlayerId, cardInstanceId, tookEffect);
        if (updated == 0) {
            // The confirmation arrived before its correlation row (recordCorruptCorrelation) was
            // written — e.g. redelivered ahead of the era's own final-round close. Nothing to update
            // yet; the correlation is not lost (recordCorruptCorrelation still inserts it), but this
            // confirmation itself has no row to attach to and is silently dropped, so surface it.
            log.warn(
                    "confirmCorruptInversion found no matching correlation for game {} era {} corrupting player {}"
                            + " card {} — confirmation dropped",
                    gameId,
                    eraNumber,
                    corruptingPlayerId,
                    cardInstanceId);
        }
    }
}
