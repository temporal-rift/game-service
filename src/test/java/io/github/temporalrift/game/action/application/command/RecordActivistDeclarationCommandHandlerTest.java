package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.application.ActionTargetValidator;
import io.github.temporalrift.game.action.application.port.in.RecordActivistDeclarationUseCase;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistEraState;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.ActivistEraStateRepository;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordActivistDeclarationCommandHandler")
class RecordActivistDeclarationCommandHandlerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID TARGET_EVENT_ID = UUID.randomUUID();
    private static final UUID TARGET_OUTCOME_ID = UUID.randomUUID();
    private static final int ERA_NUMBER = 1;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    ActivistEraStateRepository activistEraStateRepository;

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    DeclarationPhaseRepository declarationPhaseRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionTargetValidator actionTargetValidator;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    PlayerState playerState;

    @Spy
    Clock clock = CLOCK;

    RecordActivistDeclarationCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RecordActivistDeclarationCommandHandler(
                activistEraStateRepository,
                actionRoundRepository,
                declarationPhaseRepository,
                playerStateRepository,
                actionTargetValidator,
                actionEventPublisher,
                applicationEventPublisher,
                clock);
    }

    @Test
    @DisplayName("handle — Rally declaration — publishes the round-one timeline request metadata")
    void handleRallyPublishesTimelineRequestMetadata() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, 1))
                .willReturn(Optional.empty());
        given(declarationPhaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER))
                .willReturn(Optional.of(openDeclarationPhase()));
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, ERA_NUMBER, PLAYER_ID))
                .willReturn(Optional.empty());

        // when
        handler.handle(new RecordActivistDeclarationUseCase.Command(
                GAME_ID, ERA_NUMBER, PLAYER_ID, ActivistDeclarationMode.RALLY, TARGET_EVENT_ID, TARGET_OUTCOME_ID));

        // then
        then(actionEventPublisher).should().publish(argThat(envelope -> {
            assertThat(envelope.aggregateType()).isEqualTo(ActivistEraState.AGGREGATE_TYPE);
            assertThat(envelope.gameId()).isEqualTo(GAME_ID);
            assertThat(envelope.occurredAt()).isEqualTo(CLOCK.instant());
            assertThat(envelope.payload())
                    .isEqualTo(new ActivistDeclarationRecorded(
                            GAME_ID,
                            ERA_NUMBER,
                            1,
                            PLAYER_ID,
                            ActivistDeclarationMode.RALLY,
                            TARGET_EVENT_ID,
                            TARGET_OUTCOME_ID));
            return true;
        }));
        then(activistEraStateRepository).should().save(any(ActivistEraState.class));
        var internalEventCaptor = ArgumentCaptor.forClass(Object.class);
        then(applicationEventPublisher).should().publishEvent(internalEventCaptor.capture());
        assertThat(internalEventCaptor.getValue())
                .isEqualTo(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded(
                        GAME_ID,
                        ERA_NUMBER,
                        1,
                        PLAYER_ID,
                        io.github.temporalrift.game.shared.domain.model.SpecialAction.RALLY,
                        TARGET_EVENT_ID,
                        TARGET_OUTCOME_ID));
    }

    @Test
    @DisplayName("handle — validates the window under a write lock so closure cannot interleave")
    void handleValidatesPhaseUnderWriteLock() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, 1))
                .willReturn(Optional.empty());
        given(declarationPhaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER))
                .willReturn(Optional.of(openDeclarationPhase()));
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));
        given(playerState.faction()).willReturn(Faction.ACTIVISTS);
        given(playerState.isJammed()).willReturn(false);
        given(activistEraStateRepository.findByGameIdAndEraNumberAndActivistPlayerId(GAME_ID, ERA_NUMBER, PLAYER_ID))
                .willReturn(Optional.empty());

        // when
        handler.handle(new RecordActivistDeclarationUseCase.Command(
                GAME_ID, ERA_NUMBER, PLAYER_ID, ActivistDeclarationMode.RALLY, TARGET_EVENT_ID, TARGET_OUTCOME_ID));

        // then — the timeout close takes the same row lock, so this serializes validation
        // against closure; an unlocked read must never be used here.
        then(declarationPhaseRepository).should().findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER);
        then(declarationPhaseRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("handle — Round 1 created while waiting for the player lock — rejects the declaration")
    void handleRejectsDeclarationWhenRoundOneCommitsWhileWaitingForPlayerLock() {
        // The callback represents ActionRoundSagaImpl committing Round 1 while this handler is
        // blocked on the player row. The boundary must be read only after that lock is acquired.
        var roundOneCreated = new AtomicBoolean();
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willAnswer(invocation -> {
                    roundOneCreated.set(true);
                    return Optional.of(playerState);
                });
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, 1))
                .willAnswer(invocation -> roundOneCreated.get() ? Optional.of(mockRound()) : Optional.empty());
        given(declarationPhaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER))
                .willReturn(Optional.of(openDeclarationPhase()));
        var command = new RecordActivistDeclarationUseCase.Command(
                GAME_ID, ERA_NUMBER, PLAYER_ID, ActivistDeclarationMode.RALLY, TARGET_EVENT_ID, TARGET_OUTCOME_ID);

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DeclarationWindowClosedException.class);

        then(activistEraStateRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    private static io.github.temporalrift.game.action.domain.actionround.ActionRound mockRound() {
        return mock(io.github.temporalrift.game.action.domain.actionround.ActionRound.class);
    }

    @Test
    @DisplayName("handle — missing declaration phase — rejects the declaration as a closed window")
    void handleRejectsDeclarationWhenPhaseMissing() {
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));
        given(declarationPhaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER))
                .willReturn(Optional.empty());
        var command = new RecordActivistDeclarationUseCase.Command(
                GAME_ID, ERA_NUMBER, PLAYER_ID, ActivistDeclarationMode.RALLY, TARGET_EVENT_ID, TARGET_OUTCOME_ID);

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DeclarationWindowClosedException.class);

        then(activistEraStateRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handle — expired declaration phase — rejects the declaration as a closed window")
    void handleRejectsDeclarationWhenPhaseExpired() {
        given(playerStateRepository.findByGameIdAndPlayerIdWithLock(GAME_ID, PLAYER_ID))
                .willReturn(Optional.of(playerState));
        given(declarationPhaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA_NUMBER))
                .willReturn(Optional.of(
                        io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase.reconstitute(
                                java.util.UUID.randomUUID(),
                                GAME_ID,
                                ERA_NUMBER,
                                CLOCK.instant().minusSeconds(1),
                                io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus
                                        .OPEN)));
        var command = new RecordActivistDeclarationUseCase.Command(
                GAME_ID, ERA_NUMBER, PLAYER_ID, ActivistDeclarationMode.RALLY, TARGET_EVENT_ID, TARGET_OUTCOME_ID);

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DeclarationWindowClosedException.class);

        then(activistEraStateRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    private static io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase openDeclarationPhase() {
        return new io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase(
                java.util.UUID.randomUUID(),
                GAME_ID,
                ERA_NUMBER,
                CLOCK.instant().plusSeconds(60));
    }
}
