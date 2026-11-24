package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Normal victory for one qualifying player. A shared victory publishes one fact per qualifier, so
 * the authoritative winner set for a game is the set of {@code WinConditionMet} facts for that
 * game. Consumers aggregate by {@code gameId}; {@code EndGameSaga} stays exactly-once regardless of
 * qualifier count.
 *
 * @param winConditions every normal-victory condition the winner met, ordered {@code
 *     SCORE_THRESHOLD} before {@code FACTION_OBJECTIVE}; null for {@code LAST_PLAYER_STANDING}.
 */
public record WinConditionMet(
        UUID gameId, UUID winnerId, String faction, int finalScore, String winType, List<String> winConditions) {
    public WinConditionMet {
        winConditions = winConditions == null ? null : List.copyOf(winConditions);
    }
}
