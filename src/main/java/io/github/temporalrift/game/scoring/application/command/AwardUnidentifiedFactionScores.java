package io.github.temporalrift.game.scoring.application.command;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EndGameScoreFactRepository;
import io.github.temporalrift.game.scoring.domain.port.out.FactionIdentificationRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionRevealed;

@Component
/**
 * Applies the Revisionist end-game bonus from the final faction reveal.
 *
 * <p>The eligibility check intentionally precedes the atomic ledger claim: the reveal that
 * invokes this handler is not prior identification, while a persisted earlier identification
 * disqualifies the player.
 */
public class AwardUnidentifiedFactionScores {

    private final PlayerScoreRepository playerScoreRepository;
    private final FactionIdentificationRepository factionIdentificationRepository;
    private final EndGameScoreFactRepository endGameScoreFactRepository;

    AwardUnidentifiedFactionScores(
            PlayerScoreRepository playerScoreRepository,
            FactionIdentificationRepository factionIdentificationRepository,
            EndGameScoreFactRepository endGameScoreFactRepository) {
        this.playerScoreRepository = playerScoreRepository;
        this.factionIdentificationRepository = factionIdentificationRepository;
        this.endGameScoreFactRepository = endGameScoreFactRepository;
    }

    @Transactional
    public void award(FactionRevealed reveal) {
        var scoresByPlayer = new HashMap<UUID, PlayerScore>();
        playerScoreRepository
                .findAllByGameIdWithLock(reveal.gameId())
                .forEach(score -> scoresByPlayer.put(score.playerId(), score));
        var awarded = reveal.reveals().stream()
                .filter(player -> Faction.tryParse(player.faction()).orElse(null) == Faction.REVISIONISTS)
                .filter(player ->
                        !factionIdentificationRepository.wasIdentifiedBeforeGameEnd(reveal.gameId(), player.playerId()))
                .filter(player -> endGameScoreFactRepository.claim(
                        reveal.gameId(), player.playerId(), ScoreReason.FACTION_UNIDENTIFIED))
                .map(player -> scoresByPlayer.computeIfAbsent(
                        player.playerId(),
                        playerId ->
                                new PlayerScore(UUID.randomUUID(), reveal.gameId(), playerId, Faction.REVISIONISTS)))
                .map(score -> {
                    score.applyEndGame(ScoreReason.FACTION_UNIDENTIFIED);
                    return score;
                })
                .toList();
        if (!awarded.isEmpty()) {
            playerScoreRepository.saveAll(List.copyOf(awarded));
        }
    }
}
