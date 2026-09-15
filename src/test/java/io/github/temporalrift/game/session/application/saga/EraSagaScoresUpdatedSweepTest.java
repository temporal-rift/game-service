package io.github.temporalrift.game.session.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.ScoresUpdated;

@ExtendWith(MockitoExtension.class)
class EraSagaScoresUpdatedSweepTest {

    @Mock
    EraSagaScoresUpdatedInboxRepository scoresUpdatedInbox;

    @Mock
    EraSagaAdvancer eraSagaAdvancer;

    @Mock
    EraSagaRecoveryMetrics recoveryMetrics;

    @InjectMocks
    EraSagaScoresUpdatedSweep sweep;

    @Test
    @DisplayName("recorded-but-not-advanced era that actually advances is recorded as recovered")
    void sweep_retriesPendingEras() {
        var pending = scoresUpdated(UUID.randomUUID(), 2);
        given(scoresUpdatedInbox.findRecordedButNotAdvanced()).willReturn(List.of(pending));
        given(eraSagaAdvancer.handleScoresUpdated(pending.gameId(), pending)).willReturn(true);

        sweep.sweep();

        then(eraSagaAdvancer).should().handleScoresUpdated(pending.gameId(), pending);
        then(recoveryMetrics).should().recordScoresUpdatedRecovery();
    }

    @Test
    @DisplayName("nothing pending — advancer and metric untouched")
    void sweep_nothingPending_noRetry() {
        given(scoresUpdatedInbox.findRecordedButNotAdvanced()).willReturn(List.of());

        sweep.sweep();

        then(eraSagaAdvancer).should(never()).handleScoresUpdated(any(), any());
        then(recoveryMetrics).should(never()).recordScoresUpdatedRecovery();
    }

    @Test
    @DisplayName("a swept item that turns out to be a no-op is not counted as a recovery")
    void sweep_advancerNoOps_metricNotIncremented() {
        // A concurrent sweep pass or a redelivered ScoresUpdated can claim this same era first, so
        // handleScoresUpdated returns normally without throwing but reports no real transition.
        var pending = scoresUpdated(UUID.randomUUID(), 2);
        given(scoresUpdatedInbox.findRecordedButNotAdvanced()).willReturn(List.of(pending));
        given(eraSagaAdvancer.handleScoresUpdated(pending.gameId(), pending)).willReturn(false);

        sweep.sweep();

        then(recoveryMetrics).should(never()).recordScoresUpdatedRecovery();
    }

    @Test
    @DisplayName("one failing era does not starve the rest of the batch, and only the recovered era is metered")
    void sweep_failureDoesNotStarveBatch() {
        var failing = scoresUpdated(UUID.randomUUID(), 1);
        var healthy = scoresUpdated(UUID.randomUUID(), 1);
        given(scoresUpdatedInbox.findRecordedButNotAdvanced()).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("saga not found"))
                .given(eraSagaAdvancer)
                .handleScoresUpdated(eq(failing.gameId()), any());
        given(eraSagaAdvancer.handleScoresUpdated(healthy.gameId(), healthy)).willReturn(true);

        sweep.sweep();

        then(eraSagaAdvancer).should().handleScoresUpdated(healthy.gameId(), healthy);
        then(recoveryMetrics).should().recordScoresUpdatedRecovery();
    }

    private ScoresUpdated scoresUpdated(UUID gameId, int eraNumber) {
        return new ScoresUpdated(
                gameId,
                eraNumber,
                List.of(new ScoresUpdated.ScoreUpdate(UUID.randomUUID(), Faction.PROPHETS, 2, "bonus", 10)));
    }
}
