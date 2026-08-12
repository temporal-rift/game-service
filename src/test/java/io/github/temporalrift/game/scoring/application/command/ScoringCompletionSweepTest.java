package io.github.temporalrift.game.scoring.application.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.context.PendingEraScoringCompletion;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;

@ExtendWith(MockitoExtension.class)
class ScoringCompletionSweepTest {

    @Mock
    EraScoringContextRepository contextRepository;

    @Mock
    EraScoringCompletionChecker completionChecker;

    @InjectMocks
    ScoringCompletionSweep sweep;

    @Test
    @DisplayName("resolved-but-unscored eras are retried")
    void sweep_retriesPendingEras() {
        var pending = new PendingEraScoringCompletion(UUID.randomUUID(), 2);
        given(contextRepository.findResolvedErasNotYetScored()).willReturn(List.of(pending));

        sweep.sweep();

        then(completionChecker).should().tryComplete(pending.gameId(), pending.eraNumber());
    }

    @Test
    @DisplayName("nothing pending — checker untouched")
    void sweep_nothingPending_noRetry() {
        given(contextRepository.findResolvedErasNotYetScored()).willReturn(List.of());

        sweep.sweep();

        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }

    @Test
    @DisplayName("one failing era does not starve the rest of the batch")
    void sweep_failureDoesNotStarveBatch() {
        var failing = new PendingEraScoringCompletion(UUID.randomUUID(), 1);
        var healthy = new PendingEraScoringCompletion(UUID.randomUUID(), 1);
        given(contextRepository.findResolvedErasNotYetScored()).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("era context not ready"))
                .given(completionChecker)
                .tryComplete(failing.gameId(), failing.eraNumber());

        sweep.sweep();

        then(completionChecker).should().tryComplete(healthy.gameId(), healthy.eraNumber());
    }
}
