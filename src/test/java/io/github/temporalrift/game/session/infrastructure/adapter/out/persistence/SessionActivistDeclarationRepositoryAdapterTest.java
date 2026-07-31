package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.SpecialAction;

@ExtendWith(MockitoExtension.class)
class SessionActivistDeclarationRepositoryAdapterTest {

    @Mock
    SessionActivistDeclarationJpaRepository repository;

    @InjectMocks
    SessionActivistDeclarationRepositoryAdapter adapter;

    @Test
    void saveIfAbsent_persistsTheFirstDeclarationForThePlayerAndEra() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(repository.existsByGameIdAndEraNumberAndPlayerId(gameId, 2, playerId))
                .willReturn(false);

        adapter.saveIfAbsent(gameId, 2, playerId, targetEventId, SpecialAction.RALLY);

        var entity = ArgumentCaptor.forClass(SessionActivistDeclarationJpaEntity.class);
        then(repository).should().save(entity.capture());
        assertThat(entity.getValue().playerId()).isEqualTo(playerId);
        assertThat(entity.getValue().targetEventId()).isEqualTo(targetEventId);
    }

    @Test
    void saveIfAbsent_ignoresTheAtLeastOnceRedeliveryForTheSamePlayerAndEra() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(repository.existsByGameIdAndEraNumberAndPlayerId(gameId, 2, playerId))
                .willReturn(true);

        adapter.saveIfAbsent(gameId, 2, playerId, UUID.randomUUID(), SpecialAction.MOMENTUM);

        then(repository).should(never()).save(any());
    }

    @Test
    void findPlayerIdsTargeting_mapsThePersistedDeclarations() {
        var gameId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var firstPlayerId = UUID.randomUUID();
        var secondPlayerId = UUID.randomUUID();
        given(repository.findAllByGameIdAndEraNumberAndTargetEventId(gameId, 2, targetEventId))
                .willReturn(List.of(
                        SessionActivistDeclarationJpaEntity.from(
                                gameId, 2, firstPlayerId, targetEventId, SpecialAction.RALLY),
                        SessionActivistDeclarationJpaEntity.from(
                                gameId, 2, secondPlayerId, targetEventId, SpecialAction.MOMENTUM)));

        assertThat(adapter.findPlayerIdsTargeting(gameId, 2, targetEventId))
                .containsExactly(firstPlayerId, secondPlayerId);
        then(repository).should().findAllByGameIdAndEraNumberAndTargetEventId(eq(gameId), eq(2), eq(targetEventId));
    }
}
