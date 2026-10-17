package io.github.temporalrift.game.session.application.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.session.domain.port.out.EraSagaScoresUpdatedInboxRepository;
import io.github.temporalrift.game.shared.domain.event.ScoresUpdated;

class EraSagaEventListenerTest {

    private static final UUID GAME_ID = UUID.randomUUID();

    private final EraSaga eraSaga = mock(EraSaga.class);
    private final EraSagaAdvancer advancer = mock(EraSagaAdvancer.class);
    private final EraSagaScoresUpdatedInboxRepository inbox = mock(EraSagaScoresUpdatedInboxRepository.class);
    private final EraSagaEventListener listener = new EraSagaEventListener(eraSaga, advancer, inbox);

    @Test
    void aRecordedResolutionReplaysTheWaitingScoresOfThatEraOnly() {
        var waiting = new ScoresUpdated(GAME_ID, 1, List.of());
        var otherEra = new ScoresUpdated(GAME_ID, 2, List.of());
        var otherGame = new ScoresUpdated(UUID.randomUUID(), 1, List.of());
        given(inbox.findRecordedButNotAdvanced()).willReturn(List.of(otherGame, otherEra, waiting));

        listener.onResolutionApplied(new ResolutionAppliedApplicationEvent(GAME_ID, 1));

        then(advancer).should().handleScoresUpdated(GAME_ID, waiting);
        then(advancer).should(never()).handleScoresUpdated(any(), org.mockito.ArgumentMatchers.eq(otherEra));
        then(advancer).should(never()).handleScoresUpdated(any(), org.mockito.ArgumentMatchers.eq(otherGame));
    }
}
