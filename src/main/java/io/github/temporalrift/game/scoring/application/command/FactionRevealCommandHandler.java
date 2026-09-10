package io.github.temporalrift.game.scoring.application.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.FactionRevealCommand;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.shared.FactionRevealed;

/** Called synchronously from {@code EndGameSagaImpl}'s own transaction, not via an async listener. */
@Service
class FactionRevealCommandHandler implements FactionRevealCommand {

    private final AwardUnidentifiedFactionScores awardUnidentifiedFactionScores;
    private final ScoringGameVisibilityRepository visibilityRepository;

    FactionRevealCommandHandler(
            AwardUnidentifiedFactionScores awardUnidentifiedFactionScores,
            ScoringGameVisibilityRepository visibilityRepository) {
        this.awardUnidentifiedFactionScores = awardUnidentifiedFactionScores;
        this.visibilityRepository = visibilityRepository;
    }

    @Override
    @Transactional
    public void reveal(FactionRevealed event) {
        awardUnidentifiedFactionScores.award(event);
        visibilityRepository.markFactionsRevealed(event.gameId());
    }
}
