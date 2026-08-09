package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.domain.actionround.UnknownActionTargetException;
import io.github.temporalrift.game.action.domain.event.ParadoxResolutionCardPlayed;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.CardNotEligibleForParadoxResolutionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.DuplicateParadoxResolutionSubmissionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.DomainEventEnvelope;

@ExtendWith(MockitoExtension.class)
class PlayParadoxResolutionCardCommandHandlerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID CARD_INSTANCE_ID = UUID.randomUUID();
    private static final UUID TARGET_EVENT_ID = UUID.randomUUID();
    private static final UUID TARGET_OUTCOME_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    ActionTargetValidator actionTargetValidator;

    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    PlayParadoxResolutionCardCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlayParadoxResolutionCardCommandHandler(
                phaseRepository, playerStateRepository, actionEventPublisher, actionTargetValidator, clock);
    }

    @Test
    void acceptsCardRemovesItAndPublishesExactPayload() {
        var phase = openPhase();
        var playerState = playerStateWithCard(CardType.STABILIZE);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));

        var result = handler.handle(command());

        assertThat(result).isEqualTo(new PlayParadoxResolutionCardUseCase.Result(GAME_ID, ERA, PLAYER_ID));
        assertThat(playerState.hand()).isEmpty();
        assertThat(phase.submittedPlayerIds()).containsExactly(PLAYER_ID);
        then(phaseRepository).should().save(phase);
        then(playerStateRepository).should().save(playerState);
        var envelope = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        then(actionEventPublisher).should().publish(envelope.capture());
        assertThat(envelope.getValue().payload())
                .isEqualTo(new ParadoxResolutionCardPlayed(
                        GAME_ID,
                        ERA,
                        PLAYER_ID,
                        CARD_INSTANCE_ID,
                        CardType.STABILIZE,
                        TARGET_EVENT_ID,
                        TARGET_OUTCOME_ID));
    }

    @Test
    void rejectsWhenNoMatchingPhaseExists() {
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.empty());

        assertThatExceptionOfType(ParadoxResolutionPhaseNotOpenException.class)
                .isThrownBy(() -> handler.handle(command()));
        then(playerStateRepository).shouldHaveNoInteractions();
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void duplicateIsRejectedBeforeTheHandIsLoaded() {
        var phase = openPhase();
        phase.submit(PLAYER_ID, CardType.PUSH, NOW);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));

        assertThatExceptionOfType(DuplicateParadoxResolutionSubmissionException.class)
                .isThrownBy(() -> handler.handle(command()));
        then(playerStateRepository).shouldHaveNoInteractions();
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void ineligibleCardRemainsInHandAndNothingIsSaved() {
        var phase = openPhase();
        var playerState = playerStateWithCard(CardType.COLLIDE);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));

        assertThatExceptionOfType(CardNotEligibleForParadoxResolutionException.class)
                .isThrownBy(() -> handler.handle(command()));
        assertThat(playerState.hand()).hasSize(1);
        then(phaseRepository).should(never()).save(any());
        then(playerStateRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void targetValidationRunsBeforePhaseMutation() {
        willThrow(new UnknownActionTargetException(TARGET_EVENT_ID))
                .given(actionTargetValidator)
                .validate(GAME_ID, ERA, TARGET_EVENT_ID, TARGET_OUTCOME_ID);

        assertThatExceptionOfType(UnknownActionTargetException.class).isThrownBy(() -> handler.handle(command()));
        then(phaseRepository).shouldHaveNoInteractions();
    }

    private PlayParadoxResolutionCardUseCase.Command command() {
        return new PlayParadoxResolutionCardUseCase.Command(
                GAME_ID, ERA, PLAYER_ID, CARD_INSTANCE_ID, TARGET_EVENT_ID, TARGET_OUTCOME_ID);
    }

    private ParadoxResolutionPhase openPhase() {
        return new ParadoxResolutionPhase(UUID.randomUUID(), GAME_ID, ERA, NOW.plusSeconds(30));
    }

    private PlayerState playerStateWithCard(CardType cardType) {
        var playerState = new PlayerState(UUID.randomUUID(), GAME_ID, PLAYER_ID);
        playerState.dealCard(new PlayerState.CardInstance(CARD_INSTANCE_ID, cardType), 5);
        return playerState;
    }
}
