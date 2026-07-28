package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.Faction;

@Component
class EraScoringContextRepositoryAdapter implements EraScoringContextRepository {

    private final ScoringContextPlayerJpaRepository playerJpaRepository;
    private final ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository;
    private final ScoringContextChainFactJpaRepository chainFactJpaRepository;
    private final ScoringContextEventOutcomeJpaRepository eventOutcomeJpaRepository;
    private final ScoringContextAnnihilatedOutcomeJpaRepository annihilatedOutcomeJpaRepository;
    private final ScoringTimelineOutcomeInboxJpaRepository outcomeInboxJpaRepository;
    private final ScoringContextActionFactsReadyJpaRepository actionFactsReadyJpaRepository;

    EraScoringContextRepositoryAdapter(
            ScoringContextPlayerJpaRepository playerJpaRepository,
            ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository,
            ScoringContextChainFactJpaRepository chainFactJpaRepository,
            ScoringContextEventOutcomeJpaRepository eventOutcomeJpaRepository,
            ScoringContextAnnihilatedOutcomeJpaRepository annihilatedOutcomeJpaRepository,
            ScoringTimelineOutcomeInboxJpaRepository outcomeInboxJpaRepository,
            ScoringContextActionFactsReadyJpaRepository actionFactsReadyJpaRepository) {
        this.playerJpaRepository = playerJpaRepository;
        this.eraOutcomeExpectationJpaRepository = eraOutcomeExpectationJpaRepository;
        this.chainFactJpaRepository = chainFactJpaRepository;
        this.eventOutcomeJpaRepository = eventOutcomeJpaRepository;
        this.annihilatedOutcomeJpaRepository = annihilatedOutcomeJpaRepository;
        this.outcomeInboxJpaRepository = outcomeInboxJpaRepository;
        this.actionFactsReadyJpaRepository = actionFactsReadyJpaRepository;
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

        return new EraScoringContext(gameId, eraNumber, players, eventOutcomes, List.of(), chainFacts);
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
    public boolean actionFactsReady(UUID gameId, int eraNumber) {
        return actionFactsReadyJpaRepository.existsById(
                new ScoringContextActionFactsReadyJpaEntity.ScoringContextActionFactsReadyKey(gameId, eraNumber));
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
}
