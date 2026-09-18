package io.github.temporalrift.game.session.application.query;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.session.application.port.in.GetForesightRevealUseCase;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.ForesightRevealRepository;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;

@Service
class GetForesightRevealQueryHandler implements GetForesightRevealUseCase {

    private final ForesightRevealRepository reveals;
    private final FutureEventCatalogPort catalog;
    private final GameRepository gameRepository;

    GetForesightRevealQueryHandler(
            ForesightRevealRepository reveals, FutureEventCatalogPort catalog, GameRepository gameRepository) {
        this.reveals = reveals;
        this.catalog = catalog;
        this.gameRepository = gameRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Result> handle(Query query) {
        var stored = reveals.findByGameIdAndEraNumberAndPlayerId(
                        query.gameId(), query.eraNumber(), query.callerPlayerId())
                .orElse(null);
        if (stored == null) {
            return Optional.empty();
        }
        // The preview expires with its era: once the next era starts it is public draw history,
        // not private foresight.
        var currentEra =
                gameRepository.findById(query.gameId()).map(Game::eraCounter).orElse(null);
        if (currentEra == null || currentEra != stored.eraNumber()) {
            return Optional.empty();
        }
        var events = stored.catalogEventIds().isEmpty()
                ? List.<EventPreview>of()
                : catalog.findByEventIds(stored.catalogEventIds()).stream()
                        .map(definition -> new EventPreview(
                                definition.eventId(),
                                definition.title(),
                                definition.outcomes().stream()
                                        .map(outcome -> new OutcomePreview(outcome.outcomeId(), outcome.description()))
                                        .toList()))
                        .toList();
        return Optional.of(new Result(
                stored.gameId(),
                stored.eraNumber(),
                stored.playerId(),
                stored.nextEraNumber(),
                events,
                stored.emptyReason()));
    }
}
