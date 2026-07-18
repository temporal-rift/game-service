package io.github.temporalrift.game.action.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundSagaRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.CardType;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetRoundStatusQueryHandler")
class GetRoundStatusQueryHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID CALLER = UUID.randomUUID();
    static final int ERA = 2;
    static final int ROUND = 1;
    static final Instant NOW = Instant.parse("2026-06-08T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    ActionRoundSagaRepository actionRoundSagaRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionRound round;

    @BeforeEach
    void stubCallerIsParticipant() {
        // Lenient: the not-a-participant test overrides this stub with Optional.empty().
        lenient()
                .when(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER))
                .thenReturn(Optional.of(new PlayerState(UUID.randomUUID(), GAME_ID, CALLER)));
    }

    @Test
    @DisplayName("handle — caller is not a participant — throws RoundNotFoundException without reading the round")
    void handleCallerNotParticipant() {
        // given
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, CALLER)).willReturn(Optional.empty());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);
        var query = new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER);

        // when / then
        assertThatExceptionOfType(RoundNotFoundException.class).isThrownBy(() -> handler.handle(query));
        then(actionRoundRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — open round with saga state — returns public status and remaining timer")
    void handleOpenRoundWithSagaState() {
        // given
        var pendingPlayer = UUID.randomUUID();
        var submittedAction = submittedAction();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState(NOW.plusSeconds(42))));
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.OPEN);
        given(round.submittedActions()).willReturn(List.of(submittedAction));
        var sagaState = sagaState(NOW.plusSeconds(42), List.of(pendingPlayer));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState));
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.eraNumber()).isEqualTo(ERA);
        assertThat(result.roundNumber()).isEqualTo(ROUND);
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.timerRemainingSeconds()).isEqualTo(42);
        assertThat(result.submittedCount()).isEqualTo(1);
        assertThat(result.totalPlayers()).isEqualTo(2);
        assertThat(result.pendingPlayerIds()).containsExactly(pendingPlayer);
    }

    @Test
    @DisplayName("handle — closed timeout round — reports saga pending players after aggregate clears pending")
    void handleClosedTimeoutRoundReportsSagaPendingPlayers() {
        // given
        var skippedPlayer = UUID.randomUUID();
        var submittedAction = submittedAction();
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState(NOW.minusSeconds(1), List.of(skippedPlayer))));
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.CLOSED);
        given(round.submittedActions()).willReturn(List.of(submittedAction));
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.submittedCount()).isEqualTo(1);
        assertThat(result.totalPlayers()).isEqualTo(2);
        assertThat(result.pendingPlayerIds()).containsExactly(skippedPlayer);
    }

    @Test
    @DisplayName("handle — closed round — maps status to CLOSED")
    void handleClosedRound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState(NOW.plusSeconds(5))));
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.CLOSED);
        given(round.submittedActions()).willReturn(List.of());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.status()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("handle — expired timer — clamps remaining seconds to zero")
    void handleExpiredTimer() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState(NOW.minusSeconds(1))));
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.OPEN);
        given(round.submittedActions()).willReturn(List.of());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.timerRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("handle — very long timer — clamps remaining seconds to max int")
    void handleVeryLongTimerClampsToMaxInt() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(sagaState(NOW.plusSeconds((long) Integer.MAX_VALUE + 1), List.of())));
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.OPEN);
        given(round.submittedActions()).willReturn(List.of());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.timerRemainingSeconds()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("handle — saga state missing — returns timer zero")
    void handleSagaStateMissing() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(actionRoundSagaRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.empty());
        given(round.eraNumber()).willReturn(ERA);
        given(round.roundNumber()).willReturn(ROUND);
        given(round.status()).willReturn(RoundStatus.CLOSING);
        given(round.pendingPlayerIds()).willReturn(List.of());
        given(round.submittedActions()).willReturn(List.of());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);

        // when
        var result = handler.handle(new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER));

        // then
        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.timerRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("handle — round missing — throws RoundNotFoundException")
    void handleRoundMissing() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.empty());
        var handler = new GetRoundStatusQueryHandler(
                actionRoundRepository, actionRoundSagaRepository, playerStateRepository, CLOCK);
        var query = new GetRoundStatusUseCase.Query(GAME_ID, ERA, ROUND, CALLER);

        // when / then
        assertThatExceptionOfType(RoundNotFoundException.class).isThrownBy(() -> handler.handle(query));
    }

    private static ActionRoundSagaState sagaState(Instant timerExpiresAt) {
        return sagaState(timerExpiresAt, List.of(UUID.randomUUID()));
    }

    private static ActionRoundSagaState sagaState(Instant timerExpiresAt, List<UUID> pendingPlayerIds) {
        return new ActionRoundSagaState(
                UUID.randomUUID(),
                GAME_ID,
                ERA,
                ROUND,
                ActionRoundSagaStatus.WAITING,
                pendingPlayerIds,
                timerExpiresAt);
    }

    private static SubmittedAction submittedAction() {
        return new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), CardType.PUSH, UUID.randomUUID(), null, UUID.randomUUID());
    }
}
