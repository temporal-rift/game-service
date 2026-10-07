package io.github.temporalrift.game.session.domain.saga;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.lobby.SeatingPlanMismatchException;
import io.github.temporalrift.game.shared.domain.model.Faction;

public record FactionAssignment(UUID playerId, Faction faction) {

    /** Applies pre-agreed factions, which must cover exactly the lobby's players. */
    public static List<FactionAssignment> fromAgreedFactions(
            List<UUID> lobbyPlayerIds, Map<UUID, Faction> factionByPlayer) {
        if (lobbyPlayerIds.size() != factionByPlayer.size()
                || !factionByPlayer.keySet().containsAll(lobbyPlayerIds)) {
            throw new SeatingPlanMismatchException();
        }
        return lobbyPlayerIds.stream()
                .map(playerId -> new FactionAssignment(playerId, factionByPlayer.get(playerId)))
                .toList();
    }
}
