package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.GameParticipantValidator;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundClosedException;
import io.github.temporalrift.game.action.domain.actionround.DuplicateSubmissionException;
import io.github.temporalrift.game.action.domain.actionround.FactionRequiredException;
import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.InvalidSpecialActionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.actionround.RoundNotFoundException;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionNotEligibleForEraException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
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
import io.github.temporalrift.game.action.domain.port.out.SpecialActionEraUsageRepository;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraBudgetExhaustedException;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

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
    SpecialActionEraUsageRepository specialActionEraUsageRepository;

    @Mock
    io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository sealGameUsageRepository;

    @Mock
    GameRulesPort gameRules;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    ActionRound round;

    @Mock
    PlayerState playerState;

    @Mock
    ActionTargetValidator actionTargetValidator;

    @Mock
    GameParticipantValidator gameParticipantValidator;

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
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.ANNIHILATE,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(round.submit(any())).willReturn(false);
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
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(0);
        given(round.submit(any())).willReturn(true);
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
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null, null, null);

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
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null, null, null);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — player faction missing — throws FactionRequiredException")
    void handlePlayerFactionMissing() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(null);

        // when / then
        assertThatExceptionOfType(FactionRequiredException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
        then(actionRoundRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — player is jammed — throws JammedPlayerException before submitting")
    void handleJammedPlayer() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.ANNIHILATE, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(true);

        // when / then
        assertThatExceptionOfType(JammedPlayerException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — faction does not own the special action — throws InvalidSpecialActionException before "
            + "submitting")
    void handleFactionDoesNotOwnSpecialAction() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);

        // when / then
        assertThatExceptionOfType(InvalidSpecialActionException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — THREAD — validates the current-era target coordinate like every other special and submits")
    void handleThreadValidatesTargetCoordinatesAndSubmits() {
        // given
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.THREAD, null, null, targetEventId, targetOutcomeId, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.WEAVERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        then(actionTargetValidator).should().validate(GAME_ID, ERA, targetEventId, targetOutcomeId);
        then(round)
                .should()
                .submit(eq(new SubmittedAction.SpecialActionSubmission(
                        PLAYER_ID,
                        Faction.WEAVERS,
                        SpecialAction.THREAD,
                        null,
                        null,
                        targetEventId,
                        targetOutcomeId,
                        null)));
    }

    @Test
    @DisplayName("handle — THREAD missing its target coordinate — throws InvalidActionTargetException before "
            + "submitting")
    void handleThreadMissingTargetRejectsBeforeSubmitting() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.THREAD, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.WEAVERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName(
            "handle — THREAD carrying a source coordinate — throws InvalidActionTargetException before " + "submitting")
    void handleThreadCarryingSourceRejectsBeforeSubmitting() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.THREAD,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.WEAVERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — REWEAVE — carries no target coordinates and submits without additional validation")
    void handleReweaveSubmitsWithoutAdditionalTargetValidation() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.REWEAVE, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.WEAVERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        then(round)
                .should()
                .submit(eq(new SubmittedAction.SpecialActionSubmission(
                        PLAYER_ID, Faction.WEAVERS, SpecialAction.REWEAVE, null, null, null, null, null)));
    }

    @Test
    @DisplayName("handle — every faction representative special — valid path submits through normal commands")
    void handleRepresentativeSpecialPerFactionSubmits() {
        record FactionCase(Faction faction, SpecialAction special, UUID event, UUID outcome, UUID player) {}
        var cases = List.of(
                new FactionCase(Faction.ERASERS, SpecialAction.CASCADE, UUID.randomUUID(), UUID.randomUUID(), null),
                new FactionCase(Faction.PROPHETS, SpecialAction.FORESIGHT, UUID.randomUUID(), UUID.randomUUID(), null),
                new FactionCase(Faction.PROPHETS, SpecialAction.FULFILLMENT, UUID.randomUUID(), null, null),
                new FactionCase(
                        Faction.REVISIONISTS, SpecialAction.REWRITE, UUID.randomUUID(), UUID.randomUUID(), null),
                new FactionCase(Faction.REVISIONISTS, SpecialAction.OBSCURE, null, null, null),
                new FactionCase(Faction.WEAVERS, SpecialAction.TAPESTRY, null, null, null));
        for (var c : cases) {
            var command = new PlaySpecialActionUseCase.Command(
                    GAME_ID, ERA, ROUND, PLAYER_ID, c.special(), null, null, c.event(), c.outcome(), c.player());
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                    .willReturn(Optional.of(round));
            given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID))
                    .willReturn(Optional.of(playerState));
            given(playerState.faction()).willReturn(c.faction());
            given(playerState.isJammed()).willReturn(false);
            given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
            given(gameRules.maxEras()).willReturn(5);
            given(round.submit(any())).willReturn(false);
            given(round.id()).willReturn(UUID.randomUUID());
            given(round.gameId()).willReturn(GAME_ID);
            given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

            handler.handle(command);

            then(round)
                    .should()
                    .submit(eq(new SubmittedAction.SpecialActionSubmission(
                            PLAYER_ID, c.faction(), c.special(), null, null, c.event(), c.outcome(), c.player())));
            org.mockito.Mockito.reset(round, actionRoundRepository, playerStateRepository, playerState);
        }
    }

    @Test
    @DisplayName("handle — CASCADE in the final era — rejected before any budget or round submission")
    void handleFinalEraCascadeRejectedWithoutSpending() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                5,
                ROUND,
                PLAYER_ID,
                SpecialAction.CASCADE,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 5, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.maxEras()).willReturn(5);

        // when / then
        assertThatExceptionOfType(SpecialActionNotEligibleForEraException.class)
                .isThrownBy(() -> handler.handle(command));
        then(specialActionEraUsageRepository).shouldHaveNoInteractions();
        then(round).should(never()).submit(any());
        then(actionRoundRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("handle — MIMIC — era-budgeted valid path claims budget and submits")
    void handleMimicClaimsEraBudgetAndSubmits() {
        // given
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.MIMIC, null, null, targetEventId, targetOutcomeId, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.REVISIONISTS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.MIMIC));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.empty());
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(SpecialActionEraUsage.class);
        then(specialActionEraUsageRepository).should().save(captor.capture());
        assertThat(captor.getValue().claimedSpecials()).containsExactly(SpecialAction.MIMIC);
        then(round).should().submit(any());
        then(sealGameUsageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — special action carrying a source coordinate — throws InvalidActionTargetException")
    void handleSpecialCarryingSourceRejectsBeforeSubmitting() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.ANNIHILATE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        // when / then
        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
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
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(0);
        willThrow(new ActionRoundClosedException()).given(round).submit(any());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);

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
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(0);
        willThrow(new DuplicateSubmissionException(PLAYER_ID)).given(round).submit(any());
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);

        // when / then
        assertThatExceptionOfType(DuplicateSubmissionException.class).isThrownBy(() -> handler.handle(command));
    }

    @Test
    @DisplayName("handle — builds a SpecialActionSubmission carrying the resolved faction and command fields")
    void handleBuildsSubmissionWithResolvedFaction() {
        // given
        var targetPlayerId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, null, null, targetPlayerId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        then(gameParticipantValidator).should().requireParticipant(GAME_ID, targetPlayerId);
        then(round)
                .should()
                .submit(eq(new SubmittedAction.SpecialActionSubmission(
                        PLAYER_ID, Faction.ERASERS, SpecialAction.CORRUPT, null, null, null, null, targetPlayerId)));
    }

    @Test
    @DisplayName("handle — Corrupt targeting a player not in this game — throws PlayerStateNotFoundException before "
            + "submitting")
    void handleCorruptTargetingNonOpponentRejectsBeforeSubmitting() {
        // given
        var targetPlayerId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, null, null, targetPlayerId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        willThrow(new PlayerStateNotFoundException(GAME_ID, targetPlayerId))
                .given(gameParticipantValidator)
                .requireParticipant(GAME_ID, targetPlayerId);

        // when / then
        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — target does not belong to current game/era — propagates UnknownActionTargetException")
    void handleUnknownActionTargetPropagates() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
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
                GAME_ID, ERA, 1, PLAYER_ID, SpecialAction.EXPOSE, null, null, null, null, UUID.randomUUID());
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, 1))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        assertThatExceptionOfType(ExposeUnavailableException.class).isThrownBy(() -> handler.handle(command));

        then(round).should(never()).submit(any());
        then(activistEraStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — Activist Expose missing targetPlayerId — rejected before recordExpose runs")
    void handleActivistExposeWithoutTargetPlayerRejectsBeforeRecordExpose() {
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, 2, 2, PLAYER_ID, SpecialAction.EXPOSE, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 2, 2))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> handler.handle(command));

        then(actionRoundRepository).should(never()).findByGameIdAndEraNumberAndRoundNumber(any(), anyInt(), anyInt());
        then(activistEraStateRepository).shouldHaveNoInteractions();
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — Activist Expose carrying a targetEventId — rejected before recordExpose runs")
    void handleActivistExposeCarryingTargetEventRejectsBeforeRecordExpose() {
        var targetPlayerId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, 2, 2, PLAYER_ID, SpecialAction.EXPOSE, null, null, UUID.randomUUID(), null, targetPlayerId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 2, 2))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());

        assertThatExceptionOfType(InvalidActionTargetException.class).isThrownBy(() -> handler.handle(command));

        then(actionRoundRepository).should(never()).findByGameIdAndEraNumberAndRoundNumber(any(), anyInt(), anyInt());
        then(activistEraStateRepository).shouldHaveNoInteractions();
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — Activist Expose in Round 2 — records the Round-1 probability signature")
    void handleActivistExposeRecordsRoundOneProbabilitySignature() {
        var targetPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, 2, 2, PLAYER_ID, SpecialAction.EXPOSE, null, null, null, null, targetPlayerId);
        var roundOne = mock(ActionRound.class);
        var roundOneCard = new SubmittedAction.CardAction(
                targetPlayerId, UUID.randomUUID(), CardType.PUSH, targetEventId, sourceOutcomeId, targetOutcomeId);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, 2, 2))
                .willReturn(Optional.of(round));
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, 2, 1))
                .willReturn(Optional.of(roundOne));
        given(roundOne.submittedActions()).willReturn(List.of(roundOneCard));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(Set.of());
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 2, PLAYER_ID))
                .willReturn(Optional.empty());
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, 1, PLAYER_ID))
                .willReturn(Optional.empty());
        given(round.submit(any())).willReturn(false);
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

    @Test
    @DisplayName("handle — budgeted special never used this era — claims the budget and submits")
    void handleBudgetedSpecialFirstUseClaimsAndSubmits() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.ANNIHILATE,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.ANNIHILATE, SpecialAction.SEAL));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.empty());
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(SpecialActionEraUsage.class);
        then(specialActionEraUsageRepository).should().save(captor.capture());
        assertThat(captor.getValue().claimedSpecials()).containsExactly(SpecialAction.ANNIHILATE);
        then(round).should().submit(any());
    }

    @Test
    @DisplayName("handle — budgeted special already used this era — throws before submitting and does not re-save")
    void handleBudgetedSpecialSecondUseRejectsBeforeSubmitting() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.ANNIHILATE,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.ANNIHILATE, SpecialAction.SEAL));
        var alreadyUsed = SpecialActionEraUsage.reconstitute(
                UUID.randomUUID(), GAME_ID, ERA, PLAYER_ID, EnumSet.of(SpecialAction.ANNIHILATE));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.of(alreadyUsed));

        // when / then
        assertThatExceptionOfType(SpecialActionEraBudgetExhaustedException.class)
                .isThrownBy(() -> handler.handle(command));
        then(specialActionEraUsageRepository).should(never()).save(any());
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — different budgeted special used this era — the new special's budget is independent")
    void handleBudgetedSpecialIndependentPerSpecialType() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.CORRUPT, null, null, null, null, UUID.randomUUID());
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ERASERS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials())
                .willReturn(EnumSet.of(SpecialAction.ANNIHILATE, SpecialAction.CORRUPT));
        var alreadyUsedAnnihilate = SpecialActionEraUsage.reconstitute(
                UUID.randomUUID(), GAME_ID, ERA, PLAYER_ID, EnumSet.of(SpecialAction.ANNIHILATE));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.of(alreadyUsedAnnihilate));
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(SpecialActionEraUsage.class);
        then(specialActionEraUsageRepository).should().save(captor.capture());
        assertThat(captor.getValue().claimedSpecials())
                .containsExactlyInAnyOrder(SpecialAction.ANNIHILATE, SpecialAction.CORRUPT);
        then(round).should().submit(any());
    }

    @Test
    @DisplayName("handle — special outside the configured budget — never touches the budget repository")
    void handleNonBudgetedSpecialSkipsBudgetRepository() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.FORESIGHT,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.ANNIHILATE));
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        then(specialActionEraUsageRepository).shouldHaveNoInteractions();
        then(sealGameUsageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — SEAL — claims era and game budgets together on acceptance")
    void handleSealClaimsEraAndGameBudgets() {
        // given
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, targetEventId, targetOutcomeId, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.SEAL));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.empty());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(0);
        given(round.submit(any())).willReturn(false);
        given(round.id()).willReturn(UUID.randomUUID());
        given(round.gameId()).willReturn(GAME_ID);
        given(round.pullEvents()).willReturn(List.of(specialActionPlayedEvent()));

        // when
        handler.handle(command);

        // then
        var eraCaptor = org.mockito.ArgumentCaptor.forClass(SpecialActionEraUsage.class);
        then(specialActionEraUsageRepository).should().save(eraCaptor.capture());
        assertThat(eraCaptor.getValue().claimedSpecials()).containsExactly(SpecialAction.SEAL);
        then(round).should().submit(any());
    }

    @Test
    @DisplayName("handle — SEAL game budget exhausted — rejects before submitting and saves nothing")
    void handleSealGameBudgetExhaustedRejectsBeforeSubmitting() {
        // given
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                SpecialAction.SEAL,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.SEAL));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.empty());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(2);

        // when / then
        assertThatExceptionOfType(
                        io.github.temporalrift.game.action.domain.specialactionerausage.SealGameBudgetExhaustedException
                                .class)
                .isThrownBy(() -> handler.handle(command));
        then(specialActionEraUsageRepository).should(never()).save(any());
        then(round).should(never()).submit(any());
    }

    @Test
    @DisplayName("handle — budgeted SEAL rejected by later validation — spends neither budget")
    void handleSealRejectedByLaterValidationSpendsNothing() {
        // given a SEAL missing its target so action.validate() fails after both claims
        var command = new PlaySpecialActionUseCase.Command(
                GAME_ID, ERA, ROUND, PLAYER_ID, SpecialAction.SEAL, null, null, null, null, null);
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA, ROUND))
                .willReturn(Optional.of(round));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.PROPHETS);
        given(playerState.isJammed()).willReturn(false);
        given(gameRules.onceEraBudgetedSpecials()).willReturn(EnumSet.of(SpecialAction.SEAL));
        given(specialActionEraUsageRepository.findByGameIdAndEraNumberAndPlayerId(GAME_ID, ERA, PLAYER_ID))
                .willReturn(Optional.empty());
        given(gameRules.sealMaxUsesPerGame()).willReturn(2);
        given(sealGameUsageRepository.countAcceptedSeals(GAME_ID, PLAYER_ID)).willReturn(0);

        // when / then
        assertThatExceptionOfType(
                        io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException.class)
                .isThrownBy(() -> handler.handle(command));
        then(specialActionEraUsageRepository).should(never()).save(any());
        then(round).should(never()).submit(any());
    }

    private static SpecialActionPlayed specialActionPlayedEvent() {
        return new SpecialActionPlayed(
                GAME_ID,
                ERA,
                ROUND,
                PLAYER_ID,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                null,
                null,
                UUID.randomUUID(),
                null,
                null);
    }
}
