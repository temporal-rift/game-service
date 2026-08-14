package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionStatus;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.shared.HandSelected;

@Component
class HandSelectionRepositoryAdapter implements HandSelectionRepository {
    private final HandSelectionJpaRepository repository;

    HandSelectionRepositoryAdapter(HandSelectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public HandSelection save(HandSelection selection) {
        repository.save(toEntity(selection));
        return selection;
    }

    @Override
    public Optional<HandSelection> findByGameIdAndEraNumberAndPlayerIdWithLock(
            UUID gameId, int eraNumber, UUID playerId) {
        return repository.findWithLock(gameId, eraNumber, playerId).map(this::toDomain);
    }

    @Override
    public List<UUID> findOpenDueIds(Instant now) {
        return repository.findOpenDueIds(now);
    }

    @Override
    public Optional<HandSelection> findByIdWithLock(UUID id) {
        return repository.findByIdWithLock(id).map(this::toDomain);
    }

    private HandSelectionJpaEntity toEntity(HandSelection selection) {
        var entity = new HandSelectionJpaEntity();
        entity.id = selection.id();
        entity.gameId = selection.gameId();
        entity.eraNumber = selection.eraNumber();
        entity.playerId = selection.playerId();
        entity.status = selection.status().name();
        entity.selectionExpiresAt = selection.selectionExpiresAt();
        entity.dealtCards = selection.dealtCards().stream()
                .map(StoredHandSelectionCard::fromDomain)
                .toList();
        entity.selectedCards = selection.selectedCards().stream()
                .map(StoredHandSelectionCard::fromDomain)
                .toList();
        entity.selectionOrigin = selection.selectionOrigin() == null
                ? null
                : selection.selectionOrigin().name();
        return entity;
    }

    private HandSelection toDomain(HandSelectionJpaEntity entity) {
        return new HandSelection(
                entity.id,
                entity.gameId,
                entity.eraNumber,
                entity.playerId,
                HandSelectionStatus.valueOf(entity.status),
                entity.selectionExpiresAt,
                entity.dealtCards.stream()
                        .map(StoredHandSelectionCard::toDomain)
                        .toList(),
                entity.selectedCards == null
                        ? List.of()
                        : entity.selectedCards.stream()
                                .map(StoredHandSelectionCard::toDomain)
                                .toList(),
                entity.selectionOrigin == null ? null : HandSelected.SelectionOrigin.valueOf(entity.selectionOrigin));
    }
}
