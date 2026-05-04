package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaySpecialActionCommandHandler")
class PlaySpecialActionCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int ERA = 1;
    static final int ROUND = 1;

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionRound round;

    @Mock
    PlayerState playerState;

    @InjectMocks
    PlaySpecialActionCommandHandler handler;

    @Test
    @DisplayName("handle — happy path — saves round, does not save player state, returns result")
    void handleHappyPath() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(false);

        // when
        var result = handler.handle(command);

        // then
        then(actionRoundRepository).should().save(round);
        then(playerStateRepository).should(never()).save(any());
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.eraNumber()).isEqualTo(ERA);
        assertThat(result.roundNumber()).isEqualTo(ROUND);
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.roundClosed()).isFalse();
    }

    @Test
    @DisplayName("handle — all players submitted — calls close on round and returns roundClosed true")
    void handleAllSubmittedClosesRound() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(true);

        // when
        var result = handler.handle(command);

        // then
        then(round).should().close("ALL_SUBMITTED");
        assertThat(result.roundClosed()).isTrue();
    }

    @Test
    @DisplayName("handle — round not found — throws RoundNotFoundException")
    void handleRoundNotFound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.empty());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(RoundNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player state not found — throws PlayerStateNotFoundException")
    void handlePlayerStateNotFound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.empty());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player is jammed — propagates JammedPlayerException from aggregate")
    void handleJammedPlayer() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.ERASERS, SpecialAction.ANNIHILATE, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(true);
        willThrow(new JammedPlayerException(PLAYER_ID))
                .given(round)
                .submitSpecial(any(), any(), any(), any(), any(), any(), eq(true));

        // when / then
        assertThatExceptionOfType(JammedPlayerException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — round is closed — propagates ActionRoundClosedException")
    void handleRoundClosed() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(false);
        willThrow(new ActionRoundClosedException())
                .given(round)
                .submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player already submitted — propagates DuplicateSubmissionException")
    void handleDuplicateSubmission() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(false);
        willThrow(new DuplicateSubmissionException(PLAYER_ID))
                .given(round)
                .submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.PROPHETS, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — passes isJammed value to submitSpecial")
    void handlePassesIsJammedToAggregate() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, Faction.ERASERS, SpecialAction.CORRUPT, null, null, UUID.randomUUID());
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(false);

        // when
        handler.handle(command);

        // then
        then(round)
                .should()
                .submitSpecial(
                        eq(PLAYER_ID),
                        eq(Faction.ERASERS),
                        eq(SpecialAction.CORRUPT),
                        isNull(),
                        isNull(),
                        any(),
                        eq(false));
    }
}
