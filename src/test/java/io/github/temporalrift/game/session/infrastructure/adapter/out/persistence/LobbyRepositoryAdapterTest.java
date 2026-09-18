package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.domain.event.PlayerJoinedLobby;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

@ExtendWith(MockitoExtension.class)
class LobbyRepositoryAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    LobbyJpaRepository jpaRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    LobbyRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LobbyRepositoryAdapter(jpaRepository, eventPublisher, CLOCK, applicationEventPublisher);
        given(jpaRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void save_dualPublishesPulledEventsWithLobbyMetadata() {
        // given
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostId = UUID.randomUUID();
        var guestId = UUID.randomUUID();
        var config = new LobbyConfig("X7K2P9", 2, 5, CLOCK);
        var lobby =
                new Lobby(lobbyId, gameId, hostId, List.of(new LobbyPlayer(hostId, "Alice", null, null, true)), config);
        lobby.join(guestId, "Bob");

        // when
        adapter.save(lobby);

        // then
        var envelopeCaptor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(eventPublisher).should(times(3)).publish(envelopeCaptor.capture());
        var envelopes = envelopeCaptor.getAllValues();
        assertThat(envelopes).hasSize(3);
        envelopes.forEach(envelope -> {
            assertThat(envelope.aggregateId()).isEqualTo(lobbyId);
            assertThat(envelope.aggregateType()).isEqualTo(Lobby.AGGREGATE_TYPE);
            assertThat(envelope.gameId()).isEqualTo(gameId);
            assertThat(envelope.version()).isEqualTo(DomainEventEnvelope.SCHEMA_VERSION_V1);
            assertThat(envelope.occurredAt()).isEqualTo(CLOCK.instant());
        });
        assertThat(envelopes.get(0).payload()).isInstanceOf(LobbyCreated.class);
        assertThat(envelopes.get(1).payload()).isInstanceOf(PlayerJoinedLobby.class);
        assertThat(envelopes.get(2).payload()).isInstanceOf(PlayerJoinedLobby.class);

        var inProcessCaptor = ArgumentCaptor.forClass(Object.class);
        then(applicationEventPublisher).should(times(3)).publishEvent(inProcessCaptor.capture());
        var delivered = inProcessCaptor.getAllValues();
        assertThat(delivered).hasSize(3);
        for (var index = 0; index < envelopes.size(); index++) {
            assertThat(delivered.get(index)).isSameAs(envelopes.get(index).payload());
        }

        var hostJoined = delivered.stream()
                .filter(PlayerJoinedLobby.class::isInstance)
                .map(PlayerJoinedLobby.class::cast)
                .filter(join -> join.playerId().equals(hostId))
                .findFirst()
                .orElseThrow();
        assertThat(hostJoined.lobbyId()).isEqualTo(lobbyId);
        assertThat(hostJoined.gameId()).isEqualTo(gameId);
        assertThat(hostJoined.playerName()).isEqualTo("Alice");
    }
}
