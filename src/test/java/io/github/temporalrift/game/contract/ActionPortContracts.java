package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.handselection.HandSelection;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.HandSelectionRepository;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository;
import io.github.temporalrift.game.action.domain.port.out.SpecialActionEraUsageRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOfferStatus;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraBudgetExhaustedException;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;
import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/** Persisted driven ports of one module, one contract each; the tests are shared with the database adapters. */
public final class ActionPortContracts {

    private ActionPortContracts() {}

    public interface ParadoxResolutionPhasePortContract extends PortContract {

        @Test
        default void paradoxResolutionPhase_saveAndLockedLookup_roundTripsState() {
            var phaseId = UUID.randomUUID();
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var now = Instant.parse("2099-01-01T00:00:00Z");
            var phase = new ParadoxResolutionPhase(phaseId, gameId, 2, now.plusSeconds(30));
            phase.submit(playerId, CardType.DETONATE, now);
            port(ParadoxResolutionPhaseRepository.class).save(phase);

            var loaded = port(ParadoxResolutionPhaseRepository.class).findByGameIdAndEraNumberWithLock(gameId, 2);

            assertThat(loaded).isPresent();
            assertThat(loaded.get().id()).isEqualTo(phaseId);
            assertThat(loaded.get().expiresAt()).isEqualTo(now.plusSeconds(30));
            assertThat(loaded.get().status()).isEqualTo(ParadoxResolutionPhaseStatus.OPEN);
            assertThat(loaded.get().submittedPlayerIds()).containsExactly(playerId);
        }
    }

    public interface DeclarationPhasePortContract extends PortContract {

        @Test
        default void declarationPhase_declarationPhase_createIfAbsent_isIdempotentAndRoundTripsClose() {
            var gameId = UUID.randomUUID();
            var now = Instant.parse("2099-01-01T00:00:00Z");
            var phase = new DeclarationPhase(UUID.randomUUID(), gameId, 2, now.plusSeconds(30));

            assertThat(port(DeclarationPhaseRepository.class).createIfAbsent(phase))
                    .isTrue();
            assertThat(port(DeclarationPhaseRepository.class)
                            .createIfAbsent(new DeclarationPhase(UUID.randomUUID(), gameId, 2, now.plusSeconds(30))))
                    .isFalse();

            var loaded = port(DeclarationPhaseRepository.class).findByGameIdAndEraNumberWithLock(gameId, 2);
            assertThat(loaded).isPresent();
            assertThat(loaded.get().id()).isEqualTo(phase.id());
            assertThat(loaded.get().status()).isEqualTo(DeclarationPhaseStatus.OPEN);
            assertThat(loaded.get().closeIfOpen(now.plusSeconds(31))).isTrue();
            port(DeclarationPhaseRepository.class).save(loaded.get());

            assertThat(port(DeclarationPhaseRepository.class)
                            .findByGameIdAndEraNumber(gameId, 2)
                            .orElseThrow()
                            .status())
                    .isEqualTo(DeclarationPhaseStatus.CLOSED);
        }

        @Test
        default void declarationPhase_roundTripsDecisionsAndDuplicateOpeningPreservesThem() {
            var game = UUID.randomUUID();
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            var now = Instant.parse("2026-09-01T12:00:00Z");
            var phase = new DeclarationPhase(UUID.randomUUID(), game, 1, now.plusSeconds(120), List.of(first, second));
            transactionTemplate().executeWithoutResult(_ -> {
                assertThat(port(DeclarationPhaseRepository.class).createIfAbsent(phase))
                        .isTrue();
                phase.decide(first, DeclarationDecision.DECLINED, now);
                port(DeclarationPhaseRepository.class).save(phase);
            });
            transactionTemplate().executeWithoutResult(_ -> {
                var loaded = port(DeclarationPhaseRepository.class)
                        .findByGameIdAndEraNumberWithLock(game, 1)
                        .orElseThrow();
                assertThat(loaded.decisions())
                        .containsEntry(first, DeclarationDecision.DECLINED)
                        .containsEntry(second, DeclarationDecision.PENDING);
                assertThat(port(DeclarationPhaseRepository.class)
                                .createIfAbsent(new DeclarationPhase(
                                        UUID.randomUUID(), game, 1, now.plusSeconds(120), List.of(first, second))))
                        .isFalse();
                assertThat(loaded.decide(second, DeclarationDecision.DECLARED, now))
                        .isTrue();
                port(DeclarationPhaseRepository.class).save(loaded);
            });
            transactionTemplate().executeWithoutResult(_ -> {
                var loaded = port(DeclarationPhaseRepository.class)
                        .findByGameIdAndEraNumberWithLock(game, 1)
                        .orElseThrow();
                assertThat(loaded.status()).isEqualTo(DeclarationPhaseStatus.CLOSED);
                assertThat(loaded.hasDeclined(first)).isTrue();
                assertThat(loaded.decisions()).containsEntry(second, DeclarationDecision.DECLARED);
            });
        }
    }

