package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.action.EventsDrawn;
import io.github.temporalrift.events.action.HandDealt;
import io.github.temporalrift.events.session.FactionAssigned;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@ExtendWith(MockitoExtension.class)
class ActionStateProjectionEventListenerTest {

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    FutureEventDefinitionPort futureEventDefinitionPort;

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
                        false)));

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
    void onHandDealt_replacesPlayerHandAndPreservesFaction() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.ERASERS, List.of(), false);
        var event = new HandDealt(
                existing.gameId(),
                1,
                existing.playerId(),
                List.of(new HandDealt.CardInstance(UUID.randomUUID(), CardType.PUSH)));
        given(playerStateRepository.findByGameIdAndPlayerId(existing.gameId(), existing.playerId()))
                .willReturn(Optional.of(existing));

        listener.onHandDealt(event);

        var captor = ArgumentCaptor.forClass(PlayerState.class);
        then(playerStateRepository).should().save(captor.capture());
        assertThat(captor.getValue().faction()).isEqualTo(Faction.ERASERS);
        assertThat(captor.getValue().hand())
                .singleElement()
                .extracting(PlayerState.CardInstance::cardType)
                .isEqualTo(CardType.PUSH);
    }

    @Test
    void onHandDealt_createsPlayerStateWhenMissing() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var card = new HandDealt.CardInstance(UUID.randomUUID(), CardType.SCAN);
        var event = new HandDealt(gameId, 1, playerId, List.of(card));
        given(playerStateRepository.findByGameIdAndPlayerId(gameId, playerId)).willReturn(Optional.empty());

        listener.onHandDealt(event);

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
                List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM)),
                true);
        given(playerStateRepository.findByGameIdAndPlayerId(existing.gameId(), existing.playerId()))
                .willReturn(Optional.of(existing));

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
        given(playerStateRepository.findByGameIdAndPlayerId(gameId, playerId)).willReturn(Optional.empty());

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
                List.of(new PlayerState.CardInstance(UUID.randomUUID(), CardType.TRACE)),
                false);
        given(playerStateRepository.findByGameIdAndPlayerId(existing.gameId(), existing.playerId()))
                .willReturn(Optional.of(existing));

        listener.onFactionAssigned(
                new FactionAssigned(existing.gameId(), existing.playerId(), Faction.PROPHETS.name()));

        then(playerStateRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onFactionAssigned_rejectsConflictingFaction() {
        var existing = PlayerState.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Faction.ERASERS, List.of(), false);
        willReturn(Optional.of(existing))
                .given(playerStateRepository)
                .findByGameIdAndPlayerId(existing.gameId(), existing.playerId());

        assertThatIllegalStateException()
                .isThrownBy(() -> listener.onFactionAssigned(
                        new FactionAssigned(existing.gameId(), existing.playerId(), Faction.WEAVERS.name())))
                .withMessageContaining("Conflicting faction assignment");

        then(playerStateRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
