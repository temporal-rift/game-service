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
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.EventDefinition;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort.OutcomeDefinition;
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
    ActionRoundSagaAdapter.class,
    CurrentEraFutureEventAdapter.class
})
class ActionPersistenceIT {

    @Autowired
    ActionRoundRepository actionRoundRepository;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    ActionRoundSagaRepository actionRoundSagaRepository;

    @Autowired
    FutureEventDefinitionPort futureEventDefinitionPort;

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
        round.submitCard(
                player1,
                cardInstanceId,
                CardType.SWING,
                targetEventId,
                sourceOutcomeId,
                targetOutcomeId,
                List.of(cardInstanceId));
        round.submitSpecial(
                player2, Faction.PROPHETS, SpecialAction.SEAL, targetEventId, targetOutcomeId, targetPlayerId, false);
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
}
