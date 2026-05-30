package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
}
