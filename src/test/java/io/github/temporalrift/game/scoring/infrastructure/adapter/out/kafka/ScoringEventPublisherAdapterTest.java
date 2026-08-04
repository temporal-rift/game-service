package io.github.temporalrift.game.scoring.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;
import io.github.temporalrift.game.shared.ScoresUpdated;

@ExtendWith(MockitoExtension.class)
class ScoringEventPublisherAdapterTest {

    @Mock
    ScoringEventWireMapper mapper;

    @Mock
    OutboundIntegrationEventPublisher outboundEvents;

    @Test
    void publish_mapsScoresUpdatedToTheStableMessageType() {
        var adapter = new ScoringEventPublisherAdapter(mapper, outboundEvents);
        var gameId = UUID.randomUUID();
        var payload = new ScoresUpdated(
                gameId, 1, List.of(new ScoresUpdated.ScoreUpdate(UUID.randomUUID(), Faction.ERASERS, 3, "CHAIN", 12)));
        var event = DomainEventEnvelope.create(
                UUID.randomUUID(), "Scoring", gameId, 2, payload, java.time.Clock.systemUTC());

        adapter.publish(event);

        then(outboundEvents).should().publish(eq("ScoresUpdated"), any(), eq(event));
    }

    @Test
    void publish_rejectsAnUnsupportedPayload() {
        var adapter = new ScoringEventPublisherAdapter(mapper, outboundEvents);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.publish(DomainEventEnvelope.create(
                        UUID.randomUUID(), "Scoring", UUID.randomUUID(), 1, new Object(), java.time.Clock.systemUTC())))
                .withMessageContaining("Unsupported scoring event payload");
        then(outboundEvents).shouldHaveNoInteractions();
        then(mapper).shouldHaveNoInteractions();
    }
}
