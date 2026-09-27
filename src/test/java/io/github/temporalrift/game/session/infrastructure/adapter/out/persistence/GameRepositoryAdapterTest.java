package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.game.DrawnFutureEvent;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.shared.domain.model.CarryOverState;

@ExtendWith(MockitoExtension.class)
class GameRepositoryAdapterTest {

    @Mock
    GameJpaRepository jpaRepository;

    @InjectMocks
    GameRepositoryAdapter adapter;

    @Test
    void save_mapsEveryGameFieldOntoTheJpaEntity() {
        var id = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var carriedEventId = UUID.randomUUID();
        var cascadedEventId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var outcomeIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var game = new Game(id, lobbyId, List.of(eventId));
        game.recordDrawnEvents(Map.of(eventId, new DrawnFutureEvent(cardId, outcomeIds)));
        game.recordPendingCarryOverEvents(List.of(new PendingCarryOverEvent(carriedEventId, CarryOverState.STALLED)));
        game.recordCascadedParadoxesInRevealOrder(List.of(cascadedEventId), Integer.MAX_VALUE);

        adapter.save(game);

        var captor = ArgumentCaptor.forClass(GameJpaEntity.class);
        then(jpaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getLobbyId()).isEqualTo(lobbyId);
        assertThat(saved.getStatus()).isEqualTo(GameStatus.IN_PROGRESS.name());
        assertThat(saved.getEraCounter()).isZero();
        assertThat(saved.getCascadedEventIds()).containsExactly(cascadedEventId);
        assertThat(saved.getPendingCollapsingEventId()).isNull();
        assertThat(saved.getEventDeck()).containsExactly(eventId);
        assertThat(saved.getPendingCarryOverEvents())
                .singleElement()
                .satisfies(entry -> assertThat(entry.toDomain())
                        .isEqualTo(new PendingCarryOverEvent(carriedEventId, CarryOverState.STALLED)));
        assertThat(saved.getDrawnEvents()).singleElement().satisfies(entry -> {
            assertThat(entry.eventId()).isEqualTo(eventId);
            assertThat(entry.toDrawnFutureEvent()).isEqualTo(new DrawnFutureEvent(cardId, outcomeIds));
        });
    }

    @Test
    void findById_mapsEveryEntityFieldBackOntoTheDomainGame() {
        var id = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var cascadedEventId = UUID.randomUUID();
        var pendingCollapsingEventId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var outcomeIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var entity = new GameJpaEntity();
        entity.setId(id);
        entity.setLobbyId(lobbyId);
        entity.setStatus(GameStatus.IN_PROGRESS.name());
        entity.setEraCounter(2);
        entity.setCascadedEventIds(Set.of(cascadedEventId));
        entity.setPendingCollapsingEventId(pendingCollapsingEventId);
        entity.setEventDeck(List.of(eventId));
        entity.setPendingCarryOverEvents(List.of(PendingCarryOverEventEmbeddable.fromDomain(
                new PendingCarryOverEvent(eventId, CarryOverState.CASCADED))));
        entity.setDrawnEvents(List.of(DrawnEventEmbeddable.of(eventId, new DrawnFutureEvent(cardId, outcomeIds))));
        given(jpaRepository.findById(id)).willReturn(Optional.of(entity));

        var result = adapter.findById(id).orElseThrow();

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.lobbyId()).isEqualTo(lobbyId);
        assertThat(result.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(result.eraCounter()).isEqualTo(2);
        assertThat(result.cascadedEventIds()).containsExactly(cascadedEventId);
        assertThat(result.pendingCollapsingEventId()).isEqualTo(pendingCollapsingEventId);
        assertThat(result.eventDeck()).containsExactly(eventId);
        assertThat(result.pendingCarryOverEvents())
                .containsExactly(new PendingCarryOverEvent(eventId, CarryOverState.CASCADED));
        assertThat(result.drawnEvents()).containsEntry(eventId, new DrawnFutureEvent(cardId, outcomeIds));
    }

    @Test
    void findByIdWithLock_delegatesToTheLockingQuery() {
        var id = UUID.randomUUID();
        var entity = new GameJpaEntity();
        entity.setId(id);
        entity.setLobbyId(UUID.randomUUID());
        entity.setStatus(GameStatus.IN_PROGRESS.name());
        given(jpaRepository.findByIdWithLock(id)).willReturn(Optional.of(entity));

        var result = adapter.findByIdWithLock(id);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void findById_gameNotFound_returnsEmpty() {
        var id = UUID.randomUUID();
        given(jpaRepository.findById(id)).willReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }
}