    public interface ReactiveOfferPortContract extends PortContract {

        @Test
        default void reactiveOffer_createIfAbsent_dealConsumeAndExpireRoundTrip() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var stabilize = UUID.randomUUID();
            var detonate = UUID.randomUUID();
            var offer = new ReactiveOffer(UUID.randomUUID(), gameId, 2, playerId, stabilize, detonate);

            assertThat(port(ReactiveOfferRepository.class).createIfAbsent(offer))
                    .isTrue();
            assertThat(port(ReactiveOfferRepository.class)
                            .createIfAbsent(new ReactiveOffer(
                                    UUID.randomUUID(), gameId, 2, playerId, UUID.randomUUID(), UUID.randomUUID())))
                    .isFalse();

            var loaded = port(ReactiveOfferRepository.class)
                    .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 2, playerId);
            assertThat(loaded).isPresent();
            assertThat(loaded.get().stabilizeCardInstanceId()).isEqualTo(stabilize);
            assertThat(loaded.get().detonateCardInstanceId()).isEqualTo(detonate);
            assertThat(loaded.get().status()).isEqualTo(ReactiveOfferStatus.OFFERED);

            loaded.get().consume(detonate);
            port(ReactiveOfferRepository.class).save(loaded.get());
            assertThat(port(ReactiveOfferRepository.class)
                            .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 2, playerId)
                            .orElseThrow()
                            .status())
                    .isEqualTo(ReactiveOfferStatus.CONSUMED);

            var expired = new ReactiveOffer(
                    UUID.randomUUID(), gameId, 2, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            port(ReactiveOfferRepository.class).createIfAbsent(expired);
            port(ReactiveOfferRepository.class)
                    .findAllByGameIdAndEraNumberWithLock(gameId, 2)
                    .forEach(candidate -> {
                        candidate.expire();
                        port(ReactiveOfferRepository.class).save(candidate);
                    });
            assertThat(port(ReactiveOfferRepository.class).findAllByGameIdAndEraNumberWithLock(gameId, 2))
                    .allSatisfy(candidate -> assertThat(candidate.status()).isNotEqualTo(ReactiveOfferStatus.OFFERED));
        }
    }

    public interface ActionRoundPortContract extends PortContract {

        @Test
        default void actionRound_save_and_findById_roundTripsAllFields() {
            var roundId = UUID.randomUUID();
            var gameId = UUID.randomUUID();
            var player1 = UUID.randomUUID();
            var player2 = UUID.randomUUID();
            var targetEventId = UUID.randomUUID();
            var sourceOutcomeId = UUID.randomUUID();
            var targetOutcomeId = UUID.randomUUID();
            var cardInstanceId = UUID.randomUUID();
            var specialTargetEventId = UUID.randomUUID();
            var specialTargetOutcomeId = UUID.randomUUID();

            var round = new ActionRound(roundId, new ActionRoundConfig(gameId, 2, 3, 45), List.of(player1, player2));
            round.pullEvents();
            round.submit(new SubmittedAction.CardAction(
                    player1, cardInstanceId, CardType.SWING, targetEventId, sourceOutcomeId, targetOutcomeId));
            round.submit(new SubmittedAction.SpecialActionSubmission(
                    player2,
                    Faction.WEAVERS,
                    SpecialAction.THREAD,
                    null,
                    null,
                    specialTargetEventId,
                    specialTargetOutcomeId,
                    null));
            round.close("ALL_SUBMITTED");
            port(ActionRoundRepository.class).save(round);

            var loaded = port(ActionRoundRepository.class).findById(roundId);

            assertThat(loaded).isPresent();
            assertThat(loaded.get().id()).isEqualTo(roundId);
            assertThat(loaded.get().gameId()).isEqualTo(gameId);
            assertThat(loaded.get().eraNumber()).isEqualTo(2);
            assertThat(loaded.get().roundNumber()).isEqualTo(3);
            assertThat(loaded.get().status()).isEqualTo(RoundStatus.CLOSED);
            assertThat(loaded.get().timerSeconds()).isEqualTo(45);
            assertThat(loaded.get().closedReason()).isEqualTo("ALL_SUBMITTED");
            assertThat(loaded.get().pendingPlayerIds()).isEmpty();
            assertThat(loaded.get().submittedActions()).hasSize(2);
            assertThat(loaded.get().submittedActions())
                    .filteredOn(SubmittedAction.CardAction.class::isInstance)
                    .singleElement()
                    .isInstanceOfSatisfying(SubmittedAction.CardAction.class, card -> {
                        assertThat(card.cardType()).isEqualTo(CardType.SWING);
                        assertThat(card.sourceOutcomeId()).isEqualTo(sourceOutcomeId);
                        assertThat(card.targetOutcomeId()).isEqualTo(targetOutcomeId);
                    });
            assertThat(loaded.get().submittedActions())
                    .filteredOn(SubmittedAction.SpecialActionSubmission.class::isInstance)
                    .singleElement()
                    .isInstanceOfSatisfying(SubmittedAction.SpecialActionSubmission.class, special -> {
                        assertThat(special.specialAction()).isEqualTo(SpecialAction.THREAD);
                        assertThat(special.faction()).isEqualTo(Faction.WEAVERS);
                        assertThat(special.sourceEventId()).isNull();
                        assertThat(special.sourceOutcomeId()).isNull();
                        assertThat(special.targetEventId()).isEqualTo(specialTargetEventId);
                        assertThat(special.targetOutcomeId()).isEqualTo(specialTargetOutcomeId);
                        assertThat(special.targetPlayerId()).isNull();
                    });
        }

        @Test
        default void actionRound_save_and_find_roundTripsNullifyTargetList() {
            var roundId = UUID.randomUUID();
            var gameId = UUID.randomUUID();
            var submitter = UUID.randomUUID();
            var firstTarget = UUID.randomUUID();
            var secondTarget = UUID.randomUUID();
            var targets = List.of(firstTarget, secondTarget);

            var round = new ActionRound(
                    roundId, new ActionRoundConfig(gameId, 1, 1, 45), List.of(submitter, firstTarget, secondTarget));
            round.pullEvents();
            round.submit(new SubmittedAction.CardAction(
                    submitter,
                    UUID.randomUUID(),
                    CardType.NULLIFY,
                    CardGrade.II,
                    null,
                    null,
                    null,
                    null,
                    null,
                    targets,
                    null));
            port(ActionRoundRepository.class).save(round);

            var loaded = port(ActionRoundRepository.class).findById(roundId);

            assertThat(loaded).isPresent();
            assertThat(loaded.get().submittedActions())
                    .filteredOn(SubmittedAction.CardAction.class::isInstance)
                    .singleElement()
                    .isInstanceOfSatisfying(SubmittedAction.CardAction.class, card -> {
                        assertThat(card.cardType()).isEqualTo(CardType.NULLIFY);
                        assertThat(card.grade()).isEqualTo(CardGrade.II);
                        assertThat(card.targetPlayerId()).isNull();
                        assertThat(card.targetPlayerIds()).containsExactly(firstTarget, secondTarget);
                    });
        }

        @Test
        default void rewriteSubmission_save_and_reload_retainsPrivateTargets() {
            var gameId = UUID.randomUUID();
            var player1 = UUID.randomUUID();
            var player2 = UUID.randomUUID();
            var targetEventId = UUID.randomUUID();
            var targetOutcomeId = UUID.randomUUID();
            var round = new ActionRound(
                    UUID.randomUUID(),
                    new ActionRoundConfig(gameId, 1, 1, 60),
                    io.github.temporalrift.game.action.domain.actionround.ActionRoundParticipants.pending(
                            List.of(player1, player2)));
            round.submit(new SubmittedAction.SpecialActionSubmission(
                    player1,
                    Faction.REVISIONISTS,
                    SpecialAction.REWRITE,
                    null,
                    null,
                    targetEventId,
                    targetOutcomeId,
                    null));
            port(ActionRoundRepository.class).save(round);

            var loaded = port(ActionRoundRepository.class).findByGameIdAndEraNumberAndRoundNumber(gameId, 1, 1);

            assertThat(loaded).isPresent();
            assertThat(loaded.orElseThrow().submittedActions())
                    .anySatisfy(action -> assertThat(action)
                            .isInstanceOfSatisfying(SubmittedAction.SpecialActionSubmission.class, special -> {
                                assertThat(special.playerId()).isEqualTo(player1);
                                assertThat(special.specialAction()).isEqualTo(SpecialAction.REWRITE);
                                assertThat(special.targetEventId()).isEqualTo(targetEventId);
                                assertThat(special.targetOutcomeId()).isEqualTo(targetOutcomeId);
                            }));
        }
    }

    public interface PlayerStatePortContract extends PortContract {

        @Test
        default void playerState_save_and_find_roundTripsAllFields() {
            var state = new PlayerState(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            state.assignFaction(Faction.ERASERS);
            state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH), 5);
            state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM), 5);
            state.applyJam();

            port(PlayerStateRepository.class).save(state);

            var loaded = port(PlayerStateRepository.class).findByGameIdAndPlayerId(state.gameId(), state.playerId());

            assertThat(loaded).isPresent();
            assertThat(loaded.get().id()).isEqualTo(state.id());
            assertThat(loaded.get().faction()).isEqualTo(Faction.ERASERS);
            assertThat(loaded.get().isJammed()).isTrue();
            assertThat(loaded.get().hand()).containsExactlyElementsOf(state.hand());
        }

        @Test
        default void playerState_save_replacesExistingRow() {
            var state = new PlayerState(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH), 5);
            port(PlayerStateRepository.class).save(state);

            var updated = PlayerState.reconstitute(
                    state.id(),
                    state.gameId(),
                    state.playerId(),
                    Faction.REVISIONISTS,
                    new PlayerState.PersistedState(
                            List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.SUPPRESS)),
                            Set.of(),
                            true,
                            false));
            port(PlayerStateRepository.class).save(updated);

            var all = port(PlayerStateRepository.class).findAllByGameId(state.gameId());

            assertThat(all).singleElement().satisfies(saved -> {
                assertThat(saved.faction()).isEqualTo(Faction.REVISIONISTS);
                assertThat(saved.isJammed()).isTrue();
                assertThat(saved.hand()).containsExactlyElementsOf(updated.hand());
            });
        }
    }

    public interface ActivistEraStatePortContract extends PortContract {

        @Test
        default void activistEraState_save_lookup_and_declared_exposed_queries_roundTripAllFields() {
            var gameId = UUID.randomUUID();
            var activistPlayerId = UUID.randomUUID();
            var targetPlayerId = UUID.randomUUID();
            var targetEventId = UUID.randomUUID();
            var sourceOutcomeId = UUID.randomUUID();
            var targetOutcomeId = UUID.randomUUID();
            var state = new ActivistEraState(UUID.randomUUID(), gameId, 2, activistPlayerId, true);
            state.declare(ActivistDeclarationMode.MOMENTUM, targetEventId, targetOutcomeId);
            state.recordResolution(true);
            var signature =
                    new ProbabilityInfluenceSignature(CardType.SWING, targetEventId, sourceOutcomeId, targetOutcomeId);
            state.expose(targetPlayerId, signature);
            state.recordExposeBehaviorChanged(
                    new ProbabilityInfluenceSignature(CardType.PUSH, targetEventId, sourceOutcomeId, targetOutcomeId));
            port(ActivistEraStateRepository.class).save(state);

            var emptyState = new ActivistEraState(UUID.randomUUID(), gameId, 2, UUID.randomUUID(), false);
            port(ActivistEraStateRepository.class).save(emptyState);
            var bluffTargetState = new ActivistEraState(UUID.randomUUID(), gameId, 2, UUID.randomUUID(), false);
            bluffTargetState.expose(UUID.randomUUID(), null);
            port(ActivistEraStateRepository.class).save(bluffTargetState);

            var loaded = port(ActivistEraStateRepository.class)
                    .findByGameIdAndEraNumberAndActivistPlayerId(gameId, 2, activistPlayerId);

            assertThat(loaded).hasValueSatisfying(saved -> {
                assertThat(saved.id()).isEqualTo(state.id());
                assertThat(saved.momentumEligible()).isTrue();
                assertThat(saved.declarationSucceeded()).isTrue();
                assertThat(saved.declarationMode()).isEqualTo(ActivistDeclarationMode.MOMENTUM);
                assertThat(saved.targetEventId()).isEqualTo(targetEventId);
                assertThat(saved.targetOutcomeId()).isEqualTo(targetOutcomeId);
                assertThat(saved.exposedPlayerId()).isEqualTo(targetPlayerId);
                assertThat(saved.exposedSignature()).isEqualTo(signature);
                assertThat(saved.exposeBehaviorChanged()).isTrue();
            });
            assertThat(port(ActivistEraStateRepository.class).findDeclaredByGameIdAndEraNumber(gameId, 2))
                    .extracting(ActivistEraState::id)
                    .containsExactly(state.id());
            assertThat(port(ActivistEraStateRepository.class).findExposedByGameIdAndEraNumber(gameId, 2))
                    .extracting(ActivistEraState::id)
                    .containsExactlyInAnyOrder(state.id(), bluffTargetState.id());
            assertThat(port(ActivistEraStateRepository.class)
                            .findByGameIdAndEraNumberAndActivistPlayerId(
                                    gameId, 2, bluffTargetState.activistPlayerId()))
                    .hasValueSatisfying(saved -> {
                        assertThat(saved.exposedPlayerId()).isEqualTo(bluffTargetState.exposedPlayerId());
                        assertThat(saved.exposedSignature()).isNull();
                    });
            assertThat(port(ActivistEraStateRepository.class)
                            .findByGameIdAndEraNumberAndActivistPlayerId(gameId, 2, emptyState.activistPlayerId()))
                    .hasValueSatisfying(saved -> {
                        assertThat(saved.declarationMode()).isNull();
                        assertThat(saved.exposedSignature()).isNull();
                    });
        }
    }

    public interface ActionRoundSagaPortContract extends PortContract {

        @Test
        default void actionRoundSagaState_save_and_lookup_roundTrips() {
            var timerExpiresAt = Instant.parse("2099-01-01T00:00:30Z");
            var state = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    2,
                    ActionRoundSagaStatus.WAITING,
                    List.of(UUID.randomUUID(), UUID.randomUUID()),
                    timerExpiresAt);
            port(ActionRoundSagaRepository.class).save(state);

            var loaded = port(ActionRoundSagaRepository.class)
                    .findByGameIdAndEraNumberAndRoundNumber(state.gameId(), state.eraNumber(), state.roundNumber());

            assertThat(loaded).contains(state);
        }
    }

    public interface SpecialActionEraUsagePortContract extends PortContract {

        @Test
        default void specialActionEraUsage_save_and_find_roundTripsClaimedSpecials() {
            var usageId = UUID.randomUUID();
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var usage = new SpecialActionEraUsage(usageId, gameId, 2, playerId);
            usage.claim(SpecialAction.ANNIHILATE);
            usage.claim(SpecialAction.CORRUPT);

            port(SpecialActionEraUsageRepository.class).save(usage);

            var loaded = port(SpecialActionEraUsageRepository.class)
                    .findByGameIdAndEraNumberAndPlayerId(gameId, 2, playerId);

            assertThat(loaded).hasValueSatisfying(saved -> {
                assertThat(saved.id()).isEqualTo(usageId);
                assertThat(saved.gameId()).isEqualTo(gameId);
                assertThat(saved.eraNumber()).isEqualTo(2);
                assertThat(saved.playerId()).isEqualTo(playerId);
                assertThat(saved.claimedSpecials())
                        .containsExactlyInAnyOrder(SpecialAction.ANNIHILATE, SpecialAction.CORRUPT);
            });
        }

        @Test
        default void specialActionEraUsage_reloadedInstance_stillEnforcesTheBudget() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var usage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, playerId);
            usage.claim(SpecialAction.SEAL);
            port(SpecialActionEraUsageRepository.class).save(usage);

            var reloaded = port(SpecialActionEraUsageRepository.class)
                    .findByGameIdAndEraNumberAndPlayerId(gameId, 1, playerId);

            assertThat(reloaded).isPresent();
            var reloadedUsage = reloaded.orElseThrow();
            assertThatThrownBy(() -> reloadedUsage.claim(SpecialAction.SEAL))
                    .isInstanceOf(SpecialActionEraBudgetExhaustedException.class);
        }

        @Test
        default void specialActionEraUsage_differentGameEraOrPlayer_isIndependent() {
            var playerId = UUID.randomUUID();
            var usage = new SpecialActionEraUsage(UUID.randomUUID(), UUID.randomUUID(), 1, playerId);
            usage.claim(SpecialAction.MIMIC);
            port(SpecialActionEraUsageRepository.class).save(usage);

            assertThat(port(SpecialActionEraUsageRepository.class)
                            .findByGameIdAndEraNumberAndPlayerId(UUID.randomUUID(), 1, playerId))
                    .isEmpty();
            assertThat(port(SpecialActionEraUsageRepository.class)
                            .findByGameIdAndEraNumberAndPlayerId(usage.gameId(), 2, playerId))
                    .isEmpty();
            assertThat(port(SpecialActionEraUsageRepository.class)
                            .findByGameIdAndEraNumberAndPlayerId(usage.gameId(), 1, UUID.randomUUID()))
                    .isEmpty();
        }
    }

    public interface SealGameUsagePortContract extends PortContract {

        @Test
        default void sealGameUsage_countsSealClaimsAcrossEras() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            for (int era = 1; era <= 2; era++) {
                var usage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, era, playerId);
                usage.claim(SpecialAction.SEAL);
                port(SpecialActionEraUsageRepository.class).save(usage);
            }

            assertThat(port(SealGameUsageRepository.class).countAcceptedSeals(gameId, playerId))
                    .isEqualTo(2);
        }

        @Test
        default void sealGameUsage_ignoresOtherPlayersGamesAndSpecials() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var ownEraUsage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, playerId);
            ownEraUsage.claim(SpecialAction.ANNIHILATE);
            port(SpecialActionEraUsageRepository.class).save(ownEraUsage);
            var otherPlayerUsage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, UUID.randomUUID());
            otherPlayerUsage.claim(SpecialAction.SEAL);
            port(SpecialActionEraUsageRepository.class).save(otherPlayerUsage);
            var otherGameUsage = new SpecialActionEraUsage(UUID.randomUUID(), UUID.randomUUID(), 1, playerId);
            otherGameUsage.claim(SpecialAction.SEAL);
            port(SpecialActionEraUsageRepository.class).save(otherGameUsage);

            assertThat(port(SealGameUsageRepository.class).countAcceptedSeals(gameId, playerId))
                    .isZero();
        }
    }

    public interface HandSelectionPortContract extends PortContract {

        @Test
        default void handSelection_createIfAbsent_duplicateDoesNotAbortTheTransactionOrScheduleAnotherSelection() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var selection = selection(gameId, playerId);
            var duplicate = selection(gameId, playerId);
            var anotherPlayer = selection(gameId, UUID.randomUUID());

            transactionTemplate().executeWithoutResult(_ -> {
                assertThat(port(HandSelectionRepository.class).createIfAbsent(selection))
                        .isTrue();
                assertThat(port(HandSelectionRepository.class).createIfAbsent(duplicate))
                        .isFalse();
                assertThat(port(HandSelectionRepository.class).createIfAbsent(anotherPlayer))
                        .isTrue();
            });

            Optional<HandSelection> loaded = transactionTemplate()
                    .execute(_ -> port(HandSelectionRepository.class)
                            .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 1, playerId));

            assertThat(loaded).contains(selection);
        }

        @Test
        default void handSelection_saveAndLoad_roundTripsNonDefaultGradesForDealtAndSelectedCards() {
            var gameId = UUID.randomUUID();
            var playerId = UUID.randomUUID();
            var openSelection = selection(gameId, playerId);
            var selection = openSelection.select(
                    openSelection.dealtCards().stream()
                            .limit(5)
                            .map(HandDealt.CardInstance::cardInstanceId)
                            .collect(java.util.stream.Collectors.toSet()),
                    Instant.EPOCH);

            transactionTemplate()
                    .executeWithoutResult(
                            _ -> port(HandSelectionRepository.class).save(selection));

            Optional<HandSelection> loaded = transactionTemplate()
                    .execute(_ -> port(HandSelectionRepository.class)
                            .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 1, playerId));

            assertThat(loaded).isPresent();
            assertThat(loaded.orElseThrow().dealtCards())
                    .extracting(HandDealt.CardInstance::grade)
                    .containsOnly(CardGrade.II);
            assertThat(loaded.orElseThrow().selectedCards())
                    .extracting(HandDealt.CardInstance::grade)
                    .containsOnly(CardGrade.II);
        }

        private static HandSelection selection(UUID gameId, UUID playerId) {
            var cards = java.util.stream.IntStream.rangeClosed(1, 7)
                    .mapToObj(slot -> new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.II, slot))
                    .toList();
            return HandSelection.open(new HandDealt(gameId, 1, playerId, Instant.parse("2026-08-14T00:01:00Z"), cards));
        }
    }
}
