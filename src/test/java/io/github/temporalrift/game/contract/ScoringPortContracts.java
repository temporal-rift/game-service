package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.ParadoxCascadeScoringFact;
import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.event.AnnihilationResolved;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.scoring.domain.port.out.RevisionistExposureRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringEraCompletionRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/** The scoring module's persisted driven ports, one contract each. */
public final class ScoringPortContracts {

    private ScoringPortContracts() {}

    public interface EraScoringContextPortContract extends PortContract {

        @Test
        default void eraScoringContext_assemblesPlayersFromUpsertedFactionAssignments() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, player, Faction.ERASERS);
            repository.upsertPlayerFaction(gameId, player, Faction.PROPHETS);

            var context = repository.getRequired(gameId, 1);

            assertThat(context.players()).singleElement().satisfies(entry -> {
                assertThat(entry.playerId()).isEqualTo(player);
                assertThat(entry.faction()).isEqualTo(Faction.PROPHETS);
            });
        }

        @Test
        default void eraScoringContext_requiredContextWithoutPlayersIsNotFound() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.getRequired(gameId, 1))
                    .isInstanceOf(EraScoringContextNotFoundException.class);
        }

        @Test
        default void eraScoringContext_expectedOutcomeCountIsReplacedByTheLatestUpsert() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();

            repository.upsertExpectedOutcomeCount(gameId, 1, 3);
            repository.upsertExpectedOutcomeCount(gameId, 1, 5);

            assertThat(repository.expectedOutcomeCount(gameId, 1)).isEqualTo(5);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.expectedOutcomeCount(gameId, 2))
                    .isInstanceOf(EraScoringContextNotFoundException.class);
        }

        @Test
        default void eraScoringContext_chainFactsAreConsumedExactlyOnceAndKeepTheirOwnEra() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var chainId = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, player, Faction.WEAVERS);
            repository.recordChainFact(gameId, player, chainId, ScoreReason.CHAIN_COMPLETED, 5);

            assertThat(repository.getRequired(gameId, 1).chainFacts())
                    .containsExactly(new ChainScoringFact(player, chainId, ScoreReason.CHAIN_COMPLETED, 5));
            assertThat(repository.getRequired(gameId, 2).chainFacts()).isEmpty();
        }

        @Test
        default void eraScoringContext_paradoxCascadeFactsAreConsumedExactlyOnce() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, UUID.randomUUID(), Faction.PROPHETS);
            var paradoxId = UUID.randomUUID();
            var affectedEvent = UUID.randomUUID();
            var detonators = List.of(UUID.randomUUID());
            repository.recordParadoxCascadeFact(gameId, 5, paradoxId, affectedEvent, detonators);

            assertThat(repository.getRequired(gameId, 1).paradoxCascadeFacts())
                    .containsExactly(new ParadoxCascadeScoringFact(paradoxId, affectedEvent, detonators, 5));
            var next = repository.getRequired(gameId, 2);
            assertThat(next.paradoxCascadeFacts()).isEmpty();
            assertThat(next.priorCascadePenaltyEventIds()).contains(affectedEvent);
        }

        @Test
        default void eraScoringContext_actionFactsAreIdempotentPerPlayerAndReasonAndConsumedOnce() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, player, Faction.ACTIVISTS);
            repository.recordActionFact(gameId, 1, player, Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON);
            repository.recordActionFact(gameId, 1, player, Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON);

            assertThat(repository.getRequired(gameId, 1).actionFacts()).hasSize(1);
            assertThat(repository.getRequired(gameId, 1).actionFacts()).isEmpty();
        }

        @Test
        default void eraScoringContext_annihilationsAreRecordedOncePerActingPlayer() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, UUID.randomUUID(), Faction.ERASERS);
            var first = new AnnihilationResolved(
                    gameId, 1, 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, false);
            var secondEraser = new AnnihilationResolved(
                    gameId, 1, 1, UUID.randomUUID(), first.targetEventId(), first.targetOutcomeId(), true, true);

            repository.recordAnnihilation(first);
            repository.recordAnnihilation(first);
            repository.recordAnnihilation(secondEraser);

            assertThat(repository.getRequired(gameId, 1).annihilationFacts()).hasSize(2);
        }

        @Test
        default void eraScoringContext_fulfillmentDeclarationsAreIdempotent() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var target = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, player, Faction.REVISIONISTS);

            repository.recordFulfillmentDeclaration(gameId, 1, player, target);
            repository.recordFulfillmentDeclaration(gameId, 1, player, target);

            assertThat(repository.getRequired(gameId, 1).fulfillmentDeclarations())
                    .hasSize(1);
        }

        @Test
        default void eraScoringContext_corruptCorrelationIsIdempotentAndCarriesTheConfirmation() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var corrupting = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, corrupting, Faction.ERASERS);
            var correlation = new EraActionFactsFinalized.CorruptCorrelationFact(
                    corrupting, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID());

            repository.recordCorruptCorrelation(gameId, 2, correlation);
            repository.recordCorruptCorrelation(gameId, 2, correlation);

            assertThat(repository.getRequired(gameId, 2).corruptCorrelations())
                    .singleElement()
                    .satisfies(fact -> {
                        assertThat(fact.sourceOutcomeId()).isNull();
                        assertThat(fact.tookEffect()).isNull();
                    });

            repository.confirmCorruptInversion(gameId, 2, corrupting, correlation.cardInstanceId(), true);

            assertThat(repository.getRequired(gameId, 2).corruptCorrelations())
                    .singleElement()
                    .satisfies(fact -> assertThat(fact.tookEffect()).isTrue());
        }

        @Test
        default void eraScoringContext_aConfirmationArrivingFirstIsMergedWhenTheCorrelationIsRecorded() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var corrupting = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, corrupting, Faction.ERASERS);
            var correlation = new EraActionFactsFinalized.CorruptCorrelationFact(
                    corrupting, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID());

            repository.confirmCorruptInversionForTarget(gameId, 2, corrupting, correlation.targetEventId(), true);
            repository.recordCorruptCorrelation(gameId, 2, correlation);

            assertThat(repository.getRequired(gameId, 2).corruptCorrelations())
                    .singleElement()
                    .satisfies(fact -> assertThat(fact.tookEffect()).isTrue());
        }

        @Test
        default void eraScoringContext_theFirstWrittenOutcomeOfAnEventWins() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var event = UUID.randomUUID();
            var firstOutcome = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, UUID.randomUUID(), Faction.PROPHETS);

            repository.upsertWrittenOutcome(gameId, 1, event, firstOutcome, UUID.randomUUID());
            repository.upsertWrittenOutcome(gameId, 1, event, UUID.randomUUID(), UUID.randomUUID());

            assertThat(repository.getRequired(gameId, 1).eventOutcomes())
                    .singleElement()
                    .satisfies(fact -> {
                        assertThat(fact.eventId()).isEqualTo(event);
                        assertThat(fact.writtenOutcomeId()).isEqualTo(firstOutcome);
                    });
        }

        @Test
        default void eraScoringContext_eraResolutionBarrierDrivesThePendingCompletionSweep() {
            var repository = port(EraScoringContextRepository.class);
            var completions = port(ScoringEraCompletionRepository.class);
            var scored = UUID.randomUUID();
            var stuck = UUID.randomUUID();
            repository.saveEraResolutionCompleted(new EraResolutionCompleted(scored, 1, List.of()));
            completions.tryMarkScoringComplete(scored, 1);
            repository.saveEraResolutionCompleted(new EraResolutionCompleted(stuck, 1, List.of()));
            repository.saveEraResolutionCompleted(new EraResolutionCompleted(stuck, 1, List.of()));

            assertThat(repository.eraResolutionCompleted(stuck, 1)).isTrue();
            assertThat(repository.eraResolutionCompleted(UUID.randomUUID(), 1)).isFalse();
            assertThat(repository.findResolvedErasNotYetScored())
                    .contains(new PendingEraScoringCompletion(stuck, 1))
                    .noneMatch(entry -> entry.gameId().equals(scored));
        }

        @Test
        default void eraScoringContext_actionFactsReadyMarkerIsIdempotent() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();

            assertThat(repository.actionFactsReady(gameId, 1)).isFalse();
            repository.markActionFactsReady(gameId, 1);
            repository.markActionFactsReady(gameId, 1);

            assertThat(repository.actionFactsReady(gameId, 1)).isTrue();
            assertThat(repository.actionFactsReady(gameId, 2)).isFalse();
        }

        @Test
        default void eraScoringContext_activistDeclarationsResolveAgainstTheTimelineOutcome() {
            var repository = port(EraScoringContextRepository.class);
            var inbox = port(TimelineOutcomeInboxRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var event = UUID.randomUUID();
            var outcome = UUID.randomUUID();
            repository.upsertPlayerFaction(gameId, player, Faction.ACTIVISTS);
            repository.upsertActivistDeclaration(
                    new ActivistDeclarationRecorded(gameId, 1, 1, player, SpecialAction.RALLY, event, outcome));
            repository.saveEraResolutionCompleted(new EraResolutionCompleted(
                    gameId,
                    1,
                    List.of(new EraResolutionCompleted.TerminalResolution(
                            event, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, outcome))));
            inbox.save(new OutcomeApplied(gameId, 1, event, outcome, List.of()));

            assertThat(repository.activistDeclarationsResolved(gameId, 1)).isFalse();
            var resolved = repository.resolveActivistDeclarations(gameId, 1);

            assertThat(resolved)
                    .singleElement()
                    .satisfies(
                            declaration -> assertThat(declaration.succeeded()).isTrue());
            assertThat(repository.activistDeclarationsResolved(gameId, 1)).isTrue();
            assertThat(repository.resolveActivistDeclarations(gameId, 1)).isEmpty();
            assertThat(repository.getRequired(gameId, 1).actionFacts())
                    .singleElement()
                    .satisfies(
                            fact -> assertThat(fact.reason()).isEqualTo(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY));
        }

        @Test
        default void eraScoringContext_revisionistActionsResolveAgainstTheTimelineBarrier() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var event = UUID.randomUUID();
            repository.recordRevisionistAction(gameId, 1, player, SpecialAction.REWRITE, event, UUID.randomUUID());
            repository.recordRevisionistAction(gameId, 1, player, SpecialAction.REWRITE, event, UUID.randomUUID());
            repository.saveEraResolutionCompleted(new EraResolutionCompleted(
                    gameId,
                    1,
                    List.of(new EraResolutionCompleted.TerminalResolution(
                            event, 0, EraResolutionCompleted.TerminalState.STALLED, null))));

            assertThat(repository.revisionistActionsResolved(gameId, 1)).isFalse();
            repository.resolveRevisionistActions(gameId, 1);

            assertThat(repository.revisionistActionsResolved(gameId, 1)).isTrue();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void eraScoringContext_rollbackDiscardsRecordedFacts() {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            inNewTransaction(() -> repository.upsertPlayerFaction(gameId, player, Faction.WEAVERS));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> inNewTransaction(() -> {
                        repository.recordChainFact(gameId, player, UUID.randomUUID(), ScoreReason.CHAIN_LINK_ADDED, 1);
                        throw new IllegalStateException("boom");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(inNewTransaction(() -> repository.getRequired(gameId, 1)).chainFacts())
                    .isEmpty();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void eraScoringContext_concurrentUpsertsOfOnePlayerEndWithOneRow() throws Exception {
            var repository = port(EraScoringContextRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var first = java.util.concurrent.CompletableFuture.runAsync(
                    () -> inNewTransaction(() -> repository.upsertPlayerFaction(gameId, player, Faction.ERASERS)));
            var second = java.util.concurrent.CompletableFuture.runAsync(
                    () -> inNewTransaction(() -> repository.upsertPlayerFaction(gameId, player, Faction.PROPHETS)));
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(inNewTransaction(() -> repository.getRequired(gameId, 1)).players())
                    .hasSize(1);
        }
    }

    public interface PlayerScorePortContract extends PortContract {

        @Test
        default void playerScore_savesNewScoresAndReloadsThemWithHistory() {
            var repository = port(PlayerScoreRepository.class);
            var score = new PlayerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.WEAVERS);
            score.apply(1, ScoreReason.CHAIN_LINK_ADDED, 2);

            repository.saveAll(List.of(score));

            assertThat(repository.findAllByGameId(score.gameId()))
                    .singleElement()
                    .satisfies(persisted -> {
                        assertThat(persisted.id()).isEqualTo(score.id());
                        assertThat(persisted.totalScore()).isEqualTo(2);
                        assertThat(persisted.history()).hasSize(1);
                    });
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void playerScore_secondSaveUpdatesInPlaceAndAppendsOnlyNewHistory() {
            var repository = port(PlayerScoreRepository.class);
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var score = new PlayerScore(UUID.randomUUID(), gameId, playerId, Faction.ERASERS);
            score.apply(1, ScoreReason.ANNIHILATED_OUTCOME, 3);
            inNewTransaction(() -> repository.saveAll(List.of(score)));
            var firstLoad =
                    inNewTransaction(() -> repository.findAllByGameId(gameId)).getFirst();

            var reloaded = PlayerScore.reconstitute(
                    firstLoad.id(), gameId, playerId, Faction.ERASERS, firstLoad.totalScore(), firstLoad.history());
            reloaded.apply(2, ScoreReason.CORRUPTED_OPPONENT_CARD, 2);
            inNewTransaction(() -> repository.saveAll(List.of(reloaded)));

            assertThat(inNewTransaction(() -> repository.findAllByGameId(gameId)))
                    .singleElement()
                    .satisfies(persisted -> {
                        assertThat(persisted.id()).isEqualTo(firstLoad.id());
                        assertThat(persisted.totalScore()).isEqualTo(5);
                        assertThat(persisted.history()).hasSize(2);
                    });
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void playerScore_lockMakesASecondTransactionWait() {
            var repository = port(PlayerScoreRepository.class);
            var score = new PlayerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.WEAVERS);
            inNewTransaction(() -> repository.saveAll(List.of(score)));

            assertSecondLockerWaitsForFirst(() -> repository.findAllByGameIdWithLock(score.gameId()));
        }
    }

    public interface RevisionistExposurePortContract extends PortContract {

        @Test
        default void revisionistExposure_isIdempotentAndScopedToItsGame() {
            var repository = port(RevisionistExposureRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();

            repository.recordExposure(gameId, player);
            repository.recordExposure(gameId, player);

            assertThat(repository.exposedPlayerIds(gameId)).isEqualTo(Set.of(player));
            assertThat(repository.exposedPlayerIds(UUID.randomUUID())).isEmpty();
        }
    }

    public interface ScoringEraCompletionPortContract extends PortContract {

        @Test
        default void eraCompletion_firstClaimWinsAndErasClaimIndependently() {
            var repository = port(ScoringEraCompletionRepository.class);
            var gameId = UUID.randomUUID();

            assertThat(repository.tryMarkScoringComplete(gameId, 1)).isTrue();
            assertThat(repository.tryMarkScoringComplete(gameId, 1)).isFalse();
            assertThat(repository.tryMarkScoringComplete(gameId, 2)).isTrue();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        default void eraCompletion_rollbackReleasesTheClaim() {
            var repository = port(ScoringEraCompletionRepository.class);
            var gameId = UUID.randomUUID();

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> inNewTransaction(() -> {
                        repository.tryMarkScoringComplete(gameId, 1);
                        throw new IllegalStateException("boom");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(inNewTransaction(() -> repository.tryMarkScoringComplete(gameId, 1)))
                    .isTrue();
        }
    }

    public interface ScoringGameVisibilityPortContract extends PortContract {

        @Test
        default void gameVisibility_factionsStayHiddenUntilRevealedAndRevealIsIdempotent() {
            var repository = port(ScoringGameVisibilityRepository.class);
            var gameId = UUID.randomUUID();

            assertThat(repository.areFactionsRevealed(gameId)).isFalse();
            repository.markFactionsRevealed(gameId);
            repository.markFactionsRevealed(gameId);

            assertThat(repository.areFactionsRevealed(gameId)).isTrue();
            assertThat(repository.areFactionsRevealed(UUID.randomUUID())).isFalse();
        }
    }

    public interface ScoringPlayerPortContract extends PortContract {

        @Test
        default void scoringPlayer_upsertKeepsOneParticipantAndRenames() {
            var repository = port(ScoringPlayerRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();

            assertThat(repository.isParticipant(gameId, player)).isFalse();
            repository.upsertPlayerName(gameId, player, "A".repeat(32));
            repository.upsertPlayerName(gameId, player, "Renamed");

            assertThat(repository.isParticipant(gameId, player)).isTrue();
            assertThat(repository.isParticipant(gameId, UUID.randomUUID())).isFalse();
        }
    }

    public interface ScoringReadPortContract extends PortContract {

        @Test
        default void scoringRead_joinsScoresPlayerNamesAndTheLatestCompletedEra() {
            var scores = port(PlayerScoreRepository.class);
            var players = port(ScoringPlayerRepository.class);
            var completions = port(ScoringEraCompletionRepository.class);
            var read = port(ScoringReadRepository.class);
            var gameId = UUID.randomUUID();
            var player = UUID.randomUUID();
            var score = new PlayerScore(UUID.randomUUID(), gameId, player, Faction.PROPHETS);
            score.apply(1, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
            score.apply(2, ScoreReason.EVENT_RESOLVED_AS_WRITTEN, 4);
            scores.saveAll(List.of(score));
            players.upsertPlayerName(gameId, player, "Prophet");
            completions.tryMarkScoringComplete(gameId, 1);
            completions.tryMarkScoringComplete(gameId, 2);

            assertThat(read.findCurrentScores(gameId)).singleElement().satisfies(row -> {
                assertThat(row.playerName()).isEqualTo("Prophet");
                assertThat(row.score()).isEqualTo(8);
                assertThat(row.eraNumber()).isEqualTo(2);
                assertThat(row.faction()).isEqualTo(Faction.PROPHETS);
            });
            assertThat(read.findScoreHistory(gameId))
                    .extracting(ScoringReadRepository.ScoreHistoryRow::eraNumber)
                    .containsExactly(1, 2);
            assertThat(read.findCurrentScores(UUID.randomUUID())).isEmpty();
        }
    }

    public interface TimelineOutcomeInboxPortContract extends PortContract {

        @Test
        default void outcomeInbox_duplicateSavesStoreOnceAndDoNotPoisonLaterWrites() {
            var repository = port(TimelineOutcomeInboxRepository.class);
            var outcome = new OutcomeApplied(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), List.of());
            var other = new OutcomeApplied(
                    outcome.gameId(), outcome.eraNumber(), UUID.randomUUID(), UUID.randomUUID(), List.of());

            transactionTemplate().executeWithoutResult(status -> {
                repository.save(outcome);
                repository.save(outcome);
                repository.save(other);
            });

            assertThat(repository.findByGameIdAndEraNumber(outcome.gameId(), outcome.eraNumber()))
                    .containsExactlyInAnyOrder(outcome, other);
            assertThat(repository.findByGameIdAndEraNumber(outcome.gameId(), 2)).isEmpty();
        }
    }
}
