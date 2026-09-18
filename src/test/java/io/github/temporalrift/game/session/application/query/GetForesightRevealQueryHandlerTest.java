package io.github.temporalrift.game.session.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.application.port.in.GetForesightRevealUseCase;
import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;

@ExtendWith(MockitoExtension.class)
class GetForesightRevealQueryHandlerTest {

    @Mock
    ForesightRevealRepository reveals;

    @Mock
    FutureEventCatalogPort catalog;

    @Mock
    GameRepository gameRepository;

    @InjectMocks
    GetForesightRevealQueryHandler handler;

    @Test
    void handle_returnsTheViewerOwnPreviewWithDisplayData() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        var catalogEventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(Optional.of(new ForesightReveal(gameId, 2, viewer, 3, List.of(catalogEventId), null)));
        given(gameRepository.findById(gameId))
                .willReturn(Optional.of(
                        Game.reconstitute(gameId, UUID.randomUUID(), List.of(), 2, 0, GameStatus.IN_PROGRESS)));
        given(catalog.findByEventIds(List.of(catalogEventId)))
                .willReturn(List.of(new FutureEventDefinition(
                        catalogEventId,
                        "Storm",
                        List.of(
                                new FutureEventDefinition.OutcomeDefinition(outcomeId, "Flood", 40),
                                new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Drought", 30),
                                new FutureEventDefinition.OutcomeDefinition(UUID.randomUUID(), "Calm", 30)))));

        var result = handler.handle(new GetForesightRevealUseCase.Query(gameId, 2, viewer));

        assertThat(result).isPresent();
        assertThat(result.get().playerId()).isEqualTo(viewer);
        assertThat(result.get().nextEraNumber()).isEqualTo(3);
        assertThat(result.get().revealedEvents()).hasSize(1);
        assertThat(result.get().revealedEvents().get(0).title()).isEqualTo("Storm");
        assertThat(result.get().revealedEvents().get(0).outcomes())
                .extracting(GetForesightRevealUseCase.OutcomePreview::description)
                .containsExactly("Flood", "Drought", "Calm");
    }

    @Test
    void handle_hidesThePreviewFromANonEntitledParticipant() {
        var gameId = UUID.randomUUID();
        var other = UUID.randomUUID();
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, other)).willReturn(Optional.empty());

        assertThat(handler.handle(new GetForesightRevealUseCase.Query(gameId, 2, other)))
                .isEmpty();
    }

    @Test
    void handle_hidesThePreviewOnceTheNextEraHasStarted() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(Optional.of(new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), null)));
        given(gameRepository.findById(gameId))
                .willReturn(Optional.of(
                        Game.reconstitute(gameId, UUID.randomUUID(), List.of(), 3, 0, GameStatus.IN_PROGRESS)));

        assertThat(handler.handle(new GetForesightRevealUseCase.Query(gameId, 2, viewer)))
                .isEmpty();
    }

    @Test
    void handle_hidesThePreviewWhenTheGameIsGone() {
        var gameId = UUID.randomUUID();
        var viewer = UUID.randomUUID();
        given(reveals.findByGameIdAndEraNumberAndPlayerId(gameId, 2, viewer))
                .willReturn(Optional.of(new ForesightReveal(gameId, 2, viewer, 3, List.of(UUID.randomUUID()), null)));
        given(gameRepository.findById(gameId)).willReturn(Optional.empty());

        assertThat(handler.handle(new GetForesightRevealUseCase.Query(gameId, 2, viewer)))
                .isEmpty();
    }
}
