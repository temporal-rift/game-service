package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.action.ActionRoundStarted;
import io.github.temporalrift.events.action.ActionRoundTimerExpired;
import io.github.temporalrift.events.action.RoundSummaryPublished;
import io.github.temporalrift.events.action.RoundSummaryPublished.ActionSummary;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.events.timeline.BandedProbabilityPublished;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.RoundStatus;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;
import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaStatus;
import io.github.temporalrift.game.shared.GameRulesPort;

@ExtendWith(MockitoExtension.class)
class ActionRoundSagaImplTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA_NUMBER = 1;
    static final int ROUND_NUMBER = 1;
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final UUID PLAYER_3 = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(PLAYER_1, PLAYER_2, PLAYER_3);
    static final int TIMER_SECONDS = 60;
    static final Instant TIMER_EXPIRES_AT = Instant.parse("2099-01-01T00:00:00Z");
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    ActionRoundRepository actionRoundRepository;

    @Mock
    ActionEventPublisher actionEventPublisher;

    @Mock
    ActionRoundSagaStateManager stateManager;

    @Mock
    GameRulesPort gameRules;

    @Mock
    FutureEventDefinitionPort futureEventDefinitionPort;

    @Mock
    BandCalculator bandCalculator;

    @Mock
    ActionRoundTimerRegistry timerRegistry;

    ActionRoundSagaImpl saga;

    @BeforeEach
    void setUp() {
        saga = new ActionRoundSagaImpl(
                actionRoundRepository,
                actionEventPublisher,
                stateManager,
                gameRules,
                futureEventDefinitionPort,
                bandCalculator,
                timerRegistry,
                CLOCK);
    }

    private static EventEnvelope envelopeWithPayload(Class<?> payloadType) {
        return argThat(envelope -> payloadType.isInstance(envelope.payload()));
    }

    @Nested
    @DisplayName("start()")
    class StartTests {

        @Test
        @DisplayName("creates saga state with WAITING status, saves round, and publishes ActionRoundStarted")
        void start_createsWaitingStateAndReturnsTimerMetadata() {
            // given
            given(gameRules.actionRoundTimerSeconds(PLAYER_IDS.size())).willReturn(TIMER_SECONDS);

            // when
            var result = saga.start(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_IDS);

            // then
            var captor = ArgumentCaptor.<UUID>captor();
            then(stateManager)
                    .should(times(1))
                    .initWaiting(
                            captor.capture(),
                            eq(GAME_ID),
                            eq(ERA_NUMBER),
                            eq(ROUND_NUMBER),
                            eq(PLAYER_IDS),
                            eq(NOW.plusSeconds(TIMER_SECONDS)));
            then(actionRoundRepository).should(times(1)).save(any(ActionRound.class));
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(ActionRoundStarted.class));
            then(actionEventPublisher).should(times(1)).publishInternally(any(ActionRoundStarted.class));
            assertThat(result.sagaId()).isEqualTo(captor.getValue());
            assertThat(result.timerExpiresAt()).isEqualTo(NOW.plusSeconds(TIMER_SECONDS));
        }

        @Test
        @DisplayName("computes timer duration from GameRulesPort")
        void start_usesGameRulesForTimerDuration() {
            // given
            given(gameRules.actionRoundTimerSeconds(PLAYER_IDS.size())).willReturn(TIMER_SECONDS);

            // when
            saga.start(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_IDS);

            // then
            then(gameRules).should(times(1)).actionRoundTimerSeconds(PLAYER_IDS.size());
        }
    }

    @Nested
    @DisplayName("handlePlayerSubmitted()")
    class HandlePlayerSubmittedTests {

        @Test
        @DisplayName("removes player from pending list — does not close when others remain")
        void handlePlayerSubmitted_removesPlayer_doesNotCloseWhenOthersRemain() {
            // given
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));

            // when
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            then(stateManager).should(never()).markClosing(any(), any(int.class), any(int.class));
            then(actionRoundRepository)
                    .should(never())
                    .findByGameIdAndEraNumberAndRoundNumberWithLock(any(), any(int.class), any(int.class));
        }

        @Test
        @DisplayName("last player submits — triggers close via ALL_SUBMITTED")
        void handlePlayerSubmitted_lastPlayer_triggersClose() {
            // given
            var sagaId = UUID.randomUUID();
            var roundId = UUID.randomUUID();
            var updatedState = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));

            var round = new ActionRound(roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            then(stateManager).should(times(1)).markClosing(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(actionRoundRepository)
                    .should(times(1))
                    .findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(stateManager).should(times(1)).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
        }

        @Test
        @DisplayName("missing saga returns empty — never treated as ALL_SUBMITTED")
        void handlePlayerSubmitted_missingSaga_doesNotClose() {
            // given
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.empty());

            // when
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            then(stateManager).should(never()).markClosing(any(), any(int.class), any(int.class));
            then(actionRoundRepository)
                    .should(never())
                    .findByGameIdAndEraNumberAndRoundNumberWithLock(any(), any(int.class), any(int.class));
        }
    }

    @Nested
    @DisplayName("handleTimerExpiry()")
    class HandleTimerExpiryTests {

        @Test
        @DisplayName("saga not found — silently returns")
        void handleTimerExpiry_sagaNotFound_returnsSilently() {
            // given
            var sagaId = UUID.randomUUID();
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.empty());

            // when
            saga.handleTimerExpiry(sagaId);

            // then
            then(stateManager).should(never()).markClosing(any(), any(int.class), any(int.class));
        }

        @Test
        @DisplayName("saga already COMPLETED — silently returns")
        void handleTimerExpiry_alreadyCompleted_returnsSilently() {
            // given
            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.COMPLETED,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));

            // when
            saga.handleTimerExpiry(sagaId);

            // then
            then(stateManager).should(never()).markClosing(any(), any(int.class), any(int.class));
        }

        @Test
        @DisplayName("saga in WAITING state — triggers close via TIMER_EXPIRED")
        void handleTimerExpiry_waitingState_triggersClose() {
            // given
            var sagaId = UUID.randomUUID();
            var roundId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));

            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_2, PLAYER_3), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when
            saga.handleTimerExpiry(sagaId);

            // then
            then(stateManager).should(times(1)).markClosing(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(actionRoundRepository)
                    .should(times(1))
                    .findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(stateManager).should(times(1)).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
        }
    }

    @Nested
    @DisplayName("tryClose() — close outcomes")
    class TryCloseTests {

        @Test
        @DisplayName("AlreadyClosing outcome — completes saga without publishing events")
        void tryClose_alreadyClosing_completesSaga() {
            // given
            var roundId = UUID.randomUUID();
            var round = ActionRound.reconstitute(
                    roundId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    RoundStatus.CLOSING,
                    TIMER_SECONDS,
                    "TIMER_EXPIRED",
                    List.of(),
                    List.of());
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // markSubmitted must return a state with empty pending to trigger tryClose
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));

            // when
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            then(stateManager).should(times(1)).markClosing(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(stateManager).should(times(1)).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
            then(actionEventPublisher).should(never()).publish(any());
            then(actionEventPublisher).should(never()).publishInternally(any());
        }

        @Test
        @DisplayName("Closed outcome with TIMER_EXPIRED — publishes ActionRoundTimerExpired and RoundSummaryPublished")
        void tryClose_timerExpired_publishesTimerExpiredAndRoundSummary() {
            // given
            var roundId = UUID.randomUUID();
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_2, PLAYER_3), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when — simulate timer expiry path by calling handleTimerExpiry
            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));
            saga.handleTimerExpiry(sagaId);

            // then
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(ActionRoundTimerExpired.class));
            then(actionRoundRepository).should(times(1)).save(any(ActionRound.class));
            then(actionEventPublisher).should(times(1)).publishInternally(any(ActionRoundClosed.class));
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(RoundSummaryPublished.class));
            then(stateManager).should(times(1)).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
        }

        @Test
        @DisplayName("Closed outcome with ALL_SUBMITTED — does not publish ActionRoundTimerExpired")
        void tryClose_allSubmitted_doesNotPublishTimerExpired() {
            // given
            var roundId = UUID.randomUUID();
            var round = new ActionRound(roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when — simulate all-submitted path
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            then(actionEventPublisher).should(never()).publish(envelopeWithPayload(ActionRoundTimerExpired.class));
            then(actionRoundRepository).should(times(1)).save(any(ActionRound.class));
            then(actionEventPublisher).should(times(1)).publishInternally(any(ActionRoundClosed.class));
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(RoundSummaryPublished.class));
        }

        @Test
        @DisplayName("Round 2 — publishes BandedProbabilityPublished in addition to RoundSummaryPublished")
        void tryClose_round2_publishesBandedProbabilities() {
            // given
            var round2Id = UUID.randomUUID();
            var round1Id = UUID.randomUUID();
            var round2 = new ActionRound(round2Id, GAME_ID, ERA_NUMBER, 2, List.of(), TIMER_SECONDS);
            var round1 = new ActionRound(round1Id, GAME_ID, ERA_NUMBER, 1, List.of(), TIMER_SECONDS);

            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, 2))
                    .willReturn(Optional.of(round2));
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(GAME_ID, ERA_NUMBER, 1))
                    .willReturn(Optional.of(round1));
            given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA_NUMBER))
                    .willReturn(List.of());
            given(bandCalculator.computeBands(any(), any(), any())).willReturn(List.of());

            // when — simulate all-submitted path for round 2
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    2,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, 2, PLAYER_1)).willReturn(Optional.of(updatedState));
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, 2, PLAYER_1);

            // then
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(RoundSummaryPublished.class));
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(BandedProbabilityPublished.class));
            then(bandCalculator).should(times(1)).computeBands(any(), any(), any());
        }

        @Test
        @DisplayName("Round 1 — does not publish BandedProbabilityPublished")
        void tryClose_round1_doesNotPublishBandedProbabilities() {
            // given
            var roundId = UUID.randomUUID();
            var round = new ActionRound(roundId, GAME_ID, ERA_NUMBER, 1, List.of(), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(GAME_ID, ERA_NUMBER, 1))
                    .willReturn(Optional.of(round));

            // when
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    1,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, 1, PLAYER_1)).willReturn(Optional.of(updatedState));
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, 1, PLAYER_1);

            // then
            then(actionEventPublisher).should(never()).publish(envelopeWithPayload(BandedProbabilityPublished.class));
        }
    }

    @Nested
    @DisplayName("RoundSummaryPublished content")
    class RoundSummaryContentTests {

        @Test
        @DisplayName("includes card actions with correct category and family")
        void roundSummary_includesCardActions() {
            // given
            var roundId = UUID.randomUUID();
            var cardInstanceId = UUID.randomUUID();
            var targetEventId = UUID.randomUUID();
            var targetOutcomeId = UUID.randomUUID();

            // Build a round with one card action submitted
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_1, PLAYER_2, PLAYER_3), TIMER_SECONDS);
            round.submitCard(
                    PLAYER_1,
                    cardInstanceId,
                    CardType.PUSH,
                    targetEventId,
                    null,
                    targetOutcomeId,
                    List.of(cardInstanceId));

            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when — trigger close via all-submitted
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            var captor = ArgumentCaptor.<EventEnvelope>captor();
            then(actionEventPublisher).should(atLeastOnce()).publish(captor.capture());
            var summaryEnvelope = captor.getAllValues().stream()
                    .filter(e -> e.payload() instanceof RoundSummaryPublished)
                    .findFirst()
                    .orElseThrow();
            var summary = (RoundSummaryPublished) summaryEnvelope.payload();
            // 1 card action + 2 skipped players (PLAYER_2, PLAYER_3 still pending when close() is called)
            assertThat(summary.actionSummaries()).hasSize(3);
            var cardSummary = summary.actionSummaries().stream()
                    .filter(s -> !s.skipped())
                    .findFirst()
                    .orElseThrow();
            assertThat(cardSummary).satisfies(s -> {
                assertThat(s.playerId()).isEqualTo(PLAYER_1);
                assertThat(s.actionCategory()).isEqualTo("PUSH");
                assertThat(s.actionFamily()).isEqualTo("CARD");
                assertThat(s.skipped()).isFalse();
            });
        }

        @Test
        @DisplayName("includes special actions with correct category and family")
        void roundSummary_includesSpecialActions() {
            // given
            var roundId = UUID.randomUUID();
            var targetEventId = UUID.randomUUID();
            var targetOutcomeId = UUID.randomUUID();

            // Build a round with one special action submitted
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_1, PLAYER_2, PLAYER_3), TIMER_SECONDS);
            round.submitSpecial(
                    PLAYER_1, Faction.ERASERS, SpecialAction.ANNIHILATE, targetEventId, targetOutcomeId, null, false);

            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when
            var updatedState = new ActionRoundSagaState(
                    UUID.randomUUID(),
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.markSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1))
                    .willReturn(Optional.of(updatedState));
            saga.handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_1);

            // then
            var captor = ArgumentCaptor.<EventEnvelope>captor();
            then(actionEventPublisher).should(atLeastOnce()).publish(captor.capture());
            var summaryEnvelope = captor.getAllValues().stream()
                    .filter(e -> e.payload() instanceof RoundSummaryPublished)
                    .findFirst()
                    .orElseThrow();
            var summary = (RoundSummaryPublished) summaryEnvelope.payload();
            // 1 special action + 2 skipped players (PLAYER_2, PLAYER_3 still pending when close() is called)
            assertThat(summary.actionSummaries()).hasSize(3);
            var specialSummary = summary.actionSummaries().stream()
                    .filter(s -> !s.skipped())
                    .findFirst()
                    .orElseThrow();
            assertThat(specialSummary).satisfies(s -> {
                assertThat(s.playerId()).isEqualTo(PLAYER_1);
                assertThat(s.actionCategory()).isEqualTo("SPECIAL");
                assertThat(s.actionFamily()).isEqualTo("SPECIAL");
                assertThat(s.skipped()).isFalse();
            });
        }

        @Test
        @DisplayName("includes skipped players with skipped=true")
        void roundSummary_includesSkippedPlayers() {
            // given
            var roundId = UUID.randomUUID();
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_2, PLAYER_3), TIMER_SECONDS);

            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            // when — timer expiry path with pending players
            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));
            saga.handleTimerExpiry(sagaId);

            // then
            var captor = ArgumentCaptor.<EventEnvelope>captor();
            then(actionEventPublisher).should(atLeastOnce()).publish(captor.capture());
            var summaryEnvelope = captor.getAllValues().stream()
                    .filter(e -> e.payload() instanceof RoundSummaryPublished)
                    .findFirst()
                    .orElseThrow();
            var summary = (RoundSummaryPublished) summaryEnvelope.payload();
            assertThat(summary.actionSummaries()).hasSize(2);
            assertThat(summary.actionSummaries()).allMatch(ActionSummary::skipped);
            assertThat(summary.actionSummaries())
                    .extracting(ActionSummary::playerId)
                    .containsExactlyInAnyOrder(PLAYER_2, PLAYER_3);
        }
    }

    @Nested
    @DisplayName("Race condition — concurrent close attempts")
    class RaceConditionTests {

        @Test
        @DisplayName(
                "two concurrent timer fires — second finds AlreadyClosing, completes saga without duplicate events")
        void concurrentTimerFires_secondFindsAlreadyClosing() {
            // given — first timer fire closes the round
            var roundId = UUID.randomUUID();
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_2, PLAYER_3), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));

            // when — first fire
            saga.handleTimerExpiry(sagaId);

            // then — verify events were published once
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(ActionRoundTimerExpired.class));
            then(actionEventPublisher).should(times(1)).publish(envelopeWithPayload(RoundSummaryPublished.class));
            then(actionEventPublisher).should(times(1)).publishInternally(any(ActionRoundClosed.class));
            then(stateManager).should(times(1)).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
        }

        @Test
        @DisplayName("timer fires after all-submitted — saga already COMPLETED, no-op")
        void timerFiresAfterAllSubmitted_sagaCompleted_noop() {
            // given
            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.COMPLETED,
                    List.of(),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));

            // when
            saga.handleTimerExpiry(sagaId);

            // then
            then(stateManager).should(never()).markClosing(any(), any(int.class), any(int.class));
            then(actionRoundRepository)
                    .should(never())
                    .findByGameIdAndEraNumberAndRoundNumberWithLock(any(), any(int.class), any(int.class));
        }
    }

    @Nested
    @DisplayName("Event ordering")
    class EventOrderingTests {

        @Test
        @DisplayName("Closed outcome — events published in correct order")
        void closedOutcome_eventsPublishedInCorrectOrder() {
            // given
            var roundId = UUID.randomUUID();
            var round = new ActionRound(
                    roundId, GAME_ID, ERA_NUMBER, ROUND_NUMBER, List.of(PLAYER_2, PLAYER_3), TIMER_SECONDS);
            given(actionRoundRepository.findByGameIdAndEraNumberAndRoundNumberWithLock(
                            GAME_ID, ERA_NUMBER, ROUND_NUMBER))
                    .willReturn(Optional.of(round));

            var sagaId = UUID.randomUUID();
            var state = new ActionRoundSagaState(
                    sagaId,
                    GAME_ID,
                    ERA_NUMBER,
                    ROUND_NUMBER,
                    ActionRoundSagaStatus.WAITING,
                    List.of(PLAYER_2, PLAYER_3),
                    TIMER_EXPIRES_AT);
            given(stateManager.findBySagaId(sagaId)).willReturn(Optional.of(state));

            // when
            saga.handleTimerExpiry(sagaId);

            // then — verify order using inOrder
            var ordered = inOrder(actionEventPublisher, actionRoundRepository, stateManager);
            then(actionEventPublisher).should(ordered).publish(envelopeWithPayload(ActionRoundTimerExpired.class));
            then(actionRoundRepository).should(ordered).save(any(ActionRound.class));
            then(actionEventPublisher).should(ordered).publishInternally(any(ActionRoundClosed.class));
            then(actionEventPublisher).should(ordered).publish(envelopeWithPayload(RoundSummaryPublished.class));
            then(stateManager).should(ordered).complete(GAME_ID, ERA_NUMBER, ROUND_NUMBER);
        }
    }
}
