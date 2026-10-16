package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextCorruptPendingConfirmationJpaRepository
        extends InMemoryJpaRepository<ScoringContextCorruptPendingConfirmationJpaEntity, UUID>
        implements ScoringContextCorruptPendingConfirmationJpaRepository {

    InMemoryScoringContextCorruptPendingConfirmationJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextCorruptPendingConfirmationJpaEntity>of(
                                "scoring_context_corrupt_pending_confirmation",
                                ScoringContextCorruptPendingConfirmationJpaEntity::getId)
                        .unique(
                                "game_era_corrupting_target",
                                pending -> key(
                                        pending.getGameId(),
                                        pending.getEraNumber(),
                                        pending.getCorruptingPlayerId(),
                                        pending.getTargetEventId())));
    }

    @Override
    public Optional<ScoringContextCorruptPendingConfirmationJpaEntity>
            findByGameIdAndEraNumberAndCorruptingPlayerIdAndTargetEventId(
                    UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetEventId) {
        return findFirst(pending -> matches(pending, gameId, eraNumber, corruptingPlayerId, targetEventId));
    }

    @Override
    public void insertIfAbsent(
            UUID id, UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetEventId, boolean tookEffect) {
        var pending = new ScoringContextCorruptPendingConfirmationJpaEntity();
        pending.setId(id);
        pending.setGameId(gameId);
        pending.setEraNumber(eraNumber);
        pending.setCorruptingPlayerId(corruptingPlayerId);
        pending.setTargetEventId(targetEventId);
        pending.setTookEffect(tookEffect);
        insertRowIfAbsent(pending);
    }

    @Override
    public void deletePending(UUID gameId, int eraNumber, UUID corruptingPlayerId, UUID targetEventId) {
        deleteMatching(pending -> matches(pending, gameId, eraNumber, corruptingPlayerId, targetEventId));
    }

    private static boolean matches(
            ScoringContextCorruptPendingConfirmationJpaEntity pending,
            UUID gameId,
            int eraNumber,
            UUID corruptingPlayerId,
            UUID targetEventId) {
        return pending.getGameId().equals(gameId)
                && pending.getEraNumber() == eraNumber
                && pending.getCorruptingPlayerId().equals(corruptingPlayerId)
                && pending.getTargetEventId().equals(targetEventId);
    }
}
