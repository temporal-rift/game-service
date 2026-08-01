package io.github.temporalrift.game.scoring.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EndGameScoreFactRepository;
import io.github.temporalrift.game.scoring.domain.port.out.FactionIdentificationRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionRevealed;

@ExtendWith(MockitoExtension.class)
class AwardUnidentifiedFactionScoresTest {

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    FactionIdentificationRepository factionIdentificationRepository;

    @Mock
    EndGameScoreFactRepository endGameScoreFactRepository;

    @InjectMocks
    AwardUnidentifiedFactionScores handler;

    @Test
    void award_eligibleRevisionistCreatesReservedEndGameScoreEntry() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of());
        given(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .willReturn(false);
        given(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                .willReturn(true);

        handler.award(reveal(gameId, playerId, Faction.REVISIONISTS));

        var scores = ArgumentCaptor.forClass(List.class);
        then(playerScoreRepository).should().saveAll(scores.capture());
        assertThat(scores.getValue()).singleElement().satisfies(score -> {
            assertThat(((PlayerScore) score).totalScore()).isEqualTo(6);
            assertThat(((PlayerScore) score).history())
                    .singleElement()
                    .satisfies(entry -> assertThat(entry.eraNumber()).isZero());
        });
    }

    @Test
    void award_previouslyIdentifiedRevisionistDoesNotClaimOrApplyBonus() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of());
        given(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .willReturn(true);

        handler.award(reveal(gameId, playerId, Faction.REVISIONISTS));

        then(endGameScoreFactRepository).shouldHaveNoInteractions();
        then(playerScoreRepository).should().findAllByGameIdWithLock(gameId);
        then(playerScoreRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void award_duplicateFinalRevealDoesNotApplyTheBonusAgain() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var existing = new PlayerScore(UUID.randomUUID(), gameId, playerId, Faction.REVISIONISTS);
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of(existing));
        given(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .willReturn(false);
        given(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                .willReturn(false);

        handler.award(reveal(gameId, playerId, Faction.REVISIONISTS));

        assertThat(existing.totalScore()).isZero();
        then(playerScoreRepository).should().findAllByGameIdWithLock(gameId);
        then(playerScoreRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void award_eligibleRevisionistAddsTheBonusToAnExistingScore() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var existing = new PlayerScore(UUID.randomUUID(), gameId, playerId, Faction.REVISIONISTS);
        existing.apply(1, ScoreReason.SECRET_OUTCOME_WON);
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of(existing));
        given(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .willReturn(false);
        given(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                .willReturn(true);

        handler.award(reveal(gameId, playerId, Faction.REVISIONISTS));

        then(playerScoreRepository).should().saveAll(List.of(existing));
        assertThat(existing.totalScore()).isEqualTo(10);
    }

    private FactionRevealed reveal(UUID gameId, UUID playerId, Faction faction) {
        return new FactionRevealed(gameId, List.of(new FactionRevealed.PlayerFactionResult(playerId, faction.name())));
    }
}
