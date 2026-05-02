package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.FactionRevealed;
import io.github.temporalrift.events.session.GameEnded;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.StartGameSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaState;
import io.github.temporalrift.game.session.domain.saga.StartGameSagaStatus;

@ExtendWith(MockitoExtension.class)
class EndGameSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(PLAYER_1, PLAYER_2);
    static final List<FactionAssignment> ASSIGNMENTS = List.of(
            new FactionAssignment(PLAYER_1, Faction.PROPHETS), new FactionAssignment(PLAYER_2, Faction.ERASERS));

    @Mock
    GameRepository gameRepository;

    @Mock
    StartGameSagaRepository startGameSagaRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    EndGameSagaStateManager stateManager;

    @Mock
    FinalScoreQueryPort finalScoreQueryPort;

    @InjectMocks
    EndGameSagaImpl saga;

    // ─── WIN_CONDITION_MET ────────────────────────────────────────────────────

    @Test
    @DisplayName("WIN_CONDITION_MET trigger — game ends, GameEnded and FactionRevealed published")
    void start_winConditionMet_publishesGameEndedAndFactionRevealed() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());

        // when
        saga.start(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_1);

        // then
        then(gameRepository).should().save(argThat(g -> g.status() == GameStatus.ENDED_BY_WIN));
        then(eventPublisher).should().publish(argThat(e -> e.payload() instanceof GameEnded));
        then(eventPublisher).should().publish(argThat(e -> e.payload() instanceof FactionRevealed));
        then(stateManager).should().complete(GAME_ID);
    }

    @Test
    @DisplayName("WIN_CONDITION_MET — GameEnded endReason is WIN_CONDITION_MET")
    void start_winConditionMet_endReasonIsCorrect() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());
        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        saga.start(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_1);

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        var gameEnded = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof GameEnded)
                .map(e -> (GameEnded) e.payload())
                .findFirst()
                .orElseThrow();
        assertThat(gameEnded.endReason()).isEqualTo("WIN_CONDITION_MET");
    }

    // ─── TIMELINE_COLLAPSED ───────────────────────────────────────────────────

    @Test
    @DisplayName("TIMELINE_COLLAPSED trigger — game ends, GameEnded with TIMELINE_COLLAPSED reason")
    void start_timelineCollapsed_endReasonIsTimelineCollapsed() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 3, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());
        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        saga.start(GAME_ID, EndGameTrigger.TIMELINE_COLLAPSED, PLAYER_1, PLAYER_2);

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        var gameEnded = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof GameEnded)
                .map(e -> (GameEnded) e.payload())
                .findFirst()
                .orElseThrow();
        assertThat(gameEnded.endReason()).isEqualTo("TIMELINE_COLLAPSED");
    }

    // ─── TIMELINE_STABILIZED ─────────────────────────────────────────────────

    @Test
    @DisplayName("TIMELINE_STABILIZED trigger — game ends, GameEnded with TIMELINE_STABILIZED reason")
    void start_timelineStabilized_endReasonIsTimelineStabilized() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 5, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());
        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        saga.start(GAME_ID, EndGameTrigger.TIMELINE_STABILIZED, PLAYER_1, PLAYER_2);

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        var gameEnded = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof GameEnded)
                .map(e -> (GameEnded) e.payload())
                .findFirst()
                .orElseThrow();
        assertThat(gameEnded.endReason()).isEqualTo("TIMELINE_STABILIZED");
    }

    // ─── FactionRevealed ─────────────────────────────────────────────────────

    @Test
    @DisplayName("FactionRevealed contains all players with their factions")
    void start_factionRevealedContainsAllAssignments() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(startGameSagaRepository.findByGameId(GAME_ID)).willReturn(Optional.of(startGameSagaState()));
        given(finalScoreQueryPort.getScores(GAME_ID)).willReturn(List.of());
        var captor = ArgumentCaptor.<EventEnvelope>captor();

        // when
        saga.start(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_1);

        // then
        then(eventPublisher).should(org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        var factionRevealed = captor.getAllValues().stream()
                .filter(e -> e.payload() instanceof FactionRevealed)
                .map(e -> (FactionRevealed) e.payload())
                .findFirst()
                .orElseThrow();
        assertThat(factionRevealed.reveals())
                .extracting(FactionRevealed.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_2);
    }

    // ─── idempotency ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("game already over — start is a no-op")
    void start_gameAlreadyOver_noOp() {
        // given
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.ENDED_BY_WIN);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        // when
        saga.start(GAME_ID, EndGameTrigger.WIN_CONDITION_MET, PLAYER_1);

        // then
        then(gameRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
        then(stateManager).should(never()).complete(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private StartGameSagaState startGameSagaState() {
        return new StartGameSagaState(
                UUID.randomUUID(), GAME_ID, LOBBY_ID, StartGameSagaStatus.COMPLETED, 5, ASSIGNMENTS);
    }
}
