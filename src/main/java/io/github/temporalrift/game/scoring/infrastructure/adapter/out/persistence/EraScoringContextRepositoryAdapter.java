package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.Faction;

@Component
class EraScoringContextRepositoryAdapter implements EraScoringContextRepository {

    private final ScoringContextPlayerJpaRepository playerJpaRepository;
    private final ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository;
    private final ScoringContextChainFactJpaRepository chainFactJpaRepository;

    EraScoringContextRepositoryAdapter(
            ScoringContextPlayerJpaRepository playerJpaRepository,
            ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository,
            ScoringContextChainFactJpaRepository chainFactJpaRepository) {
        this.playerJpaRepository = playerJpaRepository;
        this.eraOutcomeExpectationJpaRepository = eraOutcomeExpectationJpaRepository;
        this.chainFactJpaRepository = chainFactJpaRepository;
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

        return new EraScoringContext(gameId, eraNumber, players, List.of(), List.of(), chainFacts);
    }

    @Override
    public int expectedOutcomeCount(UUID gameId, int eraNumber) {
        return eraOutcomeExpectationJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .map(ScoringContextEraOutcomeExpectationJpaEntity::getExpectedOutcomeCount)
                .orElseThrow(() -> new EraScoringContextNotFoundException(gameId, eraNumber));
    }

    @Override
    public void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction) {
        var entity = playerJpaRepository
                .findByGameIdAndPlayerId(gameId, playerId)
                .orElseGet(ScoringContextPlayerJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setGameId(gameId);
            entity.setPlayerId(playerId);
        }
        entity.setFaction(faction.name());
        playerJpaRepository.save(entity);
    }

    @Override
    public void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount) {
        var entity = eraOutcomeExpectationJpaRepository
                .findByGameIdAndEraNumber(gameId, eraNumber)
                .orElseGet(ScoringContextEraOutcomeExpectationJpaEntity::new);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setGameId(gameId);
            entity.setEraNumber(eraNumber);
        }
        entity.setExpectedOutcomeCount(expectedOutcomeCount);
        eraOutcomeExpectationJpaRepository.save(entity);
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
}
