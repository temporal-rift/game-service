package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.Faction;

public interface SessionFactionObjectivePort {

    List<ObjectiveProgress> evaluate(UUID gameId, int eraNumber);

    record ObjectiveProgress(UUID playerId, Faction faction, int progressCount, int threshold, boolean objectiveMet) {}
}
