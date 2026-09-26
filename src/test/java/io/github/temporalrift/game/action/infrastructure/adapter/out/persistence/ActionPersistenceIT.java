package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.EventDefinition;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.OutcomeDefinition;
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
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@PersistenceIntegrationTest
class ActionPersistenceIT {

    @Autowired
    ActionRoundRepository actionRoundRepository;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    ActivistEraStateRepository activistEraStateRepository;

    @Autowired
    ActionRoundSagaRepository actionRoundSagaRepository;

    @Autowired
    FutureEventDefinitionPort futureEventDefinitionPort;

    @Autowired
    ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;

    @Autowired
    DeclarationPhaseRepository declarationPhaseRepository;

    @Autowired
    ReactiveOfferRepository reactiveOfferRepository;

    @Autowired
    SpecialActionEraUsageRepository specialActionEraUsageRepository;

    @Autowired
    SealGameUsageRepository sealGameUsageRepository;

    @Test
    void paradoxResolutionPhase_saveAndLockedLookup_roundTripsState() {
        var phaseId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var now = Instant.parse("2099-01-01T00:00:00Z");
        var phase = new ParadoxResolutionPhase(phaseId, gameId, 2, now.plusSeconds(30));
        phase.submit(playerId, CardType.DETONATE, now);
        paradoxResolutionPhaseRepository.save(phase);

        var loaded = paradoxResolutionPhaseRepository.findByGameIdAndEraNumberWithLock(gameId, 2);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(phaseId);
        assertThat(loaded.get().expiresAt()).isEqualTo(now.plusSeconds(30));
        assertThat(loaded.get().status()).isEqualTo(ParadoxResolutionPhaseStatus.OPEN);
        assertThat(loaded.get().submittedPlayerIds()).containsExactly(playerId);
    }

    @Test
    void declarationPhase_createIfAbsent_isIdempotentAndRoundTripsClose() {
        var gameId = UUID.randomUUID();
        var now = Instant.parse("2099-01-01T00:00:00Z");
        var phase = new DeclarationPhase(UUID.randomUUID(), gameId, 2, now.plusSeconds(30));

        assertThat(declarationPhaseRepository.createIfAbsent(phase)).isTrue();
        assertThat(declarationPhaseRepository.createIfAbsent(
                        new DeclarationPhase(UUID.randomUUID(), gameId, 2, now.plusSeconds(30))))
                .isFalse();

        var loaded = declarationPhaseRepository.findByGameIdAndEraNumberWithLock(gameId, 2);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(phase.id());
        assertThat(loaded.get().status()).isEqualTo(DeclarationPhaseStatus.OPEN);
        assertThat(loaded.get().closeIfOpen(now.plusSeconds(31))).isTrue();
        declarationPhaseRepository.save(loaded.get());

        assertThat(declarationPhaseRepository
                        .findByGameIdAndEraNumber(gameId, 2)
                        .orElseThrow()
                        .status())
                .isEqualTo(DeclarationPhaseStatus.CLOSED);
    }

    @Test
    void reactiveOffer_createIfAbsent_dealConsumeAndExpireRoundTrip() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var stabilize = UUID.randomUUID();
        var detonate = UUID.randomUUID();
        var offer = new ReactiveOffer(UUID.randomUUID(), gameId, 2, playerId, stabilize, detonate);

        assertThat(reactiveOfferRepository.createIfAbsent(offer)).isTrue();
        assertThat(reactiveOfferRepository.createIfAbsent(new ReactiveOffer(
                        UUID.randomUUID(), gameId, 2, playerId, UUID.randomUUID(), UUID.randomUUID())))
                .isFalse();

