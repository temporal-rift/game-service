package io.github.temporalrift.game.simulation.infrastructure.adapter.out.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe.Observation;
import io.github.temporalrift.game.simulation.domain.event.ExecutionClosed;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

@ExtendWith(MockitoExtension.class)
class ExecutionGameScopeTest {

    private static final UUID GAME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_GAME = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    ExecutionRepository executions;

    @Mock
    ExecutionProbe sessionProbe;

    @Mock
    ExecutionProbe actionProbe;

    private ExecutionGameScope scope() {
        return new ExecutionGameScope(
                new PinnedExecutionContext(executions), List.of(actionProbe, sessionProbe), CLOCK);
    }

    private void open() {
        given(executions.find()).willReturn(Optional.of(Execution.configure(ExecutionContextTestData.context("1"))));
    }

    private static Observation hosting(UUID gameId) {
        return new Observation(gameId, false, 0, 0, null);
    }

    @Test
    @DisplayName("with no context open no game is admitted")
    void admits_noContextOpen_isFalse() {
        given(executions.find()).willReturn(Optional.empty());

        assertThat(scope().admits(GAME)).isFalse();
    }

    @Test
    @DisplayName("an event without a game is never admitted")
    void admits_noGameId_isFalse() {
        assertThat(scope().admits(null)).isFalse();
    }

    @Test
    @DisplayName("only the game the open context hosts is admitted")
    void admits_hostedGame_isTrueAndOthersAreNot() {
        open();
        given(actionProbe.observe(any(Instant.class))).willReturn(hosting(null));
        given(sessionProbe.observe(any(Instant.class))).willReturn(hosting(GAME));
        var scope = scope();

        assertThat(scope.admits(GAME)).isTrue();
        assertThat(scope.admits(OTHER_GAME)).isFalse();
    }

    @Test
    @DisplayName("before the context hosts a game no game is admitted, and the hosted game is found once it exists")
    void admits_contextWithoutGameYet_isFalse() {
        open();
        given(actionProbe.observe(any(Instant.class))).willReturn(hosting(null));
        given(sessionProbe.observe(any(Instant.class)))
                .willReturn(hosting(null))
                .willReturn(hosting(GAME));
        var scope = scope();

        assertThat(scope.admits(GAME)).isFalse();
        assertThat(scope.admits(GAME)).isTrue();
    }

    @Test
    @DisplayName("the hosted game is looked up once and forgotten when the context closes")
    void onClosed_forgetsTheHostedGame() {
        open();
        given(actionProbe.observe(any(Instant.class))).willReturn(hosting(null));
        given(sessionProbe.observe(any(Instant.class)))
                .willReturn(hosting(GAME))
                .willReturn(hosting(OTHER_GAME));
        var scope = scope();
        assertThat(scope.admits(GAME)).isTrue();
        assertThat(scope.admits(GAME)).isTrue();
        verify(sessionProbe, times(1)).observe(any(Instant.class));

        scope.onClosed(new ExecutionClosed(ExecutionContextTestData.CASE_KEY));

        assertThat(scope.admits(GAME)).isFalse();
        assertThat(scope.admits(OTHER_GAME)).isTrue();
    }
}
