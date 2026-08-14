package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.handselection.HandSelectionStatus;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.HandSelected;

@ExtendWith(MockitoExtension.class)
class HandSelectionRepositoryAdapterTest {
    @Mock
    HandSelectionJpaRepository jpaRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    HandSelectionRepositoryAdapter adapter;

    @Test
    void save_mapsPendingDealAndTerminalSelection() {
        var selection = selection();

        adapter.save(selection);

        var captor = ArgumentCaptor.forClass(HandSelectionJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.id).isEqualTo(selection.id());
        assertThat(entity.status).isEqualTo(HandSelectionStatus.OPEN.name());
        assertThat(entity.dealtCards).hasSize(7);
        assertThat(entity.dealtCards)
                .extracting(StoredHandSelectionCard::cardGrade)
                .contains(CardGrade.III.name());
        assertThat(entity.selectedCards).isEmpty();

        adapter.save(selection.selectRandomOnExpiry(Instant.EPOCH, new java.util.Random(4)));
        then(jpaRepository).should(times(2)).save(captor.capture());
        var resolved = captor.getAllValues().getLast();
        assertThat(resolved.status).isEqualTo(HandSelectionStatus.SELECTED.name());
        assertThat(resolved.selectionOrigin).isEqualTo(HandSelected.SelectionOrigin.TIMEOUT_RANDOM.name());
        assertThat(resolved.selectedCards).hasSize(5);
    }

    @Test
    void findWithLock_mapsPersistedFinalSelection() {
        var openSelection = selection();
        var selection = openSelection.select(
                openSelection.dealtCards().stream()
                        .limit(5)
                        .map(HandDealt.CardInstance::cardInstanceId)
                        .collect(java.util.stream.Collectors.toSet()),
                Instant.EPOCH.minusSeconds(1));
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
        entity.selectionOrigin = selection.selectionOrigin().name();
        given(jpaRepository.findWithLock(selection.gameId(), selection.eraNumber(), selection.playerId()))
                .willReturn(Optional.of(entity));

        var loaded = adapter.findByGameIdAndEraNumberAndPlayerIdWithLock(
                selection.gameId(), selection.eraNumber(), selection.playerId());

        assertThat(loaded).contains(selection);
        assertThat(loaded.orElseThrow().selectedCards())
                .extracting(HandDealt.CardInstance::grade)
                .contains(CardGrade.III);
    }

    @Test
    void createIfAbsent_mapsAnOpenSelectionToTheAtomicInsert() {
        var selection = selection();
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(jpaRepository.insertIfAbsent(
                        selection.id(),
                        selection.gameId(),
                        selection.eraNumber(),
                        selection.playerId(),
                        HandSelectionStatus.OPEN.name(),
                        selection.selectionExpiresAt(),
                        "[]"))
                .willReturn(1);

        assertThat(adapter.createIfAbsent(selection)).isTrue();
    }

    private static HandSelection selection() {
        var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(slot -> new HandDealt.CardInstance(
                        UUID.randomUUID(), CardType.PUSH, slot == 1 ? CardGrade.III : CardGrade.I, slot))
                .toList();
        return HandSelection.open(new HandDealt(UUID.randomUUID(), 1, UUID.randomUUID(), Instant.EPOCH, cards));
    }
}
