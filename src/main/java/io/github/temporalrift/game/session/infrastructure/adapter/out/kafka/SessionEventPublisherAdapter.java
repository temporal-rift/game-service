package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.session.FactionAssigned;
import io.github.temporalrift.game.session.GameEnded;
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
 * ZenWave-generated {@link DefaultServiceEventsProducer}, which itself calls {@link
 * ApplicationEventPublisher#publishEvent} internally (transactionalOutbox=modulith) - Spring
 * Modulith's JPA event publication still intercepts it the same way it always has.
 *
 * <p>EventsDrawn/HandDealt aren't wired here yet - their apis spec entry is pending publication (apis
 * PR #4, temporal-rift/apis) - and still publish a raw {@link EventEnvelope} directly, which {@code
 * KafkaExternalizationConfig} still recognizes for exactly this reason.
 *
 * <p>Every generated {@code XxxPayloadHeaders} class carries the same six fields but is a distinct
 * static nested type with no shared supertype beyond {@code HashMap<String,Object>}, so each
 * publish call constructs its own - there's no way to share one instance across the differently
 * typed method signatures.
 */
@Component
class SessionEventPublisherAdapter implements SessionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DefaultServiceEventsProducer producer;
    private final SessionEventWireMapper mapper;

    SessionEventPublisherAdapter(
            ApplicationEventPublisher applicationEventPublisher,
            DefaultServiceEventsProducer producer,
            SessionEventWireMapper mapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.producer = producer;
        this.mapper = mapper;
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        switch (event.payload()) {
            case LobbyCreated e ->
                producer.publishLobbyCreated(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.LobbyCreatedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case PlayerJoinedLobby e ->
                producer.publishPlayerJoinedLobby(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.PlayerJoinedLobbyPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case PlayerLeftLobby e ->
                producer.publishPlayerLeftLobby(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.PlayerLeftLobbyPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case LobbyClosed e ->
                producer.publishLobbyClosed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.LobbyClosedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case HostTransferred e ->
                producer.publishHostTransferred(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.HostTransferredPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case EraStarted e ->
                producer.publishEraStarted(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.EraStartedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case EraEnded e ->
                producer.publishEraEnded(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.EraEndedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case EraFailed e ->
                producer.publishEraFailed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.EraFailedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case FactionAssigned e ->
                producer.publishFactionAssigned(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.FactionAssignedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case FactionsDrawn e ->
                producer.publishFactionsDrawn(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.FactionsDrawnPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case GameStartCancelled e ->
                producer.publishGameStartCancelled(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.GameStartCancelledPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case GameStartFailed e ->
                producer.publishGameStartFailed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.GameStartFailedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case GameStarted e ->
                producer.publishGameStarted(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.GameStartedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case PlayerAbandoned e ->
                producer.publishPlayerAbandoned(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.PlayerAbandonedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case PlayerDisconnected e ->
                producer.publishPlayerDisconnected(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.PlayerDisconnectedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case WinConditionMet e ->
                producer.publishWinConditionMet(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.WinConditionMetPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case GameEndedAbnormally e ->
                producer.publishGameEndedAbnormally(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.GameEndedAbnormallyPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case GameEnded e ->
                producer.publishGameEnded(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.GameEndedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case TimelineCollapsed e ->
                producer.publishTimelineCollapsed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.TimelineCollapsedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case TimelineStabilized e ->
                producer.publishTimelineStabilized(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.TimelineStabilizedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            case FactionRevealed e ->
                producer.publishFactionRevealed(
                        mapper.toWire(e),
                        new DefaultServiceEventsProducer.FactionRevealedPayloadHeaders()
                                .set("eventId", event.eventId().toString())
                                .set("aggregateId", event.aggregateId().toString())
                                .set("aggregateType", event.aggregateType())
                                .set("gameId", event.gameId().toString())
                                .set("occurredAt", event.occurredAt())
                                .set("version", event.version()));
            default -> applicationEventPublisher.publishEvent(toLegacyEnvelope(event));
        }
    }

    private EventEnvelope toLegacyEnvelope(DomainEventEnvelope event) {
        return new EventEnvelope(
                event.eventId(),
                deriveEventType(event.payload()),
                event.aggregateId(),
                event.aggregateType(),
                event.gameId(),
                event.occurredAt(),
                event.version(),
                event.payload());
    }

    private static String deriveEventType(Object payload) {
        var pkg = payload.getClass().getPackageName();
        return pkg.substring(pkg.lastIndexOf('.') + 1) + "."
                + payload.getClass().getSimpleName();
    }
}
