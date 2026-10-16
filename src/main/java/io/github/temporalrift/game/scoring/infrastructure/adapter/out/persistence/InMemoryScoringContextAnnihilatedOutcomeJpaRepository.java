package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.event.AnnihilationResolved;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextAnnihilatedOutcomeJpaRepository
        extends InMemoryJpaRepository<ScoringContextAnnihilatedOutcomeJpaEntity, UUID>
        implements ScoringContextAnnihilatedOutcomeJpaRepository {

    InMemoryScoringContextAnnihilatedOutcomeJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextAnnihilatedOutcomeJpaEntity>of(
                                "scoring_context_annihilated_outcome", ScoringContextAnnihilatedOutcomeJpaEntity::getId)
                        .unique(
                                "game_era_event_outcome_player",
                                outcome -> key(
                                        outcome.getGameId(),
                                        outcome.getEraNumber(),
                                        outcome.getEventId(),
                                        outcome.getOutcomeId(),
                                        outcome.getPlayerId())));
    }

    @Override
    public List<ScoringContextAnnihilatedOutcomeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findMatching(outcome -> outcome.getGameId().equals(gameId) && outcome.getEraNumber() == eraNumber);
    }

    @Override
    public void insertIfAbsent(UUID id, AnnihilationResolved resolution) {
        var outcome = new ScoringContextAnnihilatedOutcomeJpaEntity();
        outcome.setId(id);
        outcome.setGameId(resolution.gameId());
        outcome.setEraNumber(resolution.eraNumber());
        outcome.setEventId(resolution.targetEventId());
        outcome.setOutcomeId(resolution.targetOutcomeId());
        outcome.setPlayerId(resolution.annihilatingPlayerId());
        outcome.setErased(resolution.erased());
        outcome.setWasLeading(resolution.wasLeading());
        insertRowIfAbsent(outcome);
    }
}
