package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.FactionObjectiveQuery;
import io.github.temporalrift.game.session.domain.port.out.SessionFactionObjectivePort;

@Component
class SessionFactionObjectiveAdapter implements SessionFactionObjectivePort {

    private final FactionObjectiveQuery factionObjectiveQuery;

    SessionFactionObjectiveAdapter(FactionObjectiveQuery factionObjectiveQuery) {
        this.factionObjectiveQuery = factionObjectiveQuery;
    }

    @Override
    public List<ObjectiveProgress> evaluate(UUID gameId, int eraNumber) {
        return factionObjectiveQuery.evaluate(gameId, eraNumber).stream()
                .map(progress -> new ObjectiveProgress(
                        progress.playerId(),
                        progress.faction(),
                        progress.progressCount(),
                        progress.threshold(),
                        progress.objectiveMet()))
                .toList();
    }
}
