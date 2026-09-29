package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.port.in.PassActionRoundUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.event.PlayerPassed;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.model.CardType;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassActionRoundCommandHandler")
class PassActionRoundCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID OTHER_PLAYER_ID = UUID.randomUUID();
    static final int ERA = 1;
    static final int ROUND = 3;

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    PlayerState playerState;

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    PassActionRoundCommandHandler handler;

    @Test
    @DisplayName("handle — pending player passes — saves the round and publishes only the in-process pass")
    void handlePassesPrivately() {
        // given
        var round = openRound();
        givenRound(round);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        // when
        var result = handler.handle(command());

        // then
        assertThat(result).isEqualTo(new PassActionRoundUseCase.Result(GAME_ID, ERA, ROUND, PLAYER_ID, false));
        assertThat(round.passedPlayerIds()).containsExactly(PLAYER_ID);
        then(actionRoundRepository).should().save(round);
        then(actionEventPublisher).should().publishInternally(new PlayerPassed(GAME_ID, ERA, ROUND, PLAYER_ID));
        then(actionEventPublisher).should(never()).publish(any());
        then(playerStateRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("handle — last pending player passes — reports the round ready to close early")
    void handleLastPassReportsRoundClosed() {
        // given
        var round = openRound();
        round.submit(new SubmittedAction.CardAction(
                OTHER_PLAYER_ID, UUID.randomUUID(), CardType.PUSH, UUID.randomUUID(), null, null));
        round.pullEvents();
        givenRound(round);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        // when
        var result = handler.handle(command());

        // then
        assertThat(result.roundClosed()).isTrue();
    }

    @Test
    @DisplayName("handle — jammed player — passes like anyone else")
    void handleJammedPlayerPasses() {
        // given
        var round = openRound();
        givenRound(round);
        lenient().when(playerState.isJammed()).thenReturn(true);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        // when
        handler.handle(command());

        // then
        assertThat(round.passedPlayerIds()).containsExactly(PLAYER_ID);
    }

    @Test
    @DisplayName("handle — player already passed — rejects the second pass and saves nothing")
    void handleRejectsSecondPass() {
        // given
        var round = openRound();
        round.pass(PLAYER_ID);
        givenRound(round);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        var command = command();

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> handler.handle(command));
        then(actionRoundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("handle — round closed — rejects the pass")
    void handleRejectsClosedRound() {
        // given
        var round = openRound();
        round.close("TIMER_EXPIRED");
        givenRound(round);
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        var command = command();

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — caller is not a participant — not found, never a pending-slot conflict")
    void handleRejectsNonParticipant() {
        // given
        givenRound(openRound());
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.empty());

        var command = command();

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
        then(actionRoundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("handle — unknown round — not found")
    void handleRejectsUnknownRound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.empty());

        var command = command();

        // when / then
        assertThatExceptionOfType(RoundNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    private static ActionRound openRound() {
        var round = new ActionRound(
                UUID.randomUUID(), new ActionRoundConfig(GAME_ID, ERA, ROUND, 60), List.of(PLAYER_ID, OTHER_PLAYER_ID));
        round.pullEvents();
        return round;
    }

    private void givenRound(ActionRound round) {
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
    }

    private static PassActionRoundUseCase.Command command() {
        return new PassActionRoundUseCase.Command(GAME_ID, ERA, ROUND, PLAYER_ID);
    }
}
