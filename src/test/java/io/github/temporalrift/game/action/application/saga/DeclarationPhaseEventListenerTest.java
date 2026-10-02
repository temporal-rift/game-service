package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import io.github.temporalrift.game.action.application.ActivistMomentumEligibility;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.event.ActionEventPayload;
import io.github.temporalrift.game.action.domain.event.DeclarationOptionsOffered;
import io.github.temporalrift.game.action.domain.event.DeclarationWindowOpened;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.HandSelectionCompleted;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeclarationPhaseEventListener")
class DeclarationPhaseEventListenerTest {

    static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 2;

    @Mock
    DeclarationPhaseRepository repository;

    @Mock
    DeclarationPhaseTimerScheduler timerScheduler;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActivistEraStateRepository activistEraStateRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    GameRulesPort gameRules;

    DeclarationPhaseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DeclarationPhaseEventListener(
                repository,
                timerScheduler,
                playerStateRepository,
                new ActivistMomentumEligibility(activistEraStateRepository),
                actionEventPublisher,
                gameRules,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("first opening — persists the phase, publishes its expiry once, and schedules expiry")
    void firstOpeningPublishesPersistedExpiry() {
        // given
        var playerIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(gameRules.declarationTimerSeconds(3)).willReturn(30);
        given(repository.createIfAbsent(any(DeclarationPhase.class))).willReturn(true);

        // when
        listener.onHandSelectionCompleted(new HandSelectionCompleted(GAME_ID, ERA, playerIds));

        // then
        var phase = ArgumentCaptor.forClass(DeclarationPhase.class);
        then(repository).should().createIfAbsent(phase.capture());
        assertThat(phase.getValue().gameId()).isEqualTo(GAME_ID);
        assertThat(phase.getValue().eraNumber()).isEqualTo(ERA);
        assertThat(phase.getValue().expiresAt()).isEqualTo(Instant.parse("2030-01-01T10:00:30Z"));
        var published = publishedEnvelopes(1);
        assertThat(published.getFirst().payload())
                .isEqualTo(new DeclarationWindowOpened(GAME_ID, ERA, Instant.parse("2030-01-01T10:00:30Z")));
        assertThat(published.getFirst().aggregateId())
                .isEqualTo(phase.getValue().id());
        assertThat(published.getFirst().aggregateType()).isEqualTo(DeclarationPhase.AGGREGATE_TYPE);
        then(timerScheduler)
                .should()
                .scheduleAfterCommit(phase.getValue().id(), phase.getValue().expiresAt());
    }

    @Test
    @DisplayName("first opening — offers Rally only to the Activist, never to the non-Activist")
    void firstOpeningOffersRallyOnlyToTheActivist() {
        // given
        var activist = participant(Faction.ACTIVISTS);
        var prophet = participant(Faction.PROPHETS);
        givenOpening(activist, prophet);

        // when
        listener.onHandSelectionCompleted(
                new HandSelectionCompleted(GAME_ID, ERA, List.of(activist.playerId(), prophet.playerId())));

        // then
        var payloads =
                publishedEnvelopes(2).stream().map(DomainEventEnvelope::payload).toList();
        assertThat(payloads.getFirst()).isInstanceOf(DeclarationWindowOpened.class);
        assertThat(payloads.get(1))
                .isEqualTo(new DeclarationOptionsOffered(
                        GAME_ID, ERA, activist.playerId(), List.of(ActivistDeclarationMode.RALLY)));
    }

    @Test
    @DisplayName("first opening — after a successful prior-era declaration, offers Rally and Momentum")
    void firstOpeningOffersMomentumAfterPriorSuccess() {
        // given
        var activist = participant(Faction.ACTIVISTS);
        givenOpening(activist);
        var previousEra = new ActivistEraState(UUID.randomUUID(), GAME_ID, ERA - 1, activist.playerId(), false);
        previousEra.declare(ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID());
        previousEra.recordResolution(true);
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(
                        GAME_ID, ERA - 1, activist.playerId()))
                .willReturn(Optional.of(previousEra));

        // when
        listener.onHandSelectionCompleted(new HandSelectionCompleted(GAME_ID, ERA, List.of(activist.playerId())));

        // then
        assertThat(publishedEnvelopes(2).get(1).payload())
                .isEqualTo(new DeclarationOptionsOffered(
                        GAME_ID,
                        ERA,
                        activist.playerId(),
                        List.of(ActivistDeclarationMode.RALLY, ActivistDeclarationMode.MOMENTUM)));
    }

    @Test
    @DisplayName("first opening — a jammed Activist or an unprojected participant receives no offer")
    void firstOpeningSkipsIneligibleParticipants() {
        // given
        var jammed = participant(Faction.ACTIVISTS);
        jammed.applyJam();
        var unprojected = UUID.randomUUID();
        givenOpening(jammed);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, unprojected))
                .willReturn(Optional.empty());

        // when
        listener.onHandSelectionCompleted(
                new HandSelectionCompleted(GAME_ID, ERA, List.of(jammed.playerId(), unprojected)));

        // then
        assertThat(publishedEnvelopes(1).getFirst().payload()).isInstanceOf(DeclarationWindowOpened.class);
    }

    @Test
    @DisplayName("first opening — publishes the opening before any offer, both before expiry is scheduled")
    void firstOpeningOrdersOpeningBeforeOffers() {
        // given
        var activist = participant(Faction.ACTIVISTS);
        givenOpening(activist);

        // when
        listener.onHandSelectionCompleted(new HandSelectionCompleted(GAME_ID, ERA, List.of(activist.playerId())));

        // then
        var order = inOrder(repository, actionEventPublisher, timerScheduler);
        order.verify(repository).createIfAbsent(any());
        order.verify(actionEventPublisher)
                .publish(argThat(envelope -> envelope.payload() instanceof DeclarationWindowOpened));
        order.verify(actionEventPublisher)
                .publish(argThat(envelope -> envelope.payload() instanceof DeclarationOptionsOffered));
        order.verify(timerScheduler).scheduleAfterCommit(any(), any());
    }

    @Test
    @DisplayName("duplicate, stale, or post-close trigger — publishes nothing and does not reschedule")
    void existingPhasePublishesNothing() {
        // given
        given(gameRules.declarationTimerSeconds(2)).willReturn(30);
        given(repository.createIfAbsent(any(DeclarationPhase.class))).willReturn(false);

        // when
        listener.onHandSelectionCompleted(
                new HandSelectionCompleted(GAME_ID, ERA, List.of(UUID.randomUUID(), UUID.randomUUID())));

        // then
        then(actionEventPublisher).shouldHaveNoInteractions();
        then(playerStateRepository).shouldHaveNoInteractions();
        then(timerScheduler).should(never()).scheduleAfterCommit(any(), any());
    }

    private void givenOpening(PlayerState... participants) {
        given(gameRules.declarationTimerSeconds(anyInt())).willReturn(30);
        given(repository.createIfAbsent(any(DeclarationPhase.class))).willReturn(true);
        for (var participant : participants) {
            given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, participant.playerId()))
                    .willReturn(Optional.of(participant));
        }
    }

    private static PlayerState participant(Faction faction) {
        var state = new PlayerState(UUID.randomUUID(), GAME_ID, UUID.randomUUID());
        state.assignFaction(faction);
        return state;
    }

    @SuppressWarnings("unchecked")
    private List<DomainEventEnvelope<ActionEventPayload>> publishedEnvelopes(int expected) {
        ArgumentCaptor<DomainEventEnvelope<ActionEventPayload>> captor =
                ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(actionEventPublisher).should(times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }
}
