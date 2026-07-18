package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import java.util.HashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.EventsDrawn;
import io.github.temporalrift.game.session.FactionAssigned;
import io.github.temporalrift.game.session.GameEnded;
import io.github.temporalrift.game.session.HandDealt;
import io.github.temporalrift.game.session.domain.event.EraEnded;
import io.github.temporalrift.game.session.domain.event.EraFailed;
import io.github.temporalrift.game.session.domain.event.EraStarted;
import io.github.temporalrift.game.session.domain.event.FactionRevealed;
import io.github.temporalrift.game.session.domain.event.FactionsDrawn;
import io.github.temporalrift.game.session.domain.event.GameEndedAbnormally;
import io.github.temporalrift.game.session.domain.event.GameStartCancelled;
import io.github.temporalrift.game.session.domain.event.GameStartFailed;
import io.github.temporalrift.game.session.domain.event.GameStarted;
import io.github.temporalrift.game.session.domain.event.HostTransferred;
import io.github.temporalrift.game.session.domain.event.LobbyClosed;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.event.PlayerAbandoned;
import io.github.temporalrift.game.session.domain.event.PlayerDisconnected;
import io.github.temporalrift.game.session.domain.event.PlayerJoinedLobby;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.domain.event.ResolutionStarted;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.producer.DefaultServiceEventsProducer;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

/**
 * Driven adapter that fulfils the {@link SessionEventPublisher} port.
 *
 * <p>Each event's local payload is mapped to its generated wire type and published through the
 * ZenWave-generated {@link DefaultServiceEventsProducer}, which itself calls {@code
 * ApplicationEventPublisher.publishEvent} internally (transactionalOutbox=modulith) - Spring
 * Modulith's JPA event publication still intercepts it the same way it always has.
 *
 * <p>Every generated {@code XxxPayloadHeaders} class is a distinct static nested type but they all
 * extend plain {@code HashMap<String,Object>}, so {@link #headers} populates the six common fields
 * generically instead of repeating the same six lines per case.
 *
 * <p>{@code ResolutionStarted} has no apis spec entry yet (never modeled, unlike the other events
 * migrated in this PR) - {@link #publishResolutionStartedManually} hand-builds the exact {@link
 * Message} shape the generated producer would once its spec lands, so {@code
 * KafkaExternalizationConfig} routes it identically. Replace with a real {@code
 * producer.publishResolutionStarted(...)} call once the spec is published.
 */
@Component
class SessionEventPublisherAdapter implements SessionEventPublisher {

    private final DefaultServiceEventsProducer producer;
    private final SessionEventWireMapper mapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    SessionEventPublisherAdapter(
            DefaultServiceEventsProducer producer,
            SessionEventWireMapper mapper,
            ApplicationEventPublisher applicationEventPublisher) {
        this.producer = producer;
        this.mapper = mapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        switch (event.payload()) {
            case LobbyCreated e ->
                producer.publishLobbyCreated(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.LobbyCreatedPayloadHeaders(), event));
            case PlayerJoinedLobby e ->
                producer.publishPlayerJoinedLobby(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.PlayerJoinedLobbyPayloadHeaders(), event));
            case PlayerLeftLobby e ->
                producer.publishPlayerLeftLobby(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.PlayerLeftLobbyPayloadHeaders(), event));
            case LobbyClosed e ->
                producer.publishLobbyClosed(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.LobbyClosedPayloadHeaders(), event));
            case HostTransferred e ->
                producer.publishHostTransferred(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.HostTransferredPayloadHeaders(), event));
            case EraStarted e ->
                producer.publishEraStarted(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.EraStartedPayloadHeaders(), event));
            case EraEnded e ->
                producer.publishEraEnded(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.EraEndedPayloadHeaders(), event));
            case EraFailed e ->
                producer.publishEraFailed(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.EraFailedPayloadHeaders(), event));
            case FactionAssigned e ->
                producer.publishFactionAssigned(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.FactionAssignedPayloadHeaders(), event));
            case FactionsDrawn e ->
                producer.publishFactionsDrawn(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.FactionsDrawnPayloadHeaders(), event));
            case GameStartCancelled e ->
                producer.publishGameStartCancelled(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.GameStartCancelledPayloadHeaders(), event));
            case GameStartFailed e ->
                producer.publishGameStartFailed(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.GameStartFailedPayloadHeaders(), event));
            case GameStarted e ->
                producer.publishGameStarted(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.GameStartedPayloadHeaders(), event));
            case PlayerAbandoned e ->
                producer.publishPlayerAbandoned(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.PlayerAbandonedPayloadHeaders(), event));
            case PlayerDisconnected e ->
                producer.publishPlayerDisconnected(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.PlayerDisconnectedPayloadHeaders(), event));
            case WinConditionMet e ->
                producer.publishWinConditionMet(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.WinConditionMetPayloadHeaders(), event));
            case GameEndedAbnormally e ->
                producer.publishGameEndedAbnormally(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.GameEndedAbnormallyPayloadHeaders(), event));
            case GameEnded e ->
                producer.publishGameEnded(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.GameEndedPayloadHeaders(), event));
            case TimelineCollapsed e ->
                producer.publishTimelineCollapsed(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.TimelineCollapsedPayloadHeaders(), event));
            case TimelineStabilized e ->
                producer.publishTimelineStabilized(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.TimelineStabilizedPayloadHeaders(), event));
            case FactionRevealed e ->
                producer.publishFactionRevealed(
                        mapper.toWire(e),
                        headers(new DefaultServiceEventsProducer.FactionRevealedPayloadHeaders(), event));
            case EventsDrawn e ->
                producer.publishEventsDrawn(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.EventsDrawnPayloadHeaders(), event));
            case HandDealt e ->
                producer.publishHandDealt(
                        mapper.toWire(e), headers(new DefaultServiceEventsProducer.HandDealtPayloadHeaders(), event));
            case ResolutionStarted e -> publishResolutionStartedManually(event, e);
            default ->
                throw new IllegalArgumentException(
                        "Unsupported session event payload: " + event.payload().getClass());
        }
    }

    private static <H extends HashMap<String, Object>> H headers(H headers, DomainEventEnvelope event) {
        headers.put("eventId", event.eventId().toString());
        headers.put("aggregateId", event.aggregateId().toString());
        headers.put("aggregateType", event.aggregateType());
        headers.put("gameId", event.gameId().toString());
        headers.put("occurredAt", event.occurredAt());
        headers.put("version", event.version());
        return headers;
    }

    private void publishResolutionStartedManually(DomainEventEnvelope event, ResolutionStarted payload) {
        var messageHeaders = headers(new HashMap<String, Object>(), event);
        messageHeaders.put("spring.cloud.stream.sendto.destination", "Sessionpublish-resolution-started-out");
        Message<ResolutionStarted> message = MessageBuilder.createMessage(payload, new MessageHeaders(messageHeaders));
        applicationEventPublisher.publishEvent(message);
    }
}
