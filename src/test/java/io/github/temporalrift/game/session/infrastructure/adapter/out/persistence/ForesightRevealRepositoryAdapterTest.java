package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;

@ExtendWith(MockitoExtension.class)
class ForesightRevealRepositoryAdapterTest {

    @Mock
    ForesightRevealJpaRepository repository;

    @InjectMocks
    ForesightRevealRepositoryAdapter adapter;

    @Test
    void saveIfAbsent_storesTheFirstRevealForTheViewerAndEra() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var catalogEventIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(repository.existsByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(false);

        var stored = adapter.saveIfAbsent(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));

        assertThat(stored).isTrue();
        var entity = ArgumentCaptor.forClass(ForesightRevealJpaEntity.class);
        then(repository).should().save(entity.capture());
        assertThat(entity.getValue().playerId()).isEqualTo(viewer);
        assertThat(entity.getValue().catalogEventIds()).containsExactlyElementsOf(catalogEventIds);
    }

    @Test
    void saveIfAbsent_ignoresTheRedeliveryForTheSameViewerAndEra() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        given(repository.existsByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(true);

        var stored = adapter.saveIfAbsent(new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), null));

        assertThat(stored).isFalse();
        then(repository).should(never()).save(any());
    }

    @Test
    void findByGameIdAndEraNumberAndPlayerId_mapsTheStoredReveal() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var catalogEventIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(repository.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(Optional.of(ForesightRevealJpaEntity.from(gameId, 2, viewer, 3, catalogEventIds, null)));

        var found = adapter.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer);

        assertThat(found).contains(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));
    }

    @Test
    void findByGameIdAndEraNumberAndPlayerId_unknownViewer_returnsEmpty() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        given(repository.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer)).willReturn(Optional.empty());

        assertThat(adapter.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .isEmpty();
    }
}
