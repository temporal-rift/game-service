package io.github.temporalrift.game.action.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    ParadoxResolutionPhase phase;

    private GetParadoxResolutionStatusQueryHandler handler() {
        return new GetParadoxResolutionStatusQueryHandler(phaseRepository, playerStateRepository, CLOCK);
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

        // when / then
        assertThatExceptionOfType(ParadoxResolutionPhaseNotFoundException.class)
                .isThrownBy(() -> handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER)));
        then(phaseRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("phase missing — throws ParadoxResolutionPhaseNotFoundException")
    void handlePhaseMissing() {
        // given
        stubCallerIsParticipant();
        given(phaseRepository.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(Optional.empty());

        // when / then
        assertThatExceptionOfType(ParadoxResolutionPhaseNotFoundException.class)
                .isThrownBy(() -> handler().handle(new GetParadoxResolutionStatusUseCase.Query(GAME_ID, ERA, CALLER)));
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
}
