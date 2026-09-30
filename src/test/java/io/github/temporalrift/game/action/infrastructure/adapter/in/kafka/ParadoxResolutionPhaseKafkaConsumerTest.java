package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.AdjustedBandsPublishedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxResolutionPhaseStartedPayload;
import io.github.temporalrift.game.action.application.ParadoxResolutionCardsOffering;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardsOffered.EligibleCard;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOfferStatus;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class ParadoxResolutionPhaseKafkaConsumerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PHASE_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T12:00:00Z");

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ReactiveOfferRepository reactiveOfferRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    ParadoxResolutionPhaseKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        var cardsOffering = new ParadoxResolutionCardsOffering(
                reactiveOfferRepository, actionEventPublisher, Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));
        consumer = new ParadoxResolutionPhaseKafkaConsumer(
                processedEventRepository,
                phaseRepository,
                playerStateRepository,
                reactiveOfferRepository,
                cardsOffering,
                JSON_MAPPER);
    }

    @Test
    void phaseStartedCreatesDurablePhaseWithAdvertisedExpiry() {
        var affectedEventId = UUID.randomUUID();
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of(affectedEventId)));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());

        consumer.handle(message);

        var captor = ArgumentCaptor.forClass(ParadoxResolutionPhase.class);
        then(phaseRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(eventIdOf(message));
        assertThat(captor.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(captor.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(captor.getValue().expiresAt()).isEqualTo(OCCURRED_AT.plusSeconds(30));
        assertThat(captor.getValue().affectedEventIds()).containsExactly(affectedEventId);
    }

    @Test
    void phaseStartedDealsOneStabilizeAndOneDetonatePerPlayer() {
        var message = message(
                "ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(UUID.randomUUID()), List.of(), 30));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());
        var playerOne = UUID.randomUUID();
        var playerTwo = UUID.randomUUID();
        given(playerStateRepository.findAllByGameId(GAME_ID))
                .willReturn(List.of(
                        new PlayerState(UUID.randomUUID(), GAME_ID, playerOne),
                        new PlayerState(UUID.randomUUID(), GAME_ID, playerTwo)));

        consumer.handle(message);

        var offerCaptor = ArgumentCaptor.forClass(ReactiveOffer.class);
        then(reactiveOfferRepository).should(times(2)).createIfAbsent(offerCaptor.capture());
        assertThat(offerCaptor.getAllValues()).hasSize(2);
        assertThat(offerCaptor.getAllValues().stream().map(ReactiveOffer::playerId))
                .containsExactlyInAnyOrder(playerOne, playerTwo);
        offerCaptor.getAllValues().forEach(offer -> {
            assertThat(offer.eraNumber()).isEqualTo(ERA);
            assertThat(offer.stabilizeCardInstanceId()).isNotEqualTo(offer.detonateCardInstanceId());
            assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.OFFERED);
        });
    }

    @Test
    void phaseStartedPublishesEachParticipantsEligibleResolutionCardsOnce() {
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of(UUID.randomUUID())));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());
        var push = new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH, CardGrade.II);
        var scan = new PlayerState.CardInstance(UUID.randomUUID(), CardType.SCAN, CardGrade.I);
        var holder = participant(UUID.randomUUID(), List.of(scan, push));
        var withoutEligibleHand = participant(UUID.randomUUID(), List.of(scan));
        given(playerStateRepository.findAllByGameId(GAME_ID)).willReturn(List.of(holder, withoutEligibleHand));
        given(reactiveOfferRepository.createIfAbsent(any())).willReturn(true);

        consumer.handle(message);

        var offers = ArgumentCaptor.forClass(ReactiveOffer.class);
        then(reactiveOfferRepository).should(times(2)).createIfAbsent(offers.capture());
        var holderOffer = offers.getAllValues().getFirst();
        var otherOffer = offers.getAllValues().getLast();
        var published = publishedPayloads();
        assertThat(published)
                .containsExactly(
                        new ParadoxResolutionCardsOffered(
                                GAME_ID,
                                ERA,
                                holder.playerId(),
                                List.of(
                                        new EligibleCard(push.cardInstanceId(), CardType.PUSH, CardGrade.II),
                                        new EligibleCard(
                                                holderOffer.stabilizeCardInstanceId(), CardType.STABILIZE, CardGrade.I),
                                        new EligibleCard(
                                                holderOffer.detonateCardInstanceId(), CardType.DETONATE, CardGrade.I))),
                        new ParadoxResolutionCardsOffered(
                                GAME_ID,
                                ERA,
                                withoutEligibleHand.playerId(),
                                List.of(
                                        new EligibleCard(
                                                otherOffer.stabilizeCardInstanceId(), CardType.STABILIZE, CardGrade.I),
                                        new EligibleCard(
                                                otherOffer.detonateCardInstanceId(), CardType.DETONATE, CardGrade.I))));
    }

    @Test
    void phaseStartedPublishesTheFactOnThePhaseAggregate() {
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of()));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());
        given(playerStateRepository.findAllByGameId(GAME_ID))
                .willReturn(List.of(participant(UUID.randomUUID(), List.of())));
        given(reactiveOfferRepository.createIfAbsent(any())).willReturn(true);

        consumer.handle(message);

        then(actionEventPublisher)
                .should()
                .publish(argThat(envelope -> envelope.aggregateId().equals(eventIdOf(message))
                        && envelope.aggregateType().equals(ParadoxResolutionPhase.AGGREGATE_TYPE)
                        && envelope.gameId().equals(GAME_ID)
                        && envelope.occurredAt().equals(OCCURRED_AT)));
    }

    @Test
    void phaseStartedTakesTheRosterLockBeforeReadingThePhaseOrItsParticipants() {
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of()));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());

        consumer.handle(message);

        var inOrder = inOrder(phaseRepository, playerStateRepository);
        inOrder.verify(phaseRepository).lockParticipantRoster(GAME_ID, ERA);
        inOrder.verify(phaseRepository).findByGameIdAndEraNumber(GAME_ID, ERA);
        inOrder.verify(playerStateRepository).findAllByGameId(GAME_ID);
    }

    @Test
    void phaseStartedDoesNotRepublishForAParticipantAlreadyDealt() {
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of()));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());
        given(playerStateRepository.findAllByGameId(GAME_ID))
                .willReturn(List.of(participant(UUID.randomUUID(), List.of())));
        given(reactiveOfferRepository.createIfAbsent(any())).willReturn(false);

        consumer.handle(message);

        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void duplicatePhaseStartDoesNotDealAgain() {
        var message = message(
                "ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(UUID.randomUUID()), List.of(), 30));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(
                        Optional.of(new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30))));

        consumer.handle(message);

        then(playerStateRepository).shouldHaveNoInteractions();
        then(reactiveOfferRepository).shouldHaveNoInteractions();
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void redeliveredPhaseStartRecoversMissingAffectedTargetsWithoutDealingAgain() {
        var phase = new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30));
        var affectedEventId = UUID.randomUUID();
        var message = message("ParadoxResolutionPhaseStarted", 1, phaseStarted(GAME_ID, List.of(affectedEventId)));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));

        consumer.handle(message);

        assertThat(phase.affectedEventIds()).containsExactly(affectedEventId);
        then(phaseRepository).should().save(phase);
        then(reactiveOfferRepository).shouldHaveNoInteractions();
    }

    @Test
    void eraCompletionExpiresUnusedOffers() {
        var phase = new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30));
        var message = message("EraResolutionCompleted", 1, eraCompleted(GAME_ID));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        var offer = new ReactiveOffer(
                UUID.randomUUID(), GAME_ID, ERA, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(reactiveOfferRepository.findAllByGameIdAndEraNumberWithLock(GAME_ID, ERA))
                .willReturn(List.of(offer));

        consumer.handle(message);

        assertThat(offer.status()).isEqualTo(ReactiveOfferStatus.EXPIRED);
        then(reactiveOfferRepository).should().save(offer);
    }

    @Test
    void eraCompletionClosesExistingPhase() {
        var phase = new ParadoxResolutionPhase(PHASE_ID, GAME_ID, ERA, OCCURRED_AT.plusSeconds(30));
        var message = message("EraResolutionCompleted", 1, eraCompleted(GAME_ID));
        givenClaim(message, true);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));

        consumer.handle(message);

        assertThat(phase.status()).isEqualTo(ParadoxResolutionPhaseStatus.CLOSED);
        then(phaseRepository).should().save(phase);
    }

    @Test
    void duplicateEventIsIgnoredBeforeDeserialization() {
        // A body that cannot deserialize proves the duplicate claim short-circuits before the payload is read.
        var message = message("ParadoxResolutionPhaseStarted", 1, "not-json");
        givenClaim(message, false);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void phaseStartedWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var message = message(
                "ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(UUID.randomUUID(), ERA, List.of(), List.of(), 30));
        givenClaim(message, true);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void eraCompletionWithMismatchedPayloadGameIsIgnoredBeforeMutation() {
        var message = message("EraResolutionCompleted", 1, eraCompleted(UUID.randomUUID()));
        givenClaim(message, true);

        consumer.handle(message);

        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unsupportedVersionIsSkippedBeforeClaim() {
        consumer.handle(message(
                "ParadoxResolutionPhaseStarted",
                2,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(), List.of(), 30)));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void namespacedEventTypeIsSkippedBeforeClaim() {
        consumer.handle(message(
                "timeline.ParadoxResolutionPhaseStarted",
                1,
                new ParadoxResolutionPhaseStartedPayload(GAME_ID, ERA, List.of(), List.of(), 30)));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void renamedBandCorrectionIsSkippedBeforeClaim() {
        consumer.handle(message(
                io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract
                        .ADJUSTED_BANDS_PUBLISHED_EVENT_TYPE,
                1,
                new AdjustedBandsPublishedPayload(GAME_ID, ERA, List.of())));

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    void unrelatedAndMalformedEventsAreIgnored() {
        consumer.handle(message("OutcomeApplied", 1, "{}"));
        consumer.handle(MessageBuilder.withPayload((Object) "{}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", "ParadoxResolutionPhaseStarted")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("version", "1")
                .build());

        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(phaseRepository).shouldHaveNoInteractions();
    }

    private List<ActionEventPayload> publishedPayloads() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DomainEventEnvelope<ActionEventPayload>> envelopes =
                ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(actionEventPublisher).should(atLeastOnce()).publish(envelopes.capture());
        return envelopes.getAllValues().stream()
                .map(DomainEventEnvelope::payload)
                .toList();
    }

    private static PlayerState participant(UUID playerId, List<PlayerState.CardInstance> hand) {
        return PlayerState.reconstitute(
                UUID.randomUUID(),
                GAME_ID,
                playerId,
                Faction.ERASERS,
                new PlayerState.PersistedState(hand, Set.of(), false, false));
    }

    private void givenClaim(Message<Object> message, boolean claimed) {
        given(processedEventRepository.tryMarkProcessed(eventIdOf(message), "action.paradox-resolution-phase"))
                .willReturn(claimed);
    }

    private static EraResolutionCompletedPayload eraCompleted(UUID gameId) {
        return new EraResolutionCompletedPayload(gameId, ERA, List.of());
    }

    private static ParadoxResolutionPhaseStartedPayload phaseStarted(UUID gameId, List<UUID> affectedEventIds) {
        return new ParadoxResolutionPhaseStartedPayload(gameId, ERA, List.of(UUID.randomUUID()), affectedEventIds, 30);
    }

    private static UUID eventIdOf(Message<Object> message) {
        return UUID.fromString((String) message.getHeaders().get("eventId"));
    }

    /** The published wire shape: envelope metadata in the headers, only the typed payload in the body. */
    private static Message<Object> message(String eventType, int version, Object payload) {
        var body = payload instanceof String text ? text : JSON_MAPPER.writeValueAsString(payload);
        return MessageBuilder.withPayload((Object) body.getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", PHASE_ID.toString())
                .setHeader("aggregateType", "ParadoxResolutionPhase")
                .setHeader("gameId", GAME_ID.toString())
                .setHeader("occurredAt", OCCURRED_AT.toString())
                .setHeader("version", String.valueOf(version))
                .build();
    }
}
