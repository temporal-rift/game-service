package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryHandSelectionJpaRepository extends InMemoryJpaRepository<HandSelectionJpaEntity, UUID>
        implements HandSelectionJpaRepository {

    private static final String OPEN = "OPEN";

    private final ObjectMapper objectMapper;

    InMemoryHandSelectionJpaRepository(InMemoryDatabase database, ObjectMapper objectMapper) {
        super(
                database,
                TableDefinition.<UUID, HandSelectionJpaEntity>of("hand_selection", selection -> selection.id)
                        .unique(
                                "game_era_player",
                                selection -> List.of(selection.gameId, selection.eraNumber, selection.playerId)));
        this.objectMapper = objectMapper;
    }

    @Override
    public int insertIfAbsent(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            String status,
            Instant selectionExpiresAt,
            String dealtCards) {
        var selection = new HandSelectionJpaEntity();
        selection.id = id;
        selection.gameId = gameId;
        selection.eraNumber = eraNumber;
        selection.playerId = playerId;
        selection.status = status;
        selection.selectionExpiresAt = selectionExpiresAt;
        selection.dealtCards =
                objectMapper.readValue(dealtCards, new TypeReference<List<StoredHandSelectionCard>>() {});
        return insertRowIfAbsent(selection) ? 1 : 0;
    }

    @Override
    public Optional<HandSelectionJpaEntity> findWithLock(UUID gameId, int eraNumber, UUID playerId) {
        return findFirstForUpdate(selection -> selection.gameId.equals(gameId)
                && selection.eraNumber == eraNumber
                && selection.playerId.equals(playerId));
    }

    @Override
    public List<UUID> findOpenDueIds(Instant now) {
        return findMatching(selection -> OPEN.equals(selection.status) && !selection.selectionExpiresAt.isAfter(now))
                .stream()
                .map(selection -> selection.id)
                .toList();
    }

    @Override
    public Optional<HandSelectionJpaEntity> findByIdWithLock(UUID id) {
        return findByIdForUpdate(id);
    }
}
