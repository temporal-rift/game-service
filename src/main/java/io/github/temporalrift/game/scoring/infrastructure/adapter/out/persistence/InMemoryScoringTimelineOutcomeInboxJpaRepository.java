package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.UuidOrder;

@InMemoryRepository
class InMemoryScoringTimelineOutcomeInboxJpaRepository
        extends InMemoryJpaRepository<ScoringTimelineOutcomeInboxJpaEntity, UUID>
        implements ScoringTimelineOutcomeInboxJpaRepository {

    private final ObjectMapper objectMapper;

    InMemoryScoringTimelineOutcomeInboxJpaRepository(InMemoryDatabase database, ObjectMapper objectMapper) {
        super(
                database,
                TableDefinition.<UUID, ScoringTimelineOutcomeInboxJpaEntity>of(
                                "scoring_timeline_outcome_inbox", ScoringTimelineOutcomeInboxJpaEntity::getId)
                        .unique(
                                "game_era_event",
                                inbox -> key(inbox.getGameId(), inbox.getEraNumber(), inbox.getEventId())));
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ScoringTimelineOutcomeInboxJpaEntity> findAllByGameIdAndEraNumberOrderByEventIdAsc(
            UUID gameId, int eraNumber) {
        return findMatching(
                inbox -> inbox.getGameId().equals(gameId) && inbox.getEraNumber() == eraNumber,
                Comparator.comparing(ScoringTimelineOutcomeInboxJpaEntity::getEventId, UuidOrder.POSTGRES));
    }

    @Override
    public Optional<ScoringTimelineOutcomeInboxJpaEntity> findByGameIdAndEraNumberAndEventId(
            UUID gameId, int eraNumber, UUID eventId) {
        return findFirst(inbox -> inbox.getGameId().equals(gameId)
                && inbox.getEraNumber() == eraNumber
                && inbox.getEventId().equals(eventId));
    }

    @Override
    public int insertIfAbsent(
            UUID id, UUID gameId, int eraNumber, UUID eventId, UUID winningOutcomeId, String payload) {
        var inbox = new ScoringTimelineOutcomeInboxJpaEntity();
        inbox.setId(id);
        inbox.setGameId(gameId);
        inbox.setEraNumber(eraNumber);
        inbox.setEventId(eventId);
        inbox.setWinningOutcomeId(winningOutcomeId);
        inbox.setPayload(objectMapper.readValue(payload, OutcomeApplied.class));
        return insertRowIfAbsent(inbox) ? 1 : 0;
    }
}
