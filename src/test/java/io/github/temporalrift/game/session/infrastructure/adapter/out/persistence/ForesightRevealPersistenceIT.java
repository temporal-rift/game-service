package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;

@PersistenceIntegrationTest
class ForesightRevealPersistenceIT {

    @Autowired
    ForesightRevealRepository reveals;

    @Test
    void saveIfAbsent_roundTripsTheOrderedPreviewAndKeepsTheFirstWrite() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var catalogEventIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(reveals.saveIfAbsent(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null)))
                .isTrue();

        var loaded = reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer);
        assertThat(loaded).contains(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));

        assertThat(reveals.saveIfAbsent(
                        new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), "final-era")))
                .isFalse();
        assertThat(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .contains(new ForesightReveal(gameId, 2, viewer, 3, catalogEventIds, null));
    }

    @Test
    void findByGameIdAndEraNumberAndPlayerId_unknownViewer_returnsEmpty() {
        assertThat(reveals.findByGameIdAndEraNumberAndPlayerId(UUID.randomUUID(), 2, UUID.randomUUID()))
                .isEmpty();
    }
}
