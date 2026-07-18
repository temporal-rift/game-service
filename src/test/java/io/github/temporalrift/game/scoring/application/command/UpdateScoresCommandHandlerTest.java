package io.github.temporalrift.game.scoring.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.scoring.domain.event.ScoresUpdated;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.scoring.domain.context.ActionScoringFact;
import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEventPublisher;

@DisplayName("UpdateScoresCommandHandler")
class UpdateScoresCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 1;

    final List<EventEnvelope> publishedEnvelopes = new ArrayList<>();
    final List<Object> internalEvents = new ArrayList<>();

    ScoringEventPublisher scoringPublisher = publishedEnvelopes::add;
    ApplicationEventPublisher appPublisher = internalEvents::add;

    @Test
    @DisplayName("creates new PlayerScore aggregates for players with no existing score rows")
    void createsNewAggregatesForMissingPlayers() {
        var playerId = UUID.randomUUID();
        var context = contextWithEraserPlayer(playerId);

        var savedScores = new ArrayList<PlayerScore>();
        PlayerScoreRepository repo = new FakePlayerScoreRepository(List.of(), savedScores);
        EraScoringContextRepository ctxRepo = new FakeEraScoringContextRepository(context);

        var handler =
                new UpdateScoresCommandHandler(repo, ctxRepo, new EraScoreEvaluator(), scoringPublisher, appPublisher);
        handler.handle(new UpdateEraScoresCommand(GAME_ID, ERA, List.of()));

        assertThat(savedScores).hasSize(1);
        assertThat(savedScores.get(0).playerId()).isEqualTo(playerId);
        assertThat(savedScores.get(0).faction()).isEqualTo(Faction.ERASERS);
    }

    @Test
    @DisplayName("publishes exactly one ScoresUpdated envelope and one typed event per command")
    void publishesExactlyOneScoresUpdated() {
        var playerId = UUID.randomUUID();
        var context = contextWithEraserPlayer(playerId);

        var handler = handler(context, List.<PlayerScore>of());
        handler.handle(new UpdateEraScoresCommand(GAME_ID, ERA, List.of()));

        assertThat(publishedEnvelopes).hasSize(1);
        assertThat(internalEvents).hasSize(1);
        assertThat(internalEvents.get(0)).isInstanceOf(ScoresUpdated.class);
    }

    @Test
    @DisplayName("includes all context players in ScoresUpdated including players with no score decision")
    void includesAllPlayersInEvent() {
        var weaverId = UUID.randomUUID();
        var eraserId = UUID.randomUUID();
        var chainId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS), new PlayerFaction(eraserId, Faction.ERASERS)),
                List.of(new EventOutcomeFact(UUID.randomUUID(), UUID.randomUUID(), null, 3, 3)),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_COMPLETED, ERA)));

        var handler = handler(context, List.<PlayerScore>of());
        handler.handle(new UpdateEraScoresCommand(GAME_ID, ERA, List.of()));

        var event = (ScoresUpdated) internalEvents.get(0);
        assertThat(event.updates()).hasSize(2);

        var weaverUpdate = event.updates().stream()
                .filter(u -> u.playerId().equals(weaverId))
                .findFirst()
                .orElseThrow();
        assertThat(weaverUpdate.pointsDelta()).isEqualTo(10);
        assertThat(weaverUpdate.newTotal()).isEqualTo(10);

        var eraserUpdate = event.updates().stream()
                .filter(u -> u.playerId().equals(eraserId))
                .findFirst()
                .orElseThrow();
        assertThat(eraserUpdate.pointsDelta()).isZero();
        assertThat(eraserUpdate.reason()).isEqualTo("NO_SCORE_CHANGE");
        assertThat(eraserUpdate.newTotal()).isZero();
    }

    @Test
    @DisplayName("ScoresUpdated newTotal preserves negative totals without flooring")
    void negativeNewTotalIsPreserved() {
        var weaverId = UUID.randomUUID();
        var chainId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_BROKEN, ERA)));

        var handler = handler(context, List.<PlayerScore>of());
        handler.handle(new UpdateEraScoresCommand(GAME_ID, ERA, List.of()));

        var event = (ScoresUpdated) internalEvents.get(0);
        var update = event.updates().get(0);
        assertThat(update.newTotal()).isEqualTo(-3);
        assertThat(update.pointsDelta()).isEqualTo(-3);
    }

    @Test
    @DisplayName("applies scoring to existing PlayerScore aggregates loaded from repository")
    void appliesScoringToExistingAggregates() {
        var activistId = UUID.randomUUID();
        var existingScore =
                PlayerScore.reconstitute(UUID.randomUUID(), GAME_ID, activistId, Faction.ACTIVISTS, 4, List.of());

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(activistId, Faction.ACTIVISTS)),
                List.of(),
                List.of(new ActionScoringFact(activistId, Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON)),
                List.of());

        var savedScores = new ArrayList<PlayerScore>();
        PlayerScoreRepository repo = new FakePlayerScoreRepository(List.of(existingScore), savedScores);
        EraScoringContextRepository ctxRepo = new FakeEraScoringContextRepository(context);

        var handler =
                new UpdateScoresCommandHandler(repo, ctxRepo, new EraScoreEvaluator(), scoringPublisher, appPublisher);
        handler.handle(new UpdateEraScoresCommand(GAME_ID, ERA, List.of()));

        var event = (ScoresUpdated) internalEvents.get(0);
        var update = event.updates().get(0);
        assertThat(update.newTotal()).isEqualTo(8); // 4 + 4 (DECLARED_OUTCOME_WON)
        assertThat(update.pointsDelta()).isEqualTo(4);
    }

    @Test
    @DisplayName("stamps player_score_history with the chain fact's own era, not the scoring pass's era")
    void chainFactHistoryEntryUsesFactsOwnEraNotCommandEra() {
        var weaverId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var scoringPassEra = 3;
        var factOwnEra = 1;

        var context = new EraScoringContext(
                GAME_ID,
                scoringPassEra,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_COMPLETED, factOwnEra)));

        var savedScores = new ArrayList<PlayerScore>();
        PlayerScoreRepository repo = new FakePlayerScoreRepository(List.of(), savedScores);
        EraScoringContextRepository ctxRepo = new FakeEraScoringContextRepository(context);

        var handler =
                new UpdateScoresCommandHandler(repo, ctxRepo, new EraScoreEvaluator(), scoringPublisher, appPublisher);
        handler.handle(new UpdateEraScoresCommand(GAME_ID, scoringPassEra, List.of()));

        assertThat(savedScores).hasSize(1);
        assertThat(savedScores.get(0).history()).hasSize(1);
        assertThat(savedScores.get(0).history().get(0).eraNumber()).isEqualTo(factOwnEra);
    }

    private EraScoringContext contextWithEraserPlayer(UUID playerId) {
        return new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(playerId, Faction.ERASERS)),
                List.of(new EventOutcomeFact(UUID.randomUUID(), UUID.randomUUID(), null, 3, 3)),
                List.of(),
                List.of());
    }

    private UpdateScoresCommandHandler handler(EraScoringContext context, List<PlayerScore> existingScores) {
        PlayerScoreRepository repo = new FakePlayerScoreRepository(existingScores, new ArrayList<>());
        EraScoringContextRepository ctxRepo = new FakeEraScoringContextRepository(context);
        return new UpdateScoresCommandHandler(repo, ctxRepo, new EraScoreEvaluator(), scoringPublisher, appPublisher);
    }

    static class FakePlayerScoreRepository implements PlayerScoreRepository {

        private final List<PlayerScore> existing;
        private final List<PlayerScore> saved;

        FakePlayerScoreRepository(List<PlayerScore> existing, List<PlayerScore> saved) {
            this.existing = existing;
            this.saved = saved;
        }

        @Override
        public List<PlayerScore> findAllByGameId(UUID gameId) {
            return existing;
        }

        @Override
        public List<PlayerScore> findAllByGameIdWithLock(UUID gameId) {
            return existing;
        }

        @Override
        public List<PlayerScore> saveAll(List<PlayerScore> scores) {
            saved.addAll(scores);
            return scores;
        }
    }

    static class FakeEraScoringContextRepository implements EraScoringContextRepository {

        private final EraScoringContext context;

        FakeEraScoringContextRepository(EraScoringContext context) {
            this.context = context;
        }

        @Override
        public EraScoringContext getRequired(UUID gameId, int eraNumber) {
            return context;
        }

        @Override
        public int expectedOutcomeCount(UUID gameId, int eraNumber) {
            throw new UnsupportedOperationException("not used by UpdateScoresCommandHandler");
        }

        @Override
        public void upsertPlayerFaction(UUID gameId, UUID playerId, Faction faction) {
            throw new UnsupportedOperationException("not used by UpdateScoresCommandHandler");
        }

        @Override
        public void upsertExpectedOutcomeCount(UUID gameId, int eraNumber, int expectedOutcomeCount) {
            throw new UnsupportedOperationException("not used by UpdateScoresCommandHandler");
        }

        @Override
        public void recordChainFact(UUID gameId, UUID playerId, UUID chainId, ScoreReason reason, int eraNumber) {
            throw new UnsupportedOperationException("not used by UpdateScoresCommandHandler");
        }
    }
}
