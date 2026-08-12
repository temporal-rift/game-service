package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;

import io.github.temporalrift.game.shared.ScoresUpdated;

public interface EraSagaScoresUpdatedInboxRepository {

    void record(ScoresUpdated event);

    /** @return every recorded {@code ScoresUpdated} whose era saga is still at {@code WAITING_SCORES}. */
    List<ScoresUpdated> findRecordedButNotAdvanced();
}
