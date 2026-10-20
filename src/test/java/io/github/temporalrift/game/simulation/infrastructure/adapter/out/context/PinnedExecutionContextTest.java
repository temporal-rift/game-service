package io.github.temporalrift.game.simulation.infrastructure.adapter.out.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.simulation.domain.event.ExecutionClosed;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

@ExtendWith(MockitoExtension.class)
class PinnedExecutionContextTest {

    private static final Execution FIRST = Execution.configure(ExecutionContextTestData.context("1"));
    private static final Execution SECOND = Execution.configure(ExecutionContextTestData.context("2"));

    @Mock
    ExecutionRepository executions;

    @Test
    @DisplayName("an open context is read once and then served from memory")
    void current_openContext_isReadOnce() {
        given(executions.find()).willReturn(Optional.of(FIRST));
        var pinned = new PinnedExecutionContext(executions);

        assertThat(pinned.current()).contains(FIRST.context());
        assertThat(pinned.require()).isEqualTo(FIRST.context());

        verify(executions, times(1)).find();
    }

    @Test
    @DisplayName("with no context open nothing is pinned and the context is required")
    void require_noContext_isNotConfigured() {
        given(executions.find()).willReturn(Optional.empty());
        var pinned = new PinnedExecutionContext(executions);

        assertThat(pinned.current()).isEmpty();
        assertThatThrownBy(pinned::require).isInstanceOf(ExecutionNotConfiguredException.class);
    }

    @Test
    @DisplayName("after a close the next context is read afresh")
    void onClosed_forgetsThePinnedContext() {
        given(executions.find()).willReturn(Optional.of(FIRST)).willReturn(Optional.of(SECOND));
        var pinned = new PinnedExecutionContext(executions);
        pinned.current();

        pinned.onClosed(new ExecutionClosed(FIRST.context().caseKey()));

        assertThat(pinned.current()).contains(SECOND.context());
    }

    @Test
    @DisplayName("a context read before a close committed is not pinned after it")
    void current_readOverlappingAClose_isNotPinned() {
        var pinned = new PinnedExecutionContext(executions);
        given(executions.find())
                .willAnswer(invocation -> {
                    pinned.onClosed(new ExecutionClosed(FIRST.context().caseKey()));
                    return Optional.of(FIRST);
                })
                .willReturn(Optional.of(SECOND));

        assertThat(pinned.current()).contains(FIRST.context());
        assertThat(pinned.current()).contains(SECOND.context());
    }
}
