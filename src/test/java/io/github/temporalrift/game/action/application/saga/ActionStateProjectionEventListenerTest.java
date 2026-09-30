package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.ParadoxResolutionCardsOffering;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.EventsDrawn;
import io.github.temporalrift.game.shared.domain.event.FactionAssigned;
import io.github.temporalrift.game.shared.domain.event.HandDealt;
import io.github.temporalrift.game.shared.domain.event.HandSelected;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.CarryOverState;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class ActionStateProjectionEventListenerTest {

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    FutureEventDefinitionPort futureEventDefinitionPort;

    @Mock
    ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;

    @Mock
    ParadoxResolutionCardsOffering paradoxResolutionCardsOffering;

    @InjectMocks
    ActionStateProjectionEventListener listener;

    @Test
    void onEventsDrawn_replacesEraDefinitions() {
        var event = new EventsDrawn(
                UUID.randomUUID(),
                2,
                List.of(new EventsDrawn.FutureEvent(
                        UUID.randomUUID(),
                        "Title",
                        List.of(new EventsDrawn.Outcome(UUID.randomUUID(), "Outcome", 33)),
                        CarryOverState.FRESH)));

        listener.onEventsDrawn(event);

        then(futureEventDefinitionPort)
                .should()
                .replaceForGameEra(
                        eq(event.gameId()),
                        eq(event.eraNumber()),
                        argThat(definitions -> definitions.size() == 1
                                && definitions
                                        .getFirst()
                                        .eventId()
                                        .equals(event.events().getFirst().eventId())));
    }

    @Test
    void onHandSelected_replacesPlayerHandAndPreservesFaction() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Faction.ERASERS,
                new PlayerState.PersistedState(List.of(), Set.of(), false, false));
        var event = new HandDealt(
                existing.gameId(),
                1,
                existing.playerId(),
                List.of(new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.III)));
        given(playerStateRepository.findOrCreateWithLock(existing.gameId(), existing.playerId()))
                .willReturn(existing);

        listener.onHandSelected(new HandSelected(
                event.gameId(),
                event.eraNumber(),
                event.playerId(),
                HandSelected.SelectionOrigin.PLAYER,
                event.cards()));

        var captor = ArgumentCaptor.forClass(PlayerState.class);
        then(playerStateRepository).should().save(captor.capture());
        assertThat(captor.getValue().faction()).isEqualTo(Faction.ERASERS);
        assertThat(captor.getValue().hand())
                .singleElement()
                .extracting(PlayerState.CardInstance::cardType)
                .isEqualTo(CardType.PUSH);
        assertThat(captor.getValue().hand())
                .singleElement()
                .extracting(PlayerState.CardInstance::grade)
                .isEqualTo(CardGrade.III);
    }

    @Test
    void onHandSelected_beforeTheErasParadoxResolutionPhase_offersNothing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerStateRepository.findOrCreateWithLock(gameId, playerId))
                .willReturn(new PlayerState(UUID.randomUUID(), gameId, playerId));
        given(paradoxResolutionPhaseRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.empty());

        listener.onHandSelected(handSelected(gameId, 2, playerId));

        then(paradoxResolutionCardsOffering).shouldHaveNoInteractions();
    }

    @Test
    void onHandSelected_afterTheErasParadoxResolutionPhaseOpened_adoptsThePlayerWithTheirProjectedHand() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var state = new PlayerState(UUID.randomUUID(), gameId, playerId);
        given(playerStateRepository.findOrCreateWithLock(gameId, playerId)).willReturn(state);
        var phase = new ParadoxResolutionPhase(UUID.randomUUID(), gameId, 2, Instant.parse("2026-08-09T12:00:30Z"));
        given(paradoxResolutionPhaseRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(phase));

        listener.onHandSelected(handSelected(gameId, 2, playerId));

        var inOrder = inOrder(playerStateRepository, paradoxResolutionCardsOffering);
        inOrder.verify(playerStateRepository).save(state);
        inOrder.verify(paradoxResolutionCardsOffering).offer(phase, state);
        assertThat(state.hand()).extracting(PlayerState.CardInstance::cardType).containsExactly(CardType.STABILIZE);
    }

    @Test
    void onHandSelected_afterTheErasParadoxResolutionPhaseClosed_offersNothing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerStateRepository.findOrCreateWithLock(gameId, playerId))
                .willReturn(new PlayerState(UUID.randomUUID(), gameId, playerId));
        var phase = new ParadoxResolutionPhase(UUID.randomUUID(), gameId, 2, Instant.parse("2026-08-09T12:00:30Z"));
        phase.close();
        given(paradoxResolutionPhaseRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(phase));

        listener.onHandSelected(handSelected(gameId, 2, playerId));

        then(paradoxResolutionCardsOffering).shouldHaveNoInteractions();
    }

    @Test
    void onHandSelected_createsPlayerStateWhenMissing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var card = new HandDealt.CardInstance(UUID.randomUUID(), CardType.SCAN);
        var event = new HandDealt(gameId, 1, playerId, List.of(card));
        given(playerStateRepository.findOrCreateWithLock(gameId, playerId))
                .willReturn(new PlayerState(UUID.randomUUID(), gameId, playerId));

        listener.onHandSelected(new HandSelected(
                event.gameId(),
                event.eraNumber(),
                event.playerId(),
                HandSelected.SelectionOrigin.PLAYER,
                event.cards()));

        var captor = ArgumentCaptor.forClass(PlayerState.class);
        then(playerStateRepository).should().save(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(gameId);
        assertThat(captor.getValue().playerId()).isEqualTo(playerId);
        assertThat(captor.getValue().faction()).isNull();
        assertThat(captor.getValue().hand())
                .containsExactly(new PlayerState.CardInstance(card.cardInstanceId(), CardType.SCAN));
        assertThat(captor.getValue().isJammed()).isFalse();
    }

    @Test
    void onFactionAssigned_setsFactionForExistingState() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                new PlayerState.PersistedState(
                        List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM)), Set.of(), true, false));
        given(playerStateRepository.findOrCreateWithLock(existing.gameId(), existing.playerId()))
                .willReturn(existing);

        listener.onFactionAssigned(new FactionAssigned(existing.gameId(), existing.playerId(), Faction.WEAVERS.name()));

        then(playerStateRepository)
                .should()
                .save(argThat(state -> state.faction() == Faction.WEAVERS
                        && state.isJammed()
                        && state.hand().equals(existing.hand())));
    }

    @Test
    void onFactionAssigned_createsPlayerStateWhenMissing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerStateRepository.findOrCreateWithLock(gameId, playerId))
                .willReturn(new PlayerState(UUID.randomUUID(), gameId, playerId));

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, Faction.ACTIVISTS.name()));

        then(playerStateRepository)
                .should()
                .save(argThat(state -> state.gameId().equals(gameId)
                        && state.playerId().equals(playerId)
                        && state.faction() == Faction.ACTIVISTS
                        && state.hand().isEmpty()
                        && !state.isJammed()));
    }

    @Test
    void onFactionAssigned_doesNothingWhenFactionAlreadyAssigned() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Faction.PROPHETS,
                new PlayerState.PersistedState(
                        List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.TRACE)),
                        Set.of(),
                        false,
                        false));
        given(playerStateRepository.findOrCreateWithLock(existing.gameId(), existing.playerId()))
                .willReturn(existing);

        listener.onFactionAssigned(
                new FactionAssigned(existing.gameId(), existing.playerId(), Faction.PROPHETS.name()));

        then(playerStateRepository).should(never()).save(any());
    }

    @Test
    void onFactionAssigned_skipsUnknownFactionWithoutSaving() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, "NOT_A_REAL_FACTION"));

        then(playerStateRepository).should(never()).save(any());
        then(playerStateRepository).should(never()).findOrCreateWithLock(any(), any());
    }

    @Test
    void onFactionAssigned_skipsNullFactionWithoutSaving() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, null));

        then(playerStateRepository).should(never()).save(any());
        then(playerStateRepository).should(never()).findOrCreateWithLock(any(), any());
    }

    @Test
    void onFactionAssigned_rejectsConflictingFaction() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Faction.ERASERS,
                new PlayerState.PersistedState(List.of(), Set.of(), false, false));
        willReturn(existing).given(playerStateRepository).findOrCreateWithLock(existing.gameId(), existing.playerId());

        assertThatIllegalStateException()
                .isThrownBy(() -> listener.onFactionAssigned(
                        new FactionAssigned(existing.gameId(), existing.playerId(), Faction.WEAVERS.name())))
                .withMessageContaining("Conflicting faction assignment");

        then(playerStateRepository).should(never()).save(any());
    }

    private static HandSelected handSelected(UUID gameId, int eraNumber, UUID playerId) {
        return new HandSelected(
                gameId,
                eraNumber,
                playerId,
                HandSelected.SelectionOrigin.PLAYER,
                List.of(new HandDealt.CardInstance(UUID.randomUUID(), CardType.STABILIZE)));
    }
}
