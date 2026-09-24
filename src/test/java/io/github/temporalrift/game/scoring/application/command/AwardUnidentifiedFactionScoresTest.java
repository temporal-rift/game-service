package io.github.temporalrift.game.scoring.application.command;

import static io.github.temporalrift.game.scoring.ScoreRulesTestValues.pointsDelta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.ScoreRulesTestValues;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EndGameScoreFactRepository;
import io.github.temporalrift.game.scoring.domain.port.out.FactionIdentificationRepository;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.shared.domain.event.FactionRevealed;
import io.github.temporalrift.game.shared.domain.model.Faction;

@ExtendWith(MockitoExtension.class)
class AwardUnidentifiedFactionScoresTest {

    @Mock
    PlayerScoreRepository playerScoreRepository;

    @Mock
    FactionIdentificationRepository factionIdentificationRepository;

    @Mock
    EndGameScoreFactRepository endGameScoreFactRepository;

    AwardUnidentifiedFactionScores handler;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        handler = new AwardUnidentifiedFactionScores(
                playerScoreRepository,
                factionIdentificationRepository,
                endGameScoreFactRepository,
                ScoreRulesTestValues::pointsDelta);
    }

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
        existing.apply(1, ScoreReason.SECRET_OUTCOME_WON, pointsDelta(ScoreReason.SECRET_OUTCOME_WON));
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of(existing));
        given(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(gameId, playerId))
                .willReturn(false);
        given(endGameScoreFactRepository.claim(gameId, playerId, ScoreReason.FACTION_UNIDENTIFIED))
                .willReturn(true);

        handler.award(reveal(gameId, playerId, Faction.REVISIONISTS));

        then(playerScoreRepository).should().saveAll(List.of(existing));
        assertThat(existing.totalScore()).isEqualTo(10);
    }

    @Test
    void award_threePlayersWithoutIdentifications_awardsOnlyTheRevisionist() {
        var gameId = UUID.randomUUID();
        var revisionist = UUID.randomUUID();
        givenIdentified(gameId, Set.of());

        handler.award(reveal(
                gameId,
                Map.of(
                        revisionist,
                        Faction.REVISIONISTS,
                        UUID.randomUUID(),
                        Faction.ERASERS,
                        UUID.randomUUID(),
                        Faction.PROPHETS)));

        assertThat(savedScores()).singleElement().satisfies(score -> {
            assertThat(score.playerId()).isEqualTo(revisionist);
            assertThat(score.totalScore()).isEqualTo(pointsDelta(ScoreReason.FACTION_UNIDENTIFIED));
        });
    }

    @Test
    void award_threePlayersWithoutRevisionist_awardsNothingEvenWhenOthersWereIdentified() {
        var gameId = UUID.randomUUID();
        var eraser = UUID.randomUUID();
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of());

        handler.award(reveal(
                gameId,
                Map.of(
                        eraser,
                        Faction.ERASERS,
                        UUID.randomUUID(),
                        Faction.WEAVERS,
                        UUID.randomUUID(),
                        Faction.ACTIVISTS)));

        then(factionIdentificationRepository).shouldHaveNoInteractions();
        then(endGameScoreFactRepository).shouldHaveNoInteractions();
        then(playerScoreRepository).should(never()).saveAll(any());
    }

    @Test
    void award_revisionistIdentifiedByExposeAlongsideActivist_receivesNoBonus() {
        var gameId = UUID.randomUUID();
        var revisionist = UUID.randomUUID();
        givenIdentified(gameId, Set.of(revisionist));

        handler.award(reveal(
                gameId,
                Map.of(
                        revisionist,
                        Faction.REVISIONISTS,
                        UUID.randomUUID(),
                        Faction.ACTIVISTS,
                        UUID.randomUUID(),
                        Faction.PROPHETS)));

        then(endGameScoreFactRepository).shouldHaveNoInteractions();
        then(playerScoreRepository).should(never()).saveAll(any());
    }

    @Test
    void award_identifiedNonRevisionistDoesNotAffectTheUnidentifiedRevisionist() {
        var gameId = UUID.randomUUID();
        var revisionist = UUID.randomUUID();
        var activist = UUID.randomUUID();
        givenIdentified(gameId, Set.of(activist));

        handler.award(reveal(
                gameId,
                Map.of(
                        revisionist,
                        Faction.REVISIONISTS,
                        activist,
                        Faction.ACTIVISTS,
                        UUID.randomUUID(),
                        Faction.ERASERS)));

        assertThat(savedScores())
                .singleElement()
                .extracting(PlayerScore::playerId)
                .isEqualTo(revisionist);
    }

    private void givenIdentified(UUID gameId, Set<UUID> identifiedPlayerIds) {
        given(playerScoreRepository.findAllByGameIdWithLock(gameId)).willReturn(List.of());
        lenient()
                .when(factionIdentificationRepository.wasIdentifiedBeforeGameEnd(eq(gameId), any()))
                .thenAnswer(invocation -> identifiedPlayerIds.contains(invocation.getArgument(1, UUID.class)));
        lenient()
                .when(endGameScoreFactRepository.claim(eq(gameId), any(), eq(ScoreReason.FACTION_UNIDENTIFIED)))
                .thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private List<PlayerScore> savedScores() {
        var scores = ArgumentCaptor.forClass(List.class);
        then(playerScoreRepository).should().saveAll(scores.capture());
        return (List<PlayerScore>) scores.getValue();
    }

    private FactionRevealed reveal(UUID gameId, Map<UUID, Faction> factions) {
        return new FactionRevealed(
                gameId,
                factions.entrySet().stream()
                        .map(entry -> new FactionRevealed.PlayerFactionResult(
                                entry.getKey(), entry.getValue().name()))
                        .toList());
    }

    private FactionRevealed reveal(UUID gameId, UUID playerId, Faction faction) {
        return new FactionRevealed(gameId, List.of(new FactionRevealed.PlayerFactionResult(playerId, faction.name())));
    }
}