        var loaded = reactiveOfferRepository.findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 2, playerId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().stabilizeCardInstanceId()).isEqualTo(stabilize);
        assertThat(loaded.get().detonateCardInstanceId()).isEqualTo(detonate);
        assertThat(loaded.get().status()).isEqualTo(ReactiveOfferStatus.OFFERED);

        loaded.get().consume(detonate);
        reactiveOfferRepository.save(loaded.get());
        assertThat(reactiveOfferRepository
                        .findByGameIdAndEraNumberAndPlayerIdWithLock(gameId, 2, playerId)
                        .orElseThrow()
                        .status())
                .isEqualTo(ReactiveOfferStatus.CONSUMED);

        var expired = new ReactiveOffer(
                UUID.randomUUID(), gameId, 2, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        reactiveOfferRepository.createIfAbsent(expired);
        reactiveOfferRepository.findAllByGameIdAndEraNumberWithLock(gameId, 2).forEach(candidate -> {
            candidate.expire();
            reactiveOfferRepository.save(candidate);
        });
        assertThat(reactiveOfferRepository.findAllByGameIdAndEraNumberWithLock(gameId, 2))
                .allSatisfy(candidate -> assertThat(candidate.status()).isNotEqualTo(ReactiveOfferStatus.OFFERED));
    }

    @Test
    void actionRound_save_and_findById_roundTripsAllFields() {
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
        actionRoundRepository.save(round);

        var loaded = actionRoundRepository.findById(roundId);

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
    void actionRound_save_and_find_roundTripsNullifyTargetList() {
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
                submitter, UUID.randomUUID(), CardType.NULLIFY, CardGrade.II, null, null, null, null, null, targets));
        actionRoundRepository.save(round);

        var loaded = actionRoundRepository.findById(roundId);

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
    void playerState_save_and_find_roundTripsAllFields() {
        var state = new PlayerState(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        state.assignFaction(Faction.ERASERS);
        state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH), 5);
        state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM), 5);
        state.applyJam();

        playerStateRepository.save(state);

        var loaded = playerStateRepository.findByGameIdAndPlayerId(state.gameId(), state.playerId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(state.id());
        assertThat(loaded.get().faction()).isEqualTo(Faction.ERASERS);
        assertThat(loaded.get().isJammed()).isTrue();
        assertThat(loaded.get().hand()).containsExactlyElementsOf(state.hand());
    }

    @Test
    void playerState_save_replacesExistingRow() {
        var state = new PlayerState(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        state.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH), 5);
        playerStateRepository.save(state);

        var updated = PlayerState.reconstitute(
                state.id(),
                state.gameId(),
                state.playerId(),
                Faction.REVISIONISTS,
                List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.SUPPRESS)),
                true);
        playerStateRepository.save(updated);

        var all = playerStateRepository.findAllByGameId(state.gameId());

        assertThat(all).singleElement().satisfies(saved -> {
            assertThat(saved.faction()).isEqualTo(Faction.REVISIONISTS);
            assertThat(saved.isJammed()).isTrue();
            assertThat(saved.hand()).containsExactlyElementsOf(updated.hand());
        });
    }

    @Test
    void activistEraState_save_lookup_and_declared_exposed_queries_roundTripAllFields() {
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
        activistEraStateRepository.save(state);

        var emptyState = new ActivistEraState(UUID.randomUUID(), gameId, 2, UUID.randomUUID(), false);
        activistEraStateRepository.save(emptyState);

        var loaded =
                activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(gameId, 2, activistPlayerId);

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
        assertThat(activistEraStateRepository.findDeclaredByGameIdAndEraNumber(gameId, 2))
                .extracting(ActivistEraState::id)
                .containsExactly(state.id());
        assertThat(activistEraStateRepository.findExposedByGameIdAndEraNumber(gameId, 2))
                .extracting(ActivistEraState::id)
                .containsExactly(state.id());
        assertThat(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(
                        gameId, 2, emptyState.activistPlayerId()))
                .hasValueSatisfying(saved -> {
                    assertThat(saved.declarationMode()).isNull();
                    assertThat(saved.exposedSignature()).isNull();
                });
    }

    @Test
    void actionRoundSagaState_save_and_lookup_roundTrips() {
        var timerExpiresAt = Instant.parse("2099-01-01T00:00:30Z");
        var state = new ActionRoundSagaState(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                2,
                ActionRoundSagaStatus.WAITING,
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                timerExpiresAt);
        actionRoundSagaRepository.save(state);

        var loaded = actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(
                state.gameId(), state.eraNumber(), state.roundNumber());

        assertThat(loaded).contains(state);
    }

    @Test
    void futureEventDefinitionPort_returnsStoredEraDefinitions() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var adapter = (CurrentEraFutureEventAdapter) futureEventDefinitionPort;
        adapter.replaceForGameEra(
                gameId, 1, List.of(new EventDefinition(eventId, List.of(new OutcomeDefinition(outcomeId, 70)))));

        var loaded = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, 1);

        assertThat(loaded).containsExactly(new EventDefinition(eventId, List.of(new OutcomeDefinition(outcomeId, 70))));
    }

    @Test
    void futureEventDefinitionPort_replaceForGameEra_removesPriorOutcomesBeforeDefinitions() {
        var gameId = UUID.randomUUID();
        var oldEventId = UUID.randomUUID();
        var oldOutcomeId = UUID.randomUUID();
        var newEventId = UUID.randomUUID();
        var newOutcomeId = UUID.randomUUID();
        var adapter = (CurrentEraFutureEventAdapter) futureEventDefinitionPort;
        adapter.replaceForGameEra(
                gameId, 1, List.of(new EventDefinition(oldEventId, List.of(new OutcomeDefinition(oldOutcomeId, 40)))));

        // A second replacement must not violate the outcome table's FK to its parent definition —
        // proving old outcome rows are deleted before old definition rows, not just that the final
        // state is correct.
        adapter.replaceForGameEra(
                gameId, 1, List.of(new EventDefinition(newEventId, List.of(new OutcomeDefinition(newOutcomeId, 85)))));

        var loaded = futureEventDefinitionPort.findByGameIdAndEraNumber(gameId, 1);

        assertThat(loaded)
                .containsExactly(new EventDefinition(newEventId, List.of(new OutcomeDefinition(newOutcomeId, 85))));
    }

    @Test
    void specialActionEraUsage_save_and_find_roundTripsClaimedSpecials() {
        var usageId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var usage = new SpecialActionEraUsage(usageId, gameId, 2, playerId);
        usage.claim(SpecialAction.ANNIHILATE);
        usage.claim(SpecialAction.CORRUPT);

        specialActionEraUsageRepository.save(usage);

        var loaded = specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(gameId, 2, playerId);

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
    void specialActionEraUsage_reloadedInstance_stillEnforcesTheBudget() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, playerId);
        usage.claim(SpecialAction.SEAL);
        specialActionEraUsageRepository.save(usage);

        var reloaded = specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(gameId, 1, playerId);

        assertThat(reloaded).isPresent();
        var reloadedUsage = reloaded.orElseThrow();
        assertThatThrownBy(() -> reloadedUsage.claim(SpecialAction.SEAL))
                .isInstanceOf(SpecialActionEraBudgetExhaustedException.class);
    }

    @Test
    void specialActionEraUsage_differentGameEraOrPlayer_isIndependent() {
        var playerId = UUID.randomUUID();
        var usage = new SpecialActionEraUsage(UUID.randomUUID(), UUID.randomUUID(), 1, playerId);
        usage.claim(SpecialAction.MIMIC);
        specialActionEraUsageRepository.save(usage);

        assertThat(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(UUID.randomUUID(), 1, playerId))
                .isEmpty();
        assertThat(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(usage.gameId(), 2, playerId))
                .isEmpty();
        assertThat(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(
                        usage.gameId(), 1, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void sealGameUsage_countsSealClaimsAcrossEras() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        for (int era = 1; era <= 2; era++) {
            var usage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, era, playerId);
            usage.claim(SpecialAction.SEAL);
            specialActionEraUsageRepository.save(usage);
        }

        assertThat(sealGameUsageRepository.countAcceptedSeals(gameId, playerId)).isEqualTo(2);
    }

    @Test
    void sealGameUsage_ignoresOtherPlayersGamesAndSpecials() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var ownEraUsage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, playerId);
        ownEraUsage.claim(SpecialAction.ANNIHILATE);
        specialActionEraUsageRepository.save(ownEraUsage);
        var otherPlayerUsage = new SpecialActionEraUsage(UUID.randomUUID(), gameId, 1, UUID.randomUUID());
        otherPlayerUsage.claim(SpecialAction.SEAL);
        specialActionEraUsageRepository.save(otherPlayerUsage);
        var otherGameUsage = new SpecialActionEraUsage(UUID.randomUUID(), UUID.randomUUID(), 1, playerId);
        otherGameUsage.claim(SpecialAction.SEAL);
        specialActionEraUsageRepository.save(otherGameUsage);

        assertThat(sealGameUsageRepository.countAcceptedSeals(gameId, playerId)).isZero();
    }

    @Test
    void rewriteSubmission_save_and_reload_retainsPrivateTargets() {
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
        actionRoundRepository.save(round);

        var loaded = actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(gameId, 1, 1);

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
