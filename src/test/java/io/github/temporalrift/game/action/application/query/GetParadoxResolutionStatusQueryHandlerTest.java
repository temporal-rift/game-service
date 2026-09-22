package io.github.temporalrift.game.action.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.port.in.GetParadoxResolutionStatusUseCase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotFoundException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseStatus;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.ReactiveOfferRepository;
import io.github.temporalrift.game.action.domain.reactiveoffer.ReactiveOffer;
import io.github.temporalrift.game.shared.domain.model.CardType;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetParadoxResolutionStatusQueryHandler")
class GetParadoxResolutionStatusQueryHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID CALLER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final int ERA = 2;
    static final Instant NOW = Instant.parse("2026-06-08T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ReactiveOfferRepository reactiveOfferRepository;

    @Mock
    ParadoxResolutionPhase phase;

    private GetParadoxResolutionStatusQueryHandler handler() {
        return new GetParadoxResolutionStatusQueryHandler(
                phaseRepository, playerStateRepository, reactiveOfferRepository, CLOCK);
    }

    private void stubCallerIsParticipant() {
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER))
                .willReturn(Optional.of(new PlayerState(UUID.randomUUID(), GAME_ID, CALLER)));
    }

    private void stubAllPlayers(UUID... playerIds) {
        var states = java.util.Arrays.stream(playerIds)
                .map(playerId -> new PlayerState(UUID.randomUUID(), GAME_ID, playerId))
                .toList();
        given(playerStateRepository.findAllByGameId(GAME_ID)).willReturn(states);
    }

    @Test
    @DisplayName("caller is not a participant — throws ParadoxResolutionPhaseNotFoundException without reading phase")
    void handleCallerNotParticipant() {
        // given
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER)).willReturn(Optional.empty());
        var underTest = handler();
        var query = new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER);

        // when / then
        assertThatThrownBy(() -> underTest.handle(query)).isInstanceOf(ParadoxResolutionPhaseNotFoundException.class);
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("phase missing — throws ParadoxResolutionPhaseNotFoundException")
    void handlePhaseMissing() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());
        var underTest = handler();
        var query = new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER);

        // when / then
        assertThatThrownBy(() -> underTest.handle(query)).isInstanceOf(ParadoxResolutionPhaseNotFoundException.class);
    }

    @Test
    @DisplayName("open phase — caller submitted — returns timer, aggregate progress and pending players")
    void handleOpenPhaseCallerSubmitted() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.OPEN);
        given(phase.expiresAt()).willReturn(NOW.plusSeconds(42));
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of(CALLER));
        stubAllPlayers(CALLER, OTHER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.eraNumber()).isEqualTo(ERA);
        assertThat(result.phaseOpen()).isTrue();
        assertThat(result.timerRemainingSeconds()).isEqualTo(42);
        assertThat(result.submittedCount()).isEqualTo(1);
        assertThat(result.totalPlayers()).isEqualTo(2);
        assertThat(result.pendingPlayerIds()).containsExactly(OTHER);
        assertThat(result.mySubmitted()).isTrue();
        assertThat(result.eligibleResolutionCards()).isNull();
    }

    @Test
    @DisplayName("open phase — caller not submitted — reports mySubmitted false")
    void handleOpenPhaseCallerNotSubmitted() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.OPEN);
        given(phase.expiresAt()).willReturn(NOW.plusSeconds(10));
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of());
        stubAllPlayers(CALLER, OTHER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.phaseOpen()).isTrue();
        assertThat(result.mySubmitted()).isFalse();
        assertThat(result.pendingPlayerIds()).containsExactlyInAnyOrder(CALLER, OTHER);
    }

    @Test
    @DisplayName("closed phase — reports closed with no timer and no pending list")
    void handleClosedPhase() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.CLOSED);
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of(CALLER));
        stubAllPlayers(CALLER, OTHER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.phaseOpen()).isFalse();
        assertThat(result.timerRemainingSeconds()).isNull();
        assertThat(result.pendingPlayerIds()).isNull();
        assertThat(result.mySubmitted()).isTrue();
    }

    @Test
    @DisplayName("expired open phase — reports closed with no timer and no pending list")
    void handleExpiredPhase() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.OPEN);
        given(phase.expiresAt()).willReturn(NOW.minusSeconds(1));
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of());
        stubAllPlayers(CALLER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.phaseOpen()).isFalse();
        assertThat(result.timerRemainingSeconds()).isNull();
        assertThat(result.pendingPlayerIds()).isNull();
        assertThat(result.totalPlayers()).isEqualTo(1);
    }

    @Test
    @DisplayName("submissions beyond known players — totals to the larger count")
    void handleSubmissionsBeyondKnownPlayers() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.CLOSED);
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of(CALLER, OTHER, UUID.randomUUID()));
        stubAllPlayers(CALLER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.submittedCount()).isEqualTo(3);
        assertThat(result.totalPlayers()).isEqualTo(3);
    }

    @Test
    @DisplayName("real aggregate — open phase accepts submission window from expiresAt")
    void handleRealAggregateOpenPhase() {
        // given
        stubCallerIsParticipant();
        var realPhase = new ParadoxResolutionPhase(UUID.randomUUID(), GAME_ID, ERA, NOW.plusSeconds(30));
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(realPhase));
        stubAllPlayers(CALLER, OTHER);

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.phaseOpen()).isTrue();
        assertThat(result.timerRemainingSeconds()).isEqualTo(30);
        assertThat(result.mySubmitted()).isFalse();
        assertThat(result.pendingPlayerIds()).containsExactlyInAnyOrder(CALLER, OTHER);
    }

    @Test
    @DisplayName("open phase — returns only the caller's eligible retained and offered cards")
    void handleOpenPhaseReturnsCallerEligibleCards() {
        // given
        var affectedEventId = UUID.randomUUID();
        var retainedCardId = UUID.randomUUID();
        var caller = new PlayerState(UUID.randomUUID(), GAME_ID, CALLER);
        caller.dealCard(new PlayerState.CardInstance(retainedCardId, CardType.PUSH), 5);
        caller.dealCard(new PlayerState.CardInstance(UUID.randomUUID(), CardType.COLLIDE), 5);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER)).willReturn(Optional.of(caller));
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.OPEN);
        given(phase.expiresAt()).willReturn(NOW.plusSeconds(10));
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of());
        given(phase.affectedEventIds()).willReturn(Set.of(affectedEventId));
        stubAllPlayers(CALLER, OTHER);
        var offer = new ReactiveOffer(UUID.randomUUID(), GAME_ID, ERA, CALLER, UUID.randomUUID(), UUID.randomUUID());
        given(reactiveOfferRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, CALLER))
                .willReturn(Optional.of(offer));

        // when
        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        // then
        assertThat(result.affectedEventIds()).containsExactly(affectedEventId);
        assertThat(result.eligibleResolutionCards())
                .extracting(GetParadoxResolutionStatusUseCase.EligibleCard::cardInstanceId)
                .containsExactlyInAnyOrder(
                        retainedCardId, offer.stabilizeCardInstanceId(), offer.detonateCardInstanceId());
        assertThat(result.eligibleResolutionCards())
                .extracting(GetParadoxResolutionStatusUseCase.EligibleCard::cardType)
                .containsExactlyInAnyOrder(CardType.PUSH, CardType.STABILIZE, CardType.DETONATE);
    }

    @Test
    @DisplayName("open phase — excludes an expired caller offer from eligible cards")
    void handleOpenPhaseExcludesExpiredCallerOffer() {
        var caller = new PlayerState(UUID.randomUUID(), GAME_ID, CALLER);
        var expiredOffer =
                new ReactiveOffer(UUID.randomUUID(), GAME_ID, ERA, CALLER, UUID.randomUUID(), UUID.randomUUID());
        expiredOffer.expire();
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER)).willReturn(Optional.of(caller));
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(phase.status()).willReturn(ParadoxResolutionPhaseStatus.OPEN);
        given(phase.expiresAt()).willReturn(NOW.plusSeconds(10));
        given(phase.eraNumber()).willReturn(ERA);
        given(phase.submittedPlayerIds()).willReturn(Set.of());
        given(phase.affectedEventIds()).willReturn(Set.of(UUID.randomUUID()));
        stubAllPlayers(CALLER, OTHER);
        given(reactiveOfferRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, CALLER))
                .willReturn(Optional.of(expiredOffer));

        var result = handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER));

        assertThat(result.eligibleResolutionCards()).isEqualTo(List.of());
        then(reactiveOfferRepository).should().findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, CALLER);
    }
}
