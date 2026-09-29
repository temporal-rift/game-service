package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.application.port.in.PassParadoxResolutionUseCase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.DuplicateParadoxResolutionSubmissionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhaseNotOpenException;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.playerstate.PlayerStateNotFoundException;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.model.CardType;

@ExtendWith(MockitoExtension.class)
class PassParadoxResolutionCommandHandlerTest {

    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final int ERA = 2;
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Mock
    ParadoxResolutionPhaseRepository phaseRepository;

    @Mock
    PlayerStateRepository playerStateRepository;

    @Mock
    PlayerState playerState;

    private PassParadoxResolutionCommandHandler handler() {
        return new PassParadoxResolutionCommandHandler(
                phaseRepository, playerStateRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void passConsumesThePhaseSlotWithoutTouchingTheHand() {
        var phase = openPhase();
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        var result = handler().handle(command());

        assertThat(result).isEqualTo(new PassParadoxResolutionUseCase.Result(GAME_ID, ERA, PLAYER_ID));
        assertThat(phase.submittedPlayerIds()).containsExactly(PLAYER_ID);
        then(phaseRepository).should().save(phase);
        then(playerStateRepository).should(never()).save(any());
    }

    @Test
    void rejectsAPassAfterTheSameSlotWasUsed() {
        var phase = openPhase();
        phase.submit(PLAYER_ID, CardType.STABILIZE, NOW);
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.of(playerState));

        var handler = handler();
        var command = command();

        assertThatExceptionOfType(DuplicateParadoxResolutionSubmissionException.class)
                .isThrownBy(() -> handler.handle(command));
        then(phaseRepository).should(never()).save(any());
    }

    @Test
    void rejectsAPassWhenNoPhaseIsOpen() {
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.empty());

        var handler = handler();
        var command = command();

        assertThatExceptionOfType(ParadoxResolutionPhaseNotOpenException.class)
                .isThrownBy(() -> handler.handle(command));
    }

    @Test
    void rejectsAPassFromANonParticipant() {
        var phase = openPhase();
        given(phaseRepository.findByGameIdAndEraNumberWithLock(GAME_ID, ERA)).willReturn(Optional.of(phase));
        given(playerStateRepository.findByGameIdAndPlayerId(GAME_ID, PLAYER_ID)).willReturn(Optional.empty());

        var handler = handler();
        var command = command();

        assertThatExceptionOfType(PlayerStateNotFoundException.class).isThrownBy(() -> handler.handle(command));
        assertThat(phase.submittedPlayerIds()).isEmpty();
    }

    private static ParadoxResolutionPhase openPhase() {
        return new ParadoxResolutionPhase(UUID.randomUUID(), GAME_ID, ERA, NOW.plusSeconds(60), Set.of());
    }

    private static PassParadoxResolutionUseCase.Command command() {
        return new PassParadoxResolutionUseCase.Command(GAME_ID, ERA, PLAYER_ID);
    }
}
