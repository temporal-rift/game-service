package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class TimelineCollapsePublisherTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final UUID PLAYER_3 = UUID.randomUUID();

    @Mock
    LobbyRepository lobbyRepository;

    @Mock
    SessionActivistDeclarationRepository declarationRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    TimelineCollapsePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TimelineCollapsePublisher(
                lobbyRepository, declarationRepository, eventPublisher, applicationEventPublisher, Clock.systemUTC());
    }

    @Test
    @DisplayName("reveal-ordered collapsing event decides Activist winners")
    void publishCollapse_targetingActivistWins() {
        var collapsingEvent = UUID.randomUUID();
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 3, GameStatus.IN_PROGRESS);
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(startedLobby()));
        given(declarationRepository.findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent))
                .willReturn(List.of(PLAYER_2));
        var captor = ArgumentCaptor.<DomainEventEnvelope>captor();

        publisher.publishCollapse(game, 2, collapsingEvent);

        then(eventPublisher).should().publish(captor.capture());
        var collapsed = (TimelineCollapsed) captor.getValue().payload();
        assertThat(collapsed.winners())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactly(PLAYER_2);
        assertThat(collapsed.losers())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_3);
    }

    @Test
    @DisplayName("non-Activist targeting the collapsing event does not win")
    void publishCollapse_nonActivistTargetingDoesNotWin() {
        var collapsingEvent = UUID.randomUUID();
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 2, 3, GameStatus.IN_PROGRESS);
        given(lobbyRepository.findById(LOBBY_ID)).willReturn(Optional.of(startedLobby()));
        given(declarationRepository.findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent))
                .willReturn(List.of(PLAYER_1));
        var captor = ArgumentCaptor.<DomainEventEnvelope>captor();

        publisher.publishCollapse(game, 2, collapsingEvent);

        then(eventPublisher).should().publish(captor.capture());
        var collapsed = (TimelineCollapsed) captor.getValue().payload();
        assertThat(collapsed.winners()).isEmpty();
        assertThat(collapsed.losers())
                .extracting(TimelineCollapsed.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_2, PLAYER_3);
        then(declarationRepository).should().findPlayerIdsTargeting(GAME_ID, 2, collapsingEvent);
    }

    private static Lobby startedLobby() {
        var players = List.of(
                new LobbyPlayer(PLAYER_1, "P1", Faction.ERASERS, Instant.EPOCH, true),
                new LobbyPlayer(PLAYER_2, "P2", Faction.ACTIVISTS, Instant.EPOCH, true),
                new LobbyPlayer(PLAYER_3, "P3", Faction.REVISIONISTS, Instant.EPOCH, true));
        var config = new LobbyConfig("ABCD2345", 3, 5, Clock.systemUTC());
        return Lobby.reconstitute(LOBBY_ID, GAME_ID, PLAYER_1, players, LobbyStatus.STARTED, config);
    }
}
