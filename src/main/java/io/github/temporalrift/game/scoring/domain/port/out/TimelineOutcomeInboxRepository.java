package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;

public interface TimelineOutcomeInboxRepository {

    void save(OutcomeApplied outcome);

    List<OutcomeApplied> findByGameIdAndEraNumber(UUID gameId, int eraNumber);
}
