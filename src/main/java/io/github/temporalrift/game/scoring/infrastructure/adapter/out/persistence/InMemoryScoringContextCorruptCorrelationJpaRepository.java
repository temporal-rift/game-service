package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextCorruptCorrelationJpaRepository
        extends InMemoryJpaRepository<ScoringContextCorruptCorrelationJpaEntity, UUID>
        implements ScoringContextCorruptCorrelationJpaRepository {

    InMemoryScoringContextCorruptCorrelationJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextCorruptCorrelationJpaEntity>of(
                                "scoring_context_corrupt_correlation", ScoringContextCorruptCorrelationJpaEntity::getId)
                        .unique(
                                "game_era_corrupting_card",
                                correlation -> key(
                                        correlation.getGameId(),
                                        correlation.getEraNumber(),
                                        correlation.getCorruptingPlayerId(),
                                        correlation.getCardInstanceId())));
    }

    @Override
    public List<ScoringContextCorruptCorrelationJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findMatching(
                correlation -> correlation.getGameId().equals(gameId) && correlation.getEraNumber() == eraNumber);
    }

    @Override
    public void insertIfAbsent(
            UUID id, UUID gameId, int eraNumber, EraActionFactsFinalized.CorruptCorrelationFact correlation) {
        var row = new ScoringContextCorruptCorrelationJpaEntity();
        row.setId(id);
        row.setGameId(gameId);
        row.setEraNumber(eraNumber);
        row.setCorruptingPlayerId(correlation.corruptingPlayerId());
        row.setTargetPlayerId(correlation.targetPlayerId());
        row.setCardInstanceId(correlation.cardInstanceId());
        row.setTargetEventId(correlation.targetEventId());
        row.setSourceOutcomeId(correlation.sourceOutcomeId());
        row.setTargetOutcomeId(correlation.targetOutcomeId());
        insertRowIfAbsent(row);
    }

    @Override
    public int confirmInversion(
            UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID cardInstanceId, boolean tookEffect) {
        return confirm(
                correlation -> correlation.getGameId().equals(gameId)
                        && correlation.getEraNumber() == eraNumber
                        && correlation.getCorruptingPlayerId().equals(corruptingPlayerId)
                        && correlation.getCardInstanceId().equals(cardInstanceId),
                tookEffect);
    }

    @Override
    public int confirmInversionForTarget(
            UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetEventId, boolean tookEffect) {
        return confirm(
                correlation -> correlation.getGameId().equals(gameId)
                        && correlation.getEraNumber() == eraNumber
                        && correlation.getCorruptingPlayerId().equals(corruptingPlayerId)
                        && correlation.getTargetEventId().equals(targetEventId),
                tookEffect);
    }

    private int confirm(Predicate<ScoringContextCorruptCorrelationJpaEntity> matches, boolean tookEffect) {
        var rows = findMatchingForUpdate(
                matches, Comparator.comparing(row -> row.getId().toString()));
        rows.forEach(row -> {
            row.setTookEffect(tookEffect);
            save(row);
        });
        return rows.size();
    }
}
