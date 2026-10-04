package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.action.application.port.in.DeclineDeclarationUseCase;
import io.github.temporalrift.game.action.domain.actionround.InvalidSpecialActionException;
import io.github.temporalrift.game.action.domain.actionround.JammedPlayerException;
import io.github.temporalrift.game.action.domain.activisterastate.DeclarationWindowClosedException;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationAlreadyDecidedException;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.DeclarationPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.domain.event.DeclarationPhaseClosed;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class DeclineDeclarationCommandHandlerTest {
    static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    static final UUID GAME = UUID.randomUUID();
    static final UUID PLAYER = UUID.randomUUID();

    @Mock
    PlayerStateRepository players;

    @Mock
    DeclarationPhaseRepository phases;

    @Mock
    ApplicationEventPublisher events;

    DeclineDeclarationCommandHandler handler;
    PlayerState player;
    DeclarationPhase phase;

    @BeforeEach
    void setUp() {
        handler = new DeclineDeclarationCommandHandler(players, phases, events, Clock.fixed(NOW, ZoneOffset.UTC));
        player = new PlayerState(UUID.randomUUID(), GAME, PLAYER);
        phase = new DeclarationPhase(UUID.randomUUID(), GAME, 1, NOW.plusSeconds(120), List.of(PLAYER));
        given(players.findByGameIdAndPlayerIdWithLock(GAME, PLAYER)).willReturn(Optional.of(player));
        given(phases.findByGameIdAndEraNumberWithLock(GAME, 1)).willReturn(Optional.of(phase));
    }

    @Test
    void lastDecline_closesOnceAndRetryAcknowledgesEvenAfterExpiry() {
        player.assignFaction(Faction.ACTIVISTS);
        var command = new DeclineDeclarationUseCase.Command(GAME, 1, PLAYER);
        assertThat(handler.handle(command)).isEqualTo(new DeclineDeclarationUseCase.Result(GAME, 1, PLAYER));
        handler = new DeclineDeclarationCommandHandler(
                players, phases, events, Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));
        assertThat(handler.handle(command)).isEqualTo(new DeclineDeclarationUseCase.Result(GAME, 1, PLAYER));
        then(phases).should().save(phase);
        then(events).should().publishEvent(new DeclarationPhaseClosed(GAME, 1));
        then(players).should(never()).save(any());
    }

    @Test
    void declineCannotReplaceAcceptedDeclaration() {
        player.assignFaction(Faction.ACTIVISTS);
        phase.decide(PLAYER, DeclarationDecision.DECLARED, NOW);
        var command = new DeclineDeclarationUseCase.Command(GAME, 1, PLAYER);
        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DeclarationAlreadyDecidedException.class);
        then(phases).should(never()).save(any());
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void expiredPendingDecision_isRejected() {
        player.assignFaction(Faction.ACTIVISTS);
        handler = new DeclineDeclarationCommandHandler(
                players, phases, events, Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
        var command = new DeclineDeclarationUseCase.Command(GAME, 1, PLAYER);
        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DeclarationWindowClosedException.class);
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void otherFaction_isRejected() {
        player.assignFaction(Faction.PROPHETS);
        var command = new DeclineDeclarationUseCase.Command(GAME, 1, PLAYER);
        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(InvalidSpecialActionException.class);
        then(phases).should(never()).save(any());
    }

    @Test
    void jammedPlayer_isRejected() {
        player.assignFaction(Faction.ACTIVISTS);
        player.applyJam();
        var command = new DeclineDeclarationUseCase.Command(GAME, 1, PLAYER);
        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(JammedPlayerException.class);
        then(events).shouldHaveNoInteractions();
    }
}
