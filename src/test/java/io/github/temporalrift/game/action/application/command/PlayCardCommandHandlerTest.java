package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayCardCommandHandler")
class PlayCardCommandHandlerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final UUID CARD_INSTANCE_ID = UUID.randomUUID();
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
    PlayCardCommandHandler handler;

    @Test
    @DisplayName("handle — happy path — saves round and player state, returns result")
    void handleHappyPath() {
        // given
        var cardInstanceId = UUID.randomUUID();
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, cardInstanceId, CardType.PUSH, UUID.randomUUID(), UUID.randomUUID());
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of(new PlayerState.CardInstance(cardInstanceId, CardType.PUSH)));
        given(round.submitCard(eq(PLAYER_ID), eq(cardInstanceId), eq(CardType.PUSH), any(), any(), anyList()))
                .willReturn(false);

        // when
        var result = handler.handle(command);

        // then
        then(actionRoundRepository).should().save(round);
        then(playerStateRepository).should().save(playerState);
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
        var cardInstanceId = UUID.randomUUID();
        var command =
                new PlayCardUseCase.Command(GAME_ID, ERA, ROUND, PLAYER_ID, cardInstanceId, CardType.PUSH, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of(new PlayerState.CardInstance(cardInstanceId, CardType.PUSH)));
        given(round.submitCard(any(), any(), any(), any(), any(), anyList())).willReturn(true);

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
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, UUID.randomUUID(), CardType.PUSH, null, null);

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
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, UUID.randomUUID(), CardType.PUSH, null, null);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — round is closed — propagates ActionRoundClosedException")
    void handleRoundClosed() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of(new PlayerState.CardInstance(CARD_INSTANCE_ID, CardType.PUSH)));
        willThrow(new ActionRoundClosedException())
                .given(round)
                .submitCard(any(), any(), any(), any(), any(), anyList());
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, CARD_INSTANCE_ID, CardType.PUSH, null, null);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — card not in hand — propagates CardNotInHandException")
    void handleCardNotInHand() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of());
        willThrow(new CardNotInHandException(CARD_INSTANCE_ID))
                .given(round)
                .submitCard(any(), any(), any(), any(), any(), anyList());
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, CARD_INSTANCE_ID, CardType.PUSH, null, null);

        // when / then
        assertThatExceptionOfType(CardNotInHandException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player already submitted — propagates DuplicateSubmissionException")
    void handleDuplicateSubmission() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of(new PlayerState.CardInstance(CARD_INSTANCE_ID, CardType.PUSH)));
        willThrow(new DuplicateSubmissionException(PLAYER_ID))
                .given(round)
                .submitCard(any(), any(), any(), any(), any(), anyList());
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, CARD_INSTANCE_ID, CardType.PUSH, null, null);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — submits player hand UUIDs to submitCard")
    void handleSubmitsHandUuids() {
        // given
        var c1 = new PlayerState.CardInstance(UUID.randomUUID(), CardType.PUSH);
        var c2 = new PlayerState.CardInstance(UUID.randomUUID(), CardType.JAM);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.hand()).willReturn(List.of(c1, c2));
        given(round.submitCard(any(), any(), any(), any(), any(), anyList())).willReturn(false);
        var command = new PlayCardUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, c1.cardInstanceId(), CardType.PUSH, null, null);

        // when
        handler.handle(command);

        // then
        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(List.class);
        then(round)
                .should()
                .submitCard(
                        eq(PLAYER_ID),
                        eq(c1.cardInstanceId()),
                        eq(CardType.PUSH),
                        isNull(),
                        isNull(),
                        captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(c1.cardInstanceId(), c2.cardInstanceId());
    }
}
