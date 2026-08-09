package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.temporalrift.game.PostgresTestcontainersConfiguration;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.EventDefinition;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.OutcomeDefinition;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostgresTestcontainersConfiguration.class,
    ActionRoundRepositoryAdapter.class,
    PlayerStateRepositoryAdapter.class,
    ActivistEraStateRepositoryAdapter.class,
    ActionRoundSagaAdapter.class,
    CurrentEraFutureEventAdapter.class,
    ParadoxResolutionPhaseRepositoryAdapter.class
})
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
    void actionRound_save_and_findById_roundTripsAllFields() {
        var roundId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var player1 = UUID.randomUUID();
        var player2 = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var cardInstanceId = UUID.randomUUID();

        var round = new ActionRound(roundId, gameId, 2, 3, List.of(player1, player2), 45);
        round.pullEvents();
        round.submit(new SubmittedAction.CardAction(
                player1, cardInstanceId, CardType.SWING, targetEventId, sourceOutcomeId, targetOutcomeId));
        round.submit(new SubmittedAction.SpecialActionSubmission(
                player2, Faction.PROPHETS, SpecialAction.SEAL, targetEventId, targetOutcomeId, targetPlayerId));
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
}
