package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SessionEventPublisherAdapterTest {

    @Mock
    SessionEventWireMapper mapper;

    @Mock
    OutboundIntegrationEventPublisher outboundEvents;

    @Test
    void publish_usesStableAsyncApiMessageNames() {
        var adapter = new SessionEventPublisherAdapter(mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        var lobbyCreated = envelope(gameId, new LobbyCreated(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        var resolutionStarted = envelope(gameId, new ResolutionStarted(gameId, 1));

        adapter.publish(lobbyCreated);
        adapter.publish(resolutionStarted);

        then(outboundEvents).should().publish(eq("LobbyCreated"), any(), eq(lobbyCreated));
        then(outboundEvents).should().publish(eq("ResolutionStarted"), any(), eq(resolutionStarted));
    }

    @Test
    void publish_rejectsAnUnsupportedPayload() {
        var adapter = new SessionEventPublisherAdapter(mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        record UnsupportedEvent(UUID gameId) {}

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.publish(envelope(gameId, new UnsupportedEvent(gameId))))
                .withMessageContaining("Unsupported session event payload");
    }

    private static DomainEventEnvelope<?> envelope(UUID gameId, Object payload) {
        return DomainEventEnvelope.create(
                UUID.randomUUID(), "Session", gameId, 1, payload, java.time.Clock.systemUTC());
    }
}
