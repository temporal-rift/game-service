package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.EraEnded;
import io.github.temporalrift.events.session.EraFailed;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.session.GameEndedAbnormally;
import io.github.temporalrift.events.session.TimelineStabilized;
import io.github.temporalrift.events.session.WinConditionMet;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.timeline.ResolutionStarted;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRulesPort;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;

@ExtendWith(MockitoExtension.class)
class EraSagaAdvancerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID LOBBY_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(PLAYER_1, PLAYER_2);
    static final int MAX_ERAS = 5;
    static final int WIN_THRESHOLD = 20;

    @Mock
    EraSagaRepository eraSagaRepository;

    @Mock
    GameRepository gameRepository;

    @Mock
    SessionEventPublisher eventPublisher;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    GameRulesPort gameRules;

    @InjectMocks
    EraSagaAdvancer advancer;

    // ─── handleRoundClosed ───────────────────────────────────────────────────

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    @Test
    @DisplayName("round 1 closed in WAITING_ROUND_1 — advances to WAITING_ROUND_2, no ResolutionStarted")
    void handleRoundClosed_round1_advancesToWaitingRound2() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_ROUND_1, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        var arc = new ActionRoundClosed(GAME_ID, 1, 1, "ALL_SUBMITTED", 4);

        // when
        advancer.handleRoundClosed(GAME_ID, arc);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.WAITING_ROUND_2));
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("round 2 closed in WAITING_ROUND_2 — advances to WAITING_ROUND_3, no ResolutionStarted")
    void handleRoundClosed_round2_advancesToWaitingRound3() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_ROUND_2, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        var arc = new ActionRoundClosed(GAME_ID, 1, 2, "ALL_SUBMITTED", 4);

        // when
        advancer.handleRoundClosed(GAME_ID, arc);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.WAITING_ROUND_3));
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("round 3 closed in WAITING_ROUND_3 — advances to WAITING_SCORES and publishes ResolutionStarted")
    void handleRoundClosed_round3_advancesToWaitingScoresAndPublishesResolutionStarted() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_ROUND_3, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        var arc = new ActionRoundClosed(GAME_ID, 1, 3, "ALL_SUBMITTED", 4);

        // when
        advancer.handleRoundClosed(GAME_ID, arc);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.WAITING_SCORES));
        then(eventPublisher).should().publish(envelopeWithPayload(ResolutionStarted.class));
    }

    // ─── handleScoresUpdated — win condition ─────────────────────────────────

    @Test
    @DisplayName("round 1 closed but saga in wrong state — no state change, no event")
    void handleRoundClosed_wrongState_ignored() {
        // given — saga is already WAITING_ROUND_2 but round 1 event arrives (duplicate/stale)
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_ROUND_2, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        var arc = new ActionRoundClosed(GAME_ID, 1, 1, "ALL_SUBMITTED", 4);

        // when
        advancer.handleRoundClosed(GAME_ID, arc);

        // then
        then(eraSagaRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("no saga state found — handleRoundClosed does nothing")
    void handleRoundClosed_noState_doesNothing() {
        // given
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.empty());
        var arc = new ActionRoundClosed(GAME_ID, 1, 1, "ALL_SUBMITTED", 4);

        // when
        advancer.handleRoundClosed(GAME_ID, arc);

        // then
        then(eraSagaRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    // ─── handleScoresUpdated — era progression ───────────────────────────────

    @Test
    @DisplayName("score meets threshold — saga COMPLETED and WinConditionMet published")
    void handleScoresUpdated_scoreAtThreshold_completesAndPublishesWinConditionMet() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        var updates =
                List.of(new ScoresUpdated.ScoreUpdate(PLAYER_1, Faction.PROPHETS, 5, "round-bonus", WIN_THRESHOLD));
        var su = new ScoresUpdated(GAME_ID, 1, updates);

        // when
        advancer.handleScoresUpdated(GAME_ID, su);

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.COMPLETED));
        then(eventPublisher).should().publish(envelopeWithPayload(WinConditionMet.class));
        then(eventPublisher).should(never()).publish(envelopeWithPayload(EraStarted.class));
    }

    @Test
    @DisplayName("highest scorer above threshold is chosen as winner when multiple players score high")
    void handleScoresUpdated_multipleAboveThreshold_highestScoreWins() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        var updates = List.of(
                new ScoresUpdated.ScoreUpdate(PLAYER_1, Faction.PROPHETS, 5, "bonus", 22),
                new ScoresUpdated.ScoreUpdate(PLAYER_2, Faction.WEAVERS, 3, "bonus", 25));
        var su = new ScoresUpdated(GAME_ID, 1, updates);

        var captor = org.mockito.ArgumentCaptor.<EventEnvelope>captor();

        // when
        advancer.handleScoresUpdated(GAME_ID, su);

        // then
        then(eventPublisher).should().publish(captor.capture());
        var winEvent = (WinConditionMet) captor.getValue().payload();
        assertThat(winEvent.winnerId()).isEqualTo(PLAYER_2);
        assertThat(winEvent.finalScore()).isEqualTo(25);
    }

    @Test
    @DisplayName("no winner and not final era — EraEnded and EraStarted published for next era")
    void handleScoresUpdated_noWinnerNotFinalEra_publishesEraEndedAndEraStarted() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        given(gameRules.maxEras()).willReturn(MAX_ERAS);
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        var su = noWinnerScores();

        // when
        advancer.handleScoresUpdated(GAME_ID, su);

        // then
        then(eventPublisher).should().publish(envelopeWithPayload(EraEnded.class));
        then(eventPublisher).should().publish(envelopeWithPayload(EraStarted.class));
        then(eventPublisher).should(never()).publish(envelopeWithPayload(TimelineStabilized.class));
    }

    @Test
    @DisplayName(
            "no winner and not final era — EraStarted also published as typed Spring event for internal saga trigger")
    void handleScoresUpdated_noWinnerNotFinalEra_publishesTypedEraStartedForInternalRouting() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        given(gameRules.maxEras()).willReturn(MAX_ERAS);
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), 1, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        advancer.handleScoresUpdated(GAME_ID, noWinnerScores());

        // then
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(EraStarted.class);
        var eraStarted = (EraStarted) captor.getValue();
        assertThat(eraStarted.gameId()).isEqualTo(GAME_ID);
        assertThat(eraStarted.eraNumber()).isEqualTo(2);
        assertThat(eraStarted.playerIds()).isEqualTo(PLAYER_IDS);
    }

    @Test
    @DisplayName("no winner on final era — TimelineStabilized published, no EraStarted")
    void handleScoresUpdated_noWinnerFinalEra_publishesTimelineStabilized() {
        // given
        var state = new EraSagaState(GAME_ID, MAX_ERAS, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        given(gameRules.maxEras()).willReturn(MAX_ERAS);
        // eraCounter == maxEras so endEra() sets ENDED_BY_STABILIZATION
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), MAX_ERAS, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        var su = noWinnerScores();

        // when
        advancer.handleScoresUpdated(GAME_ID, su);

        // then
        then(eventPublisher).should().publish(envelopeWithPayload(TimelineStabilized.class));
        then(eventPublisher).should(never()).publish(envelopeWithPayload(EraStarted.class));
        then(eventPublisher).should(never()).publish(envelopeWithPayload(EraEnded.class));
    }

    // ─── handleResolutionFailed ──────────────────────────────────────────────

    @Test
    @DisplayName("TimelineStabilized — Prophets and Weavers are winners, others are losers")
    void handleScoresUpdated_stabilization_prophetAndWeaverWin() {
        // given
        var player3 = UUID.randomUUID();
        var state =
                new EraSagaState(GAME_ID, MAX_ERAS, EraSagaStatus.WAITING_SCORES, List.of(PLAYER_1, PLAYER_2, player3));
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));
        given(gameRules.winScoreThreshold()).willReturn(WIN_THRESHOLD);
        given(gameRules.maxEras()).willReturn(MAX_ERAS);
        var game = Game.reconstitute(GAME_ID, LOBBY_ID, List.of(), MAX_ERAS, 0, GameStatus.IN_PROGRESS);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));

        var updates = List.of(
                new ScoresUpdated.ScoreUpdate(PLAYER_1, Faction.PROPHETS, 2, "bonus", 10),
                new ScoresUpdated.ScoreUpdate(PLAYER_2, Faction.WEAVERS, 1, "bonus", 8),
                new ScoresUpdated.ScoreUpdate(player3, Faction.ERASERS, 3, "bonus", 12));
        var su = new ScoresUpdated(GAME_ID, MAX_ERAS, updates);

        var captor = org.mockito.ArgumentCaptor.<EventEnvelope>captor();

        // when
        advancer.handleScoresUpdated(GAME_ID, su);

        // then
        then(eventPublisher).should().publish(captor.capture());
        var stabilized = (TimelineStabilized) captor.getValue().payload();
        assertThat(stabilized.winners())
                .extracting(TimelineStabilized.PlayerFactionResult::playerId)
                .containsExactlyInAnyOrder(PLAYER_1, PLAYER_2);
        assertThat(stabilized.losers())
                .extracting(TimelineStabilized.PlayerFactionResult::playerId)
                .containsExactly(player3);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saga not in WAITING_SCORES — handleScoresUpdated does nothing")
    void handleScoresUpdated_wrongStatus_ignored() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_ROUND_3, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));

        // when
        advancer.handleScoresUpdated(GAME_ID, noWinnerScores());

        // then
        then(eraSagaRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("resolution failed — saga marked FAILED, EraFailed and GameEndedAbnormally published")
    void handleResolutionFailed_inWaitingScores_marksFailedAndPublishesBothEvents() {
        // given
        var state = new EraSagaState(GAME_ID, 1, EraSagaStatus.WAITING_SCORES, PLAYER_IDS);
        given(eraSagaRepository.findByGameIdWithLock(GAME_ID)).willReturn(Optional.of(state));

        // when
        advancer.handleResolutionFailed(GAME_ID, "timeline-error");

        // then
        then(eraSagaRepository).should().save(argThat(s -> s.status() == EraSagaStatus.FAILED));
        then(eventPublisher).should().publish(envelopeWithPayload(EraFailed.class));
        then(eventPublisher).should().publish(envelopeWithPayload(GameEndedAbnormally.class));
    }

    private ScoresUpdated noWinnerScores() {
        return new ScoresUpdated(
                GAME_ID,
                1,
                List.of(
                        new ScoresUpdated.ScoreUpdate(PLAYER_1, Faction.PROPHETS, 2, "bonus", 10),
                        new ScoresUpdated.ScoreUpdate(PLAYER_2, Faction.WEAVERS, 1, "bonus", 8)));
    }
}
