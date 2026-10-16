package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringTimelineResolutionBarrierJpaRepository
        extends InMemoryJpaRepository<ScoringTimelineResolutionBarrierJpaEntity, UUID>
        implements ScoringTimelineResolutionBarrierJpaRepository {

    private final ScoringEraCompletionJpaRepository completions;
    private final ObjectMapper objectMapper;

    InMemoryScoringTimelineResolutionBarrierJpaRepository(
            InMemoryDatabase database, ScoringEraCompletionJpaRepository completions, ObjectMapper objectMapper) {
        super(
                database,
                TableDefinition.<UUID, ScoringTimelineResolutionBarrierJpaEntity>of(
                                "scoring_timeline_resolution_barrier", ScoringTimelineResolutionBarrierJpaEntity::getId)
                        .unique("game_era", barrier -> key(barrier.getGameId(), barrier.getEraNumber())));
        this.completions = completions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ScoringTimelineResolutionBarrierJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findFirst(barrier -> barrier.getGameId().equals(gameId) && barrier.getEraNumber() == eraNumber);
    }

    @Override
    public int insertIfAbsent(UUID id, UUID gameId, int eraNumber, String payload) {
        var barrier = ScoringTimelineResolutionBarrierJpaEntity.restore(
                id,
                gameId,
                eraNumber,
                parseJson("payload", () -> objectMapper.readValue(payload, EraResolutionCompleted.class)));
        return insertRowIfAbsent(barrier) ? 1 : 0;
    }

    @Override
    public List<PendingCompletion> findResolvedErasNotYetScored() {
        return findMatching(
                        barrier -> !completions.existsById(new GameEraKey(barrier.getGameId(), barrier.getEraNumber())))
                .stream()
                .<PendingCompletion>map(barrier -> new Pending(barrier.getGameId(), barrier.getEraNumber()))
                .toList();
    }

    private record Pending(UUID gameId, int eraNumber) implements PendingCompletion {

        @Override
        public UUID getGameId() {
            return gameId;
        }

        @Override
        public int getEraNumber() {
            return eraNumber;
        }
    }
}
