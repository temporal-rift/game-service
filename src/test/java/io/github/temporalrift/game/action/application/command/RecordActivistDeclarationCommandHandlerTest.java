package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.Faction;

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
    PlayerStateRepository playerStateRepository;

    @Mock
    ActionTargetValidator actionTargetValidator;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    PlayerState playerState;

    @Spy
    Clock clock = CLOCK;

    @InjectMocks
    RecordActivistDeclarationCommandHandler handler;

    @Test
    @DisplayName("handle — Rally declaration — publishes the round-one timeline request metadata")
    void handleRallyPublishesTimelineRequestMetadata() {
        // given
        given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, 1))
                .willReturn(Optional.empty());
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
        then(actionEventPublisher)
                .should()
                .publishInternally(argThat(
                        event -> event.equals(new io.github.temporalrift.game.shared.ActivistDeclarationRecorded(
                                GAME_ID,
                                ERA_NUMBER,
                                1,
                                PLAYER_ID,
                                io.github.temporalrift.game.shared.SpecialAction.RALLY,
                                TARGET_EVENT_ID,
                                TARGET_OUTCOME_ID))));
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

        assertThatThrownBy(() -> handler.handle(new RecordActivistDeclarationUseCase.Command(
                        GAME_ID,
                        ERA_NUMBER,
                        PLAYER_ID,
                        ActivistDeclarationMode.RALLY,
                        TARGET_EVENT_ID,
                        TARGET_OUTCOME_ID)))
                .isInstanceOf(DeclarationWindowClosedException.class);

        then(activistEraStateRepository).should(never()).save(any());
        then(actionEventPublisher).shouldHaveNoInteractions();
    }

    private static io.github.temporalrift.game.action.domain.actionround.ActionRound mockRound() {
        return org.mockito.Mockito.mock(io.github.temporalrift.game.action.domain.actionround.ActionRound.class);
    }
}
