package io.github.temporalrift.game.session.application.port.in;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;

/**
 * Recovers lobby membership and start state for a member.
 */
public interface GetLobbyUseCase {

    Result handle(Query query);

    record Query(UUID lobbyId, UUID callerPlayerId) {}

    record MemberSummary(UUID playerId, String playerName, boolean isHost) {}

    record Result(UUID lobbyId, UUID gameId, UUID hostPlayerId, LobbyStatus status, List<MemberSummary> members) {}
}
