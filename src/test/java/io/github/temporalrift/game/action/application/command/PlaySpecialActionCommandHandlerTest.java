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
import static org.mockito.Mockito.mock;
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

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.UnknownActionTargetException;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.ExposeUnavailableException;
import io.github.temporalrift.game.action.domain.event.SpecialActionPlayed;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

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
    ActivistEraStateRepository activistEraStateRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    ActionRound round;

    @Mock
    PlayerState playerState;

    @Mock
    ActionTargetValidator actionTargetValidator;

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    PlaySpecialActionCommandHandler handler;

    @Test
    @DisplayName(
            "handle — happy path — saves round, publishes special action, does not save player state, returns result")
    void handleHappyPath() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.ANNIHILATE, UUID.randomUUID(), UUID.randomUUID(), null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        var result = handler.handle(command);

        // then
        then(actionRoundRepository).should().save(round);
        then(playerStateRepository).should(never()).save(any());
        then(actionEventPublisher).should().publish(any());
        then(actionEventPublisher).should().publishInternally(any());
        assertThat(result.gameId()).isEqualTo(GAME_ID);
        assertThat(result.eraNumber()).isEqualTo(ERA);
        assertThat(result.roundNumber()).isEqualTo(ROUND);
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.roundClosed()).isFalse();
    }

    @Test
    @DisplayName("handle — all players submitted — does not close directly and returns roundClosed true")
    void handleAllSubmittedDoesNotCloseDirectly() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(true);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        var result = handler.handle(command);

        // then
        then(round).should(never()).close(any());
        assertThat(result.roundClosed()).isTrue();
    }

    @Test
    @DisplayName("handle — round not found — throws RoundNotFoundException")
    void handleRoundNotFound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.empty());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(RoundNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player state not found — throws PlayerStateNotFoundException")
    void handlePlayerStateNotFound() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.empty());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player faction missing — throws FactionRequiredException")
    void handlePlayerFactionMissing() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(null);

        // when / then
        assertThatExceptionOfType(FactionRequiredException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        then(actionRoundRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — player is jammed — propagates JammedPlayerException from aggregate")
    void handleJammedPlayer() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.ANNIHILATE, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
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
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        willThrow(new ActionRoundClosedException())
                .given(round)
                .submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(ActionRoundClosedException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player already submitted — propagates DuplicateSubmissionException")
    void handleDuplicateSubmission() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        willThrow(new DuplicateSubmissionException(PLAYER_ID))
                .given(round)
                .submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — passes isJammed value to submitSpecial")
    void handlePassesIsJammedToAggregate() {
        // given
        var targetPlayerId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, targetPlayerId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, targetPlayerId))
                .willReturn(Optional.of(mock(PlayerState.class)));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

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

    @Test
    @DisplayName("handle — Corrupt targeting a player not in this game — throws PlayerStateNotFoundException before "
            + "submitting")
    void handleCorruptTargetingNonOpponentRejectsBeforeSubmitting() {
        // given
        var targetPlayerId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, targetPlayerId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, targetPlayerId))
                .willReturn(Optional.empty());
        given(playerState.faction()).willReturn(Faction.ERASERS);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("handle — target does not belong to current game/era — propagates UnknownActionTargetException")
    void handleUnknownActionTargetPropagates() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, UUID.randomUUID(), UUID.randomUUID(), null);
        willThrow(new UnknownActionTargetException(command.targetEventId()))
                .given(actionTargetValidator)
                .validate(any(), eq(ERA), any(), any());

        // when / then
        assertThatExceptionOfType(UnknownActionTargetException.class).isThrownBy(() -> handler.handle(command));
        then(actionRoundRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — Activist Expose outside Round 2 — rejects before submitting")
    void handleActivistExposeOutsideRoundTwoRejectsBeforeSubmitting() {
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                1,
                PLAYER_ID,
                SpecialAction.EXPOSE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);

        assertThatExceptionOfType(ExposeUnavailableException.class).isThrownBy(() -> handler.handle(command));

        then(round).should(never()).submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean());
        then(activistEraStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — Activist Expose in Round 2 — records the Round-1 probability signature")
    void handleActivistExposeRecordsRoundOneProbabilitySignature() {
        var targetPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, 2, 2, PLAYER_ID, SpecialAction.EXPOSE, targetEventId, targetOutcomeId, targetPlayerId);
        var roundOne = mock(ActionRound.class);
        var roundOneCard = new io.github.temporalrift.game.action.domain.actionround.SubmittedAction.CardAction(
                targetPlayerId, UUID.randomUUID(), CardType.PUSH, targetEventId, sourceOutcomeId, targetOutcomeId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 2, 2))
                .willReturn(Optional.of(round));
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, 2, 1))
                .willReturn(Optional.of(roundOne));
        given(roundOne.submittedActions()).willReturn(List.of(roundOneCard));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 2, PLAYER_ID))
                .willReturn(Optional.empty());
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 1, PLAYER_ID))
                .willReturn(Optional.empty());
        given(round.submitSpecial(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        handler.handle(command);

        var state = org.mockito.ArgumentCaptor.forClass(ActivistEraState.class);
        then(activistEraStateRepository).should().save(state.capture());
        assertThat(state.getValue().exposedPlayerId()).isEqualTo(targetPlayerId);
        assertThat(state.getValue().exposedSignature())
                .isEqualTo(new io.github.temporalrift.game.action.domain.activisterastate.ProbabilityInfluenceSignature(
                        CardType.PUSH, targetEventId, sourceOutcomeId, targetOutcomeId));
    }

    private static SpecialActionPlayed specialActionPlayedEvent() {
        return new SpecialActionPlayed(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                UUID.randomUUID(),
                null,
                null);
    }
}
