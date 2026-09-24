package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContextNotFoundException;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.ParadoxCascadeScoringFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.domain.event.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.domain.event.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@ExtendWith(MockitoExtension.class)
class EraScoringContextRepositoryAdapterTest {

    @Mock
    ScoringContextPlayerJpaRepository playerJpaRepository;

    @Mock
    ScoringContextEraOutcomeExpectationJpaRepository eraOutcomeExpectationJpaRepository;

    @Mock
    ScoringContextChainFactJpaRepository chainFactJpaRepository;

    @Mock
    ScoringContextEventOutcomeJpaRepository eventOutcomeJpaRepository;

    @Mock
    ScoringContextAnnihilatedOutcomeJpaRepository annihilatedOutcomeJpaRepository;

    @Mock
    ScoringTimelineOutcomeInboxJpaRepository outcomeInboxJpaRepository;

    @Mock
    ScoringContextActionFactsReadyJpaRepository actionFactsReadyJpaRepository;

    @Mock
    ScoringContextActivistDeclarationJpaRepository activistDeclarationJpaRepository;

    @Mock
    ScoringContextActionFactJpaRepository actionFactJpaRepository;

    @Mock
    ScoringContextRevisionistActionJpaRepository revisionistActionJpaRepository;

    @Mock
    ScoringTimelineResolutionBarrierJpaRepository resolutionBarrierJpaRepository;

    @Mock
    ScoringContextFulfillmentDeclarationJpaRepository fulfillmentDeclarationJpaRepository;

    @Mock
    ScoringContextCorruptCorrelationJpaRepository corruptCorrelationJpaRepository;

    @Mock
    ScoringContextCorruptPendingConfirmationJpaRepository corruptPendingConfirmationJpaRepository;

    @Mock
    ScoringContextParadoxCascadeFactJpaRepository paradoxCascadeFactJpaRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    EraScoringContextRepositoryAdapter adapter;

    @Test
    void getRequired_assemblesPlayersAndUnconsumedChainFacts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(playerId);
        playerEntity.setFaction(Faction.WEAVERS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var chainId = UUID.randomUUID();
        var chainFactEntity = new ScoringContextChainFactJpaEntity();
        chainFactEntity.setId(UUID.randomUUID());
        chainFactEntity.setGameId(gameId);
        chainFactEntity.setPlayerId(playerId);
        chainFactEntity.setChainId(chainId);
        chainFactEntity.setReason(ScoreReason.CHAIN_COMPLETED.name());
        chainFactEntity.setEraNumber(1);
        chainFactEntity.setConsumed(false);
        given(chainFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId))
                .willReturn(List.of(chainFactEntity));

        var context = adapter.getRequired(gameId, 2);

        assertThat(context.gameId()).isEqualTo(gameId);
        assertThat(context.eraNumber()).isEqualTo(2);
        assertThat(context.players()).containsExactly(new PlayerFaction(playerId, Faction.WEAVERS));
        assertThat(context.chainFacts())
                .containsExactly(new ChainScoringFact(playerId, chainId, ScoreReason.CHAIN_COMPLETED, 1));
        assertThat(context.eventOutcomes()).isEmpty();
        assertThat(context.actionFacts()).isEmpty();
    }

    @Test
    void getRequired_marksReturnedChainFactsAsConsumed() {
        var gameId = UUID.randomUUID();
        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(UUID.randomUUID());
        playerEntity.setFaction(Faction.ERASERS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var chainFactEntity = new ScoringContextChainFactJpaEntity();
        chainFactEntity.setId(UUID.randomUUID());
        chainFactEntity.setGameId(gameId);
        chainFactEntity.setPlayerId(UUID.randomUUID());
        chainFactEntity.setChainId(UUID.randomUUID());
        chainFactEntity.setReason(ScoreReason.CHAIN_LINK_ADDED.name());
        chainFactEntity.setConsumed(false);
        given(chainFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId))
                .willReturn(List.of(chainFactEntity));

        adapter.getRequired(gameId, 1);

        assertThat(chainFactEntity.isConsumed()).isTrue();
        var captor = ArgumentCaptor.forClass(List.class);
        then(chainFactJpaRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(chainFactEntity);
    }

    @Test
    void getRequired_throwsWhenNoPlayersFound() {
        var gameId = UUID.randomUUID();
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of());

        assertThatThrownBy(() -> adapter.getRequired(gameId, 1)).isInstanceOf(EraScoringContextNotFoundException.class);

        then(chainFactJpaRepository).should(never()).findAllByGameIdAndConsumedFalseWithLock(any());
    }

    @Test
    void recordChainFact_persistsUnconsumedFactWithItsOwnEraNumber() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var chainId = UUID.randomUUID();

        adapter.recordChainFact(gameId, playerId, chainId, ScoreReason.CHAIN_BROKEN, 2);

        var captor = ArgumentCaptor.forClass(ScoringContextChainFactJpaEntity.class);
        then(chainFactJpaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGameId()).isEqualTo(gameId);
        assertThat(saved.getPlayerId()).isEqualTo(playerId);
        assertThat(saved.getChainId()).isEqualTo(chainId);
        assertThat(saved.getReason()).isEqualTo(ScoreReason.CHAIN_BROKEN.name());
        assertThat(saved.getEraNumber()).isEqualTo(2);
        assertThat(saved.isConsumed()).isFalse();
    }

    @Test
    void expectedOutcomeCount_returnsStoredValue() {
        var gameId = UUID.randomUUID();
        var entity = new ScoringContextEraOutcomeExpectationJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(gameId);
        entity.setEraNumber(3);
        entity.setExpectedOutcomeCount(3);
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 3))
                .willReturn(Optional.of(entity));

        assertThat(adapter.expectedOutcomeCount(gameId, 3)).isEqualTo(3);
    }

    @Test
    void expectedOutcomeCount_throwsWhenMissing() {
        var gameId = UUID.randomUUID();
        given(eraOutcomeExpectationJpaRepository.findByGameIdAndEraNumber(gameId, 1))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.expectedOutcomeCount(gameId, 1))
                .isInstanceOf(EraScoringContextNotFoundException.class);
    }

    @Test
    void upsertPlayerFaction_delegatesToAtomicUpsert() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        adapter.upsertPlayerFaction(gameId, playerId, Faction.ACTIVISTS);

        then(playerJpaRepository)
                .should()
                .upsert(any(UUID.class), eq(gameId), eq(playerId), eq(Faction.ACTIVISTS.name()));
    }

    @Test
    void upsertExpectedOutcomeCount_delegatesToAtomicUpsert() {
        var gameId = UUID.randomUUID();

        adapter.upsertExpectedOutcomeCount(gameId, 2, 3);

        then(eraOutcomeExpectationJpaRepository).should().upsert(any(UUID.class), eq(gameId), eq(2), eq(3));
    }

    @Test
    void saveEraResolutionCompleted_usesAtomicInsertIfAbsent() {
        var resolution = new EraResolutionCompleted(
                UUID.randomUUID(),
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        UUID.randomUUID(),
                        0,
                        EraResolutionCompleted.TerminalState.OUTCOME_APPLIED,
                        UUID.randomUUID())));
        given(objectMapper.writeValueAsString(resolution)).willReturn("{\"terminalResolutions\":[]}");

        adapter.saveEraResolutionCompleted(resolution);

        then(resolutionBarrierJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class),
                        eq(resolution.gameId()),
                        eq(resolution.eraNumber()),
                        eq("{\"terminalResolutions\":[]}"));
        then(resolutionBarrierJpaRepository).should(never()).findByGameIdAndEraNumber(any(), anyInt());
    }

    @Test
    void eraResolutionCompleted_and_requiredAppliedOutcomeCount_readTheResolutionBarrier() {
        var gameId = UUID.randomUUID();
        var resolution = new EraResolutionCompleted(
                gameId,
                2,
                List.of(
                        new EraResolutionCompleted.TerminalResolution(
                                UUID.randomUUID(),
                                0,
                                EraResolutionCompleted.TerminalState.OUTCOME_APPLIED,
                                UUID.randomUUID()),
                        new EraResolutionCompleted.TerminalResolution(
                                UUID.randomUUID(), 1, EraResolutionCompleted.TerminalState.CASCADED, null)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(resolution)));

        assertThat(adapter.eraResolutionCompleted(gameId, 2)).isTrue();
        assertThat(adapter.requiredAppliedOutcomeCount(gameId, 2)).isOne();
    }

    @Test
    void findResolvedErasNotYetScored_mapsRowsToDomain() {
        var gameId = UUID.randomUUID();
        var row = mock(ScoringTimelineResolutionBarrierJpaRepository.PendingCompletion.class);
        given(row.getGameId()).willReturn(gameId);
        given(row.getEraNumber()).willReturn(2);
        given(resolutionBarrierJpaRepository.findResolvedErasNotYetScored()).willReturn(List.of(row));

        var pending = adapter.findResolvedErasNotYetScored();

        assertThat(pending).singleElement().satisfies(entry -> {
            assertThat(entry.gameId()).isEqualTo(gameId);
            assertThat(entry.eraNumber()).isEqualTo(2);
        });
    }

    @Test
    void requiredAppliedOutcomeCount_defaultsToZeroWithoutAResolutionBarrier() {
        var gameId = UUID.randomUUID();
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.empty());

        assertThat(adapter.requiredAppliedOutcomeCount(gameId, 2)).isZero();
    }

    @Test
    void getRequired_assemblesEventOutcomeFactsFromBaselineAnnihilationsAndInbox() {
        var gameId = UUID.randomUUID();
        var eventIdWithFact = UUID.randomUUID();
        var eventIdWithoutFacts = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();

        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(UUID.randomUUID());
        playerEntity.setFaction(Faction.PROPHETS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var baselineWithFact = new ScoringContextEventOutcomeJpaEntity();
        baselineWithFact.setId(UUID.randomUUID());
        baselineWithFact.setGameId(gameId);
        baselineWithFact.setEraNumber(2);
        baselineWithFact.setEventId(eventIdWithFact);
        baselineWithFact.setStartingOutcomeCount(3);
        baselineWithFact.setWrittenOutcomeId(writtenOutcomeId);
        var baselineWithoutFacts = new ScoringContextEventOutcomeJpaEntity();
        baselineWithoutFacts.setId(UUID.randomUUID());
        baselineWithoutFacts.setGameId(gameId);
        baselineWithoutFacts.setEraNumber(2);
        baselineWithoutFacts.setEventId(eventIdWithoutFacts);
        baselineWithoutFacts.setStartingOutcomeCount(3);
        given(eventOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(baselineWithFact, baselineWithoutFacts));

        var annihilated1 = new ScoringContextAnnihilatedOutcomeJpaEntity();
        annihilated1.setId(UUID.randomUUID());
        annihilated1.setGameId(gameId);
        annihilated1.setEraNumber(2);
        annihilated1.setEventId(eventIdWithFact);
        annihilated1.setOutcomeId(UUID.randomUUID());
        annihilated1.setPlayerId(UUID.randomUUID());
        given(annihilatedOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(annihilated1));

        var inboxEntity = ScoringTimelineOutcomeInboxJpaEntity.fromDomain(
                new OutcomeApplied(gameId, 2, eventIdWithFact, winningOutcomeId, List.of()));
        given(outcomeInboxJpaRepository.findAllByGameIdAndEraNumberOrderByEventIdAsc(gameId, 2))
                .willReturn(List.of(inboxEntity));

        var context = adapter.getRequired(gameId, 2);

        assertThat(context.eventOutcomes())
                .containsExactlyInAnyOrder(
                        new EventOutcomeFact(eventIdWithFact, winningOutcomeId, writtenOutcomeId, 3, 2),
                        new EventOutcomeFact(eventIdWithoutFacts, null, null, 3, 3));
    }

    @Test
    void upsertEventOutcomeBaseline_delegatesToAtomicUpsert() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        adapter.upsertEventOutcomeBaseline(gameId, 2, eventId, 3);

        then(eventOutcomeJpaRepository).should().upsertBaseline(any(UUID.class), eq(gameId), eq(2), eq(eventId), eq(3));
    }

    @Test
    void upsertWrittenOutcome_delegatesToFirstWinsUpsert() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        adapter.upsertWrittenOutcome(gameId, 2, eventId, outcomeId, playerId);

        then(eventOutcomeJpaRepository)
                .should()
                .insertWrittenOutcomeIfFirst(
                        any(UUID.class), eq(gameId), eq(2), eq(eventId), eq(outcomeId), eq(playerId));
    }

    @Test
    void recordAnnihilatedOutcome_delegatesToIdempotentInsert() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        adapter.recordAnnihilatedOutcome(gameId, 2, eventId, outcomeId, playerId);

        then(annihilatedOutcomeJpaRepository)
                .should()
                .insertIfAbsent(any(UUID.class), eq(gameId), eq(2), eq(eventId), eq(outcomeId), eq(playerId));
    }

    @Test
    void resolveActivistDeclarations_cascadedTargetResolvesFalseWithoutWaitingForOutcome() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var declaration = new ScoringContextActivistDeclarationJpaEntity();
        declaration.setId(UUID.randomUUID());
        declaration.setGameId(gameId);
        declaration.setEraNumber(2);
        declaration.setPlayerId(playerId);
        declaration.setMode("RALLY");
        declaration.setTargetEventId(eventId);
        declaration.setTargetOutcomeId(UUID.randomUUID());
        given(activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(declaration));
        var barrier = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.CASCADED, null)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(barrier)));

        var resolutions = adapter.resolveActivistDeclarations(gameId, 2);

        assertThat(resolutions)
                .containsExactly(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved(
                        gameId, 2, playerId, false));
        assertThat(declaration.getResolutionSucceeded()).isFalse();
        then(outcomeInboxJpaRepository).should(never()).findByGameIdAndEraNumberAndEventId(any(), anyInt(), any());
        then(actionFactJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void resolveActivistDeclarations_stalledTargetResolvesFalseWithoutWaitingForOutcome() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var declaration = new ScoringContextActivistDeclarationJpaEntity();
        declaration.setId(UUID.randomUUID());
        declaration.setGameId(gameId);
        declaration.setEraNumber(2);
        declaration.setPlayerId(playerId);
        declaration.setMode("RALLY");
        declaration.setTargetEventId(eventId);
        declaration.setTargetOutcomeId(UUID.randomUUID());
        given(activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(declaration));
        var barrier = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.STALLED, null)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(barrier)));

        var resolutions = adapter.resolveActivistDeclarations(gameId, 2);

        assertThat(resolutions)
                .containsExactly(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved(
                        gameId, 2, playerId, false));
        assertThat(declaration.getResolutionSucceeded()).isFalse();
        then(outcomeInboxJpaRepository).should(never()).findByGameIdAndEraNumberAndEventId(any(), anyInt(), any());
        then(actionFactJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void resolveActivistDeclarations_carriedEventScoresOnlyTheLaterEraDeclaration() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        var first = new ScoringContextActivistDeclarationJpaEntity();
        first.setId(UUID.randomUUID());
        first.setGameId(gameId);
        first.setEraNumber(2);
        first.setPlayerId(playerId);
        first.setMode("RALLY");
        first.setTargetEventId(eventId);
        first.setTargetOutcomeId(winningOutcomeId);
        var later = new ScoringContextActivistDeclarationJpaEntity();
        later.setId(UUID.randomUUID());
        later.setGameId(gameId);
        later.setEraNumber(3);
        later.setPlayerId(playerId);
        later.setMode("RALLY");
        later.setTargetEventId(eventId);
        later.setTargetOutcomeId(winningOutcomeId);
        given(activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willAnswer(invocation -> first.getResolutionSucceeded() == null ? List.of(first) : List.of());
        given(activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, 3))
                .willAnswer(invocation -> later.getResolutionSucceeded() == null ? List.of(later) : List.of());
        var stalled = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.STALLED, null)));
        var resolved = new EraResolutionCompleted(
                gameId,
                3,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, winningOutcomeId)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(stalled)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 3))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(resolved)));
        given(outcomeInboxJpaRepository.findByGameIdAndEraNumberAndEventId(gameId, 3, eventId))
                .willReturn(Optional.of(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(
                        new OutcomeApplied(gameId, 3, eventId, winningOutcomeId, List.of()))));

        assertThat(adapter.resolveActivistDeclarations(gameId, 2))
                .containsExactly(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved(
                        gameId, 2, playerId, false));
        assertThat(adapter.resolveActivistDeclarations(gameId, 3))
                .containsExactly(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved(
                        gameId, 3, playerId, true));
        assertThat(adapter.resolveActivistDeclarations(gameId, 2)).isEmpty();
        assertThat(adapter.resolveActivistDeclarations(gameId, 3)).isEmpty();
        assertThat(first.getResolutionSucceeded()).isFalse();
        assertThat(later.getResolutionSucceeded()).isTrue();
        then(actionFactJpaRepository)
                .should(times(1))
                .insertIfAbsent(
                        any(UUID.class),
                        eq(gameId),
                        eq(3),
                        eq(playerId),
                        eq(Faction.ACTIVISTS.name()),
                        eq(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY.name()));
    }

    @Test
    void resolveActivistDeclarations_appliedWinningRallyRecordsSuccessAndItsScoringFact() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        var declaration = new ScoringContextActivistDeclarationJpaEntity();
        declaration.setId(UUID.randomUUID());
        declaration.setGameId(gameId);
        declaration.setEraNumber(2);
        declaration.setPlayerId(playerId);
        declaration.setMode("RALLY");
        declaration.setTargetEventId(eventId);
        declaration.setTargetOutcomeId(winningOutcomeId);
        given(activistDeclarationJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(declaration));
        var barrier = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, winningOutcomeId)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(barrier)));
        given(outcomeInboxJpaRepository.findByGameIdAndEraNumberAndEventId(gameId, 2, eventId))
                .willReturn(Optional.of(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(
                        new OutcomeApplied(gameId, 2, eventId, winningOutcomeId, List.of()))));

        assertThat(adapter.resolveActivistDeclarations(gameId, 2))
                .containsExactly(new io.github.temporalrift.game.shared.domain.event.ActivistDeclarationResolved(
                        gameId, 2, playerId, true));
        assertThat(declaration.getResolutionSucceeded()).isTrue();
        then(actionFactJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class),
                        eq(gameId),
                        eq(2),
                        eq(playerId),
                        eq(Faction.ACTIVISTS.name()),
                        eq(ScoreReason.DECLARED_OUTCOME_WON_WITH_RALLY.name()));
    }

    @Test
    void recordActionFact_and_upsertActivistDeclaration_delegateToIdempotentInserts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var declaration = new ActivistDeclarationRecorded(
                gameId,
                2,
                1,
                playerId,
                io.github.temporalrift.game.shared.domain.model.SpecialAction.MOMENTUM,
                UUID.randomUUID(),
                UUID.randomUUID());

        adapter.recordActionFact(gameId, 2, playerId, Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON);
        adapter.upsertActivistDeclaration(declaration);

        then(actionFactJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class),
                        eq(gameId),
                        eq(2),
                        eq(playerId),
                        eq(Faction.ACTIVISTS.name()),
                        eq(ScoreReason.DECLARED_OUTCOME_WON.name()));
        then(activistDeclarationJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class),
                        eq(gameId),
                        eq(2),
                        eq(playerId),
                        eq("MOMENTUM"),
                        eq(declaration.targetEventId()),
                        eq(declaration.targetOutcomeId()));
    }

    @Test
    void recordRevisionistAction_delegatesToIdempotentInsert() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();

        adapter.recordRevisionistAction(gameId, 2, playerId, SpecialAction.REWRITE, eventId, outcomeId);

        then(revisionistActionJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class), eq(gameId), eq(2), eq(playerId), eq("REWRITE"), eq(eventId), eq(outcomeId));
    }

    @Test
    void resolveRevisionistActions_appliedWinningRewriteRecordsSecretOutcomeFact() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var action = revisionistAction(gameId, playerId, SpecialAction.REWRITE, eventId, outcomeId);
        given(revisionistActionJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(action));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(resolutionBarrier(gameId, eventId, outcomeId)));
        given(outcomeInboxJpaRepository.findByGameIdAndEraNumberAndEventId(gameId, 2, eventId))
                .willReturn(Optional.of(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(
                        new OutcomeApplied(gameId, 2, eventId, outcomeId, List.of()))));

        adapter.resolveRevisionistActions(gameId, 2);

        assertThat(action.getResolved()).isTrue();
        then(actionFactJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class),
                        eq(gameId),
                        eq(2),
                        eq(playerId),
                        eq(Faction.REVISIONISTS.name()),
                        eq(ScoreReason.SECRET_OUTCOME_WON.name()));
    }

    @Test
    void resolveRevisionistActions_appliedNonWinningMimicResolvesWithoutScoringFact() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();
        var action = revisionistAction(gameId, playerId, SpecialAction.MIMIC, eventId, targetOutcomeId);
        given(revisionistActionJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(action));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(resolutionBarrier(gameId, eventId, winningOutcomeId)));
        given(outcomeInboxJpaRepository.findByGameIdAndEraNumberAndEventId(gameId, 2, eventId))
                .willReturn(Optional.of(ScoringTimelineOutcomeInboxJpaEntity.fromDomain(
                        new OutcomeApplied(gameId, 2, eventId, winningOutcomeId, List.of()))));

        adapter.resolveRevisionistActions(gameId, 2);

        assertThat(action.getResolved()).isTrue();
        then(actionFactJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void resolveRevisionistActions_cascadedTargetResolvesFalseWithoutWaitingForOutcome() {
        var gameId = UUID.randomUUID();
        var action =
                revisionistAction(gameId, UUID.randomUUID(), SpecialAction.MIMIC, UUID.randomUUID(), UUID.randomUUID());
        given(revisionistActionJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(action));
        var barrier = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        action.getTargetEventId(), 0, EraResolutionCompleted.TerminalState.CASCADED, null)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(barrier)));

        adapter.resolveRevisionistActions(gameId, 2);

        assertThat(action.getResolved()).isFalse();
        then(outcomeInboxJpaRepository).shouldHaveNoInteractions();
        then(actionFactJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void resolveRevisionistActions_stalledTargetResolvesFalseWithoutWaitingForOutcome() {
        var gameId = UUID.randomUUID();
        var action =
                revisionistAction(gameId, UUID.randomUUID(), SpecialAction.MIMIC, UUID.randomUUID(), UUID.randomUUID());
        given(revisionistActionJpaRepository.findAllUnresolvedWithLock(gameId, 2))
                .willReturn(List.of(action));
        var barrier = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        action.getTargetEventId(), 0, EraResolutionCompleted.TerminalState.STALLED, null)));
        given(resolutionBarrierJpaRepository.findByGameIdAndEraNumber(gameId, 2))
                .willReturn(Optional.of(ScoringTimelineResolutionBarrierJpaEntity.fromDomain(barrier)));

        adapter.resolveRevisionistActions(gameId, 2);

        assertThat(action.getResolved()).isFalse();
        then(outcomeInboxJpaRepository).shouldHaveNoInteractions();
        then(actionFactJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void actionFactsReady_delegatesToExistsById() {
        var gameId = UUID.randomUUID();
        given(actionFactsReadyJpaRepository.existsById(new GameEraKey(gameId, 2)))
                .willReturn(true);

        assertThat(adapter.actionFactsReady(gameId, 2)).isTrue();
    }

    @Test
    void markActionFactsReady_delegatesToAtomicInsertIfAbsent() {
        var gameId = UUID.randomUUID();

        adapter.markActionFactsReady(gameId, 2);

        then(actionFactsReadyJpaRepository).should().insertIfAbsent(gameId, 2);
    }

    @Test
    void recordFulfillmentDeclaration_delegatesToIdempotentInsert() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();

        adapter.recordFulfillmentDeclaration(gameId, 2, playerId, targetEventId);

        then(fulfillmentDeclarationJpaRepository)
                .should()
                .insertIfAbsent(any(UUID.class), eq(gameId), eq(2), eq(playerId), eq(targetEventId));
    }

    @Test
    void recordCorruptCorrelation_delegatesToIdempotentInsert() {
        var gameId = UUID.randomUUID();
        var correlation = new EraActionFactsFinalized.CorruptCorrelationFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());

        adapter.recordCorruptCorrelation(gameId, 2, correlation);

        then(corruptCorrelationJpaRepository)
                .should()
                .insertIfAbsent(any(UUID.class), eq(gameId), eq(2), eq(correlation));
    }

    @Test
    void confirmCorruptInversion_delegatesToUpdate() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var cardInstanceId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversion(gameId, 2, corruptingPlayerId, cardInstanceId, true))
                .willReturn(1);

        adapter.confirmCorruptInversion(gameId, 2, corruptingPlayerId, cardInstanceId, true);

        then(corruptCorrelationJpaRepository)
                .should()
                .confirmInversion(eq(gameId), eq(2), eq(corruptingPlayerId), eq(cardInstanceId), eq(true));
    }

    @Test
    void confirmCorruptInversion_noMatchingRow_doesNotThrow() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var cardInstanceId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversion(gameId, 2, corruptingPlayerId, cardInstanceId, true))
                .willReturn(0);

        assertThatCode(() -> adapter.confirmCorruptInversion(gameId, 2, corruptingPlayerId, cardInstanceId, true))
                .doesNotThrowAnyException();
    }

    @Test
    void confirmCorruptInversionForTarget_delegatesToUpdate() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversionForTarget(
                        gameId, 2, corruptingPlayerId, targetEventId, true))
                .willReturn(1);

        adapter.confirmCorruptInversionForTarget(gameId, 2, corruptingPlayerId, targetEventId, true);

        then(corruptCorrelationJpaRepository)
                .should()
                .confirmInversionForTarget(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(true));
    }

    @Test
    void confirmCorruptInversionForTarget_noMatchingRow_storesPendingConfirmation() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversionForTarget(
                        gameId, 2, corruptingPlayerId, targetEventId, false))
                .willReturn(0);

        assertThatCode(() ->
                        adapter.confirmCorruptInversionForTarget(gameId, 2, corruptingPlayerId, targetEventId, false))
                .doesNotThrowAnyException();

        // Pending-first ordering: the insert must precede the update so every interleaving with the
        // separate recordCorruptCorrelation transaction converges instead of stranding the row.
        var pendingInOrder = inOrder(corruptPendingConfirmationJpaRepository, corruptCorrelationJpaRepository);
        then(corruptPendingConfirmationJpaRepository)
                .should(pendingInOrder)
                .insertIfAbsent(
                        any(UUID.class), eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(false));
        then(corruptCorrelationJpaRepository)
                .should(pendingInOrder)
                .confirmInversionForTarget(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(false));
        then(corruptPendingConfirmationJpaRepository).should(never()).deletePending(any(), anyInt(), any(), any());
    }

    @Test
    void confirmCorruptInversionForTarget_matchingRow_appliesUpdateAndRemovesPending() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversionForTarget(
                        gameId, 2, corruptingPlayerId, targetEventId, true))
                .willReturn(1);

        adapter.confirmCorruptInversionForTarget(gameId, 2, corruptingPlayerId, targetEventId, true);

        then(corruptPendingConfirmationJpaRepository)
                .should()
                .insertIfAbsent(
                        any(UUID.class), eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(true));
        then(corruptCorrelationJpaRepository)
                .should()
                .confirmInversionForTarget(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(true));
        then(corruptPendingConfirmationJpaRepository)
                .should()
                .deletePending(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId));
    }

    @Test
    void recordCorruptCorrelation_withPendingConfirmation_mergesAndConsumesIt() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var pending = new ScoringContextCorruptPendingConfirmationJpaEntity();
        pending.setId(UUID.randomUUID());
        pending.setGameId(gameId);
        pending.setEraNumber(2);
        pending.setCorruptingPlayerId(corruptingPlayerId);
        pending.setTargetEventId(targetEventId);
        pending.setTookEffect(true);
        given(corruptPendingConfirmationJpaRepository.findByGameIdAndEraNumberAndCorruptingPlayerIdAndTargetEventId(
                        gameId, 2, corruptingPlayerId, targetEventId))
                .willReturn(Optional.of(pending));

        adapter.recordCorruptCorrelation(
                gameId,
                2,
                new EraActionFactsFinalized.CorruptCorrelationFact(
                        corruptingPlayerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        targetEventId,
                        null,
                        UUID.randomUUID()));

        then(corruptCorrelationJpaRepository)
                .should()
                .confirmInversionForTarget(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId), eq(true));
        then(corruptPendingConfirmationJpaRepository)
                .should()
                .deletePending(eq(gameId), eq(2), eq(corruptingPlayerId), eq(targetEventId));
    }

    @Test
    void corruptConfirmAndRecord_lockTheSamePlayerRow() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(corruptCorrelationJpaRepository.confirmInversionForTarget(
                        gameId, 2, corruptingPlayerId, targetEventId, true))
                .willReturn(1);
        given(corruptPendingConfirmationJpaRepository.findByGameIdAndEraNumberAndCorruptingPlayerIdAndTargetEventId(
                        gameId, 2, corruptingPlayerId, targetEventId))
                .willReturn(Optional.empty());

        adapter.confirmCorruptInversionForTarget(gameId, 2, corruptingPlayerId, targetEventId, true);
        adapter.recordCorruptCorrelation(
                gameId,
                2,
                new EraActionFactsFinalized.CorruptCorrelationFact(
                        corruptingPlayerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        targetEventId,
                        null,
                        UUID.randomUUID()));

        then(playerJpaRepository).should(times(2)).findByGameIdAndPlayerIdWithLock(eq(gameId), eq(corruptingPlayerId));
    }

    @Test
    void recordCorruptCorrelation_withoutPendingConfirmation_leavesPendingStoreAlone() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        given(corruptPendingConfirmationJpaRepository.findByGameIdAndEraNumberAndCorruptingPlayerIdAndTargetEventId(
                        gameId, 2, corruptingPlayerId, targetEventId))
                .willReturn(Optional.empty());

        adapter.recordCorruptCorrelation(
                gameId,
                2,
                new EraActionFactsFinalized.CorruptCorrelationFact(
                        corruptingPlayerId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        targetEventId,
                        null,
                        UUID.randomUUID()));

        then(corruptCorrelationJpaRepository)
                .should(never())
                .confirmInversionForTarget(any(), anyInt(), any(), any(), anyBoolean());
        then(corruptPendingConfirmationJpaRepository).should(never()).deletePending(any(), anyInt(), any(), any());
    }

    @Test
    void getRequired_assemblesAnnihilationFactsAndFulfillmentDeclarations() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var cardInstanceId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();

        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(playerId);
        playerEntity.setFaction(Faction.ERASERS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var annihilated = new ScoringContextAnnihilatedOutcomeJpaEntity();
        annihilated.setId(UUID.randomUUID());
        annihilated.setGameId(gameId);
        annihilated.setEraNumber(2);
        annihilated.setEventId(eventId);
        annihilated.setOutcomeId(outcomeId);
        annihilated.setPlayerId(playerId);
        given(annihilatedOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(annihilated));

        var declaration = new ScoringContextFulfillmentDeclarationJpaEntity();
        declaration.setId(UUID.randomUUID());
        declaration.setGameId(gameId);
        declaration.setEraNumber(2);
        declaration.setPlayerId(playerId);
        declaration.setTargetEventId(eventId);
        given(fulfillmentDeclarationJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(declaration));

        var correlation = new ScoringContextCorruptCorrelationJpaEntity();
        correlation.setId(UUID.randomUUID());
        correlation.setGameId(gameId);
        correlation.setEraNumber(2);
        correlation.setCorruptingPlayerId(playerId);
        correlation.setTargetPlayerId(targetPlayerId);
        correlation.setCardInstanceId(cardInstanceId);
        correlation.setTargetEventId(eventId);
        correlation.setSourceOutcomeId(sourceOutcomeId);
        correlation.setTargetOutcomeId(outcomeId);
        correlation.setTookEffect(true);
        given(corruptCorrelationJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(correlation));

        var context = adapter.getRequired(gameId, 2);

        assertThat(context.annihilationFacts())
                .containsExactly(new io.github.temporalrift.game.scoring.domain.context.AnnihilationFact(
                        eventId, outcomeId, playerId));
        assertThat(context.fulfillmentDeclarations())
                .containsExactly(new io.github.temporalrift.game.scoring.domain.context.FulfillmentDeclarationFact(
                        playerId, eventId));
        assertThat(context.corruptCorrelations())
                .containsExactly(new io.github.temporalrift.game.scoring.domain.context.CorruptCorrelationFact(
                        playerId, targetPlayerId, cardInstanceId, eventId, sourceOutcomeId, outcomeId, true));
    }

    @Test
    void getRequired_carriedEventDoesNotCarryProphetWritingOrFulfillmentIntoLaterEra() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var oldWrittenOutcomeId = UUID.randomUUID();
        var newWrittenOutcomeId = UUID.randomUUID();
        var player = new ScoringContextPlayerJpaEntity();
        player.setId(UUID.randomUUID());
        player.setGameId(gameId);
        player.setPlayerId(playerId);
        player.setFaction(Faction.PROPHETS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(player));

        var oldBaseline = new ScoringContextEventOutcomeJpaEntity();
        oldBaseline.setId(UUID.randomUUID());
        oldBaseline.setGameId(gameId);
        oldBaseline.setEraNumber(2);
        oldBaseline.setEventId(eventId);
        oldBaseline.setStartingOutcomeCount(3);
        oldBaseline.setWrittenOutcomeId(oldWrittenOutcomeId);
        var carriedBaseline = new ScoringContextEventOutcomeJpaEntity();
        carriedBaseline.setId(UUID.randomUUID());
        carriedBaseline.setGameId(gameId);
        carriedBaseline.setEraNumber(3);
        carriedBaseline.setEventId(eventId);
        carriedBaseline.setStartingOutcomeCount(3);
        var freshBaseline = new ScoringContextEventOutcomeJpaEntity();
        freshBaseline.setId(UUID.randomUUID());
        freshBaseline.setGameId(gameId);
        freshBaseline.setEraNumber(4);
        freshBaseline.setEventId(eventId);
        freshBaseline.setStartingOutcomeCount(3);
        freshBaseline.setWrittenOutcomeId(newWrittenOutcomeId);
        given(eventOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 2)).willReturn(List.of(oldBaseline));
        given(eventOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 3)).willReturn(List.of(carriedBaseline));
        given(eventOutcomeJpaRepository.findAllByGameIdAndEraNumber(gameId, 4)).willReturn(List.of(freshBaseline));

        var oldDeclaration = new ScoringContextFulfillmentDeclarationJpaEntity();
        oldDeclaration.setId(UUID.randomUUID());
        oldDeclaration.setGameId(gameId);
        oldDeclaration.setEraNumber(2);
        oldDeclaration.setPlayerId(playerId);
        oldDeclaration.setTargetEventId(eventId);
        given(fulfillmentDeclarationJpaRepository.findAllByGameIdAndEraNumber(gameId, 2))
                .willReturn(List.of(oldDeclaration));

        var stalledEra = adapter.getRequired(gameId, 2);
        var carriedEra = adapter.getRequired(gameId, 3);
        var resolvingEra = adapter.getRequired(gameId, 4);

        assertThat(stalledEra.eventOutcomes())
                .singleElement()
                .satisfies(fact -> assertThat(fact.writtenOutcomeId()).isEqualTo(oldWrittenOutcomeId));
        assertThat(stalledEra.fulfillmentDeclarations())
                .containsExactly(new io.github.temporalrift.game.scoring.domain.context.FulfillmentDeclarationFact(
                        playerId, eventId));
        assertThat(carriedEra.eventOutcomes())
                .singleElement()
                .satisfies(fact -> assertThat(fact.writtenOutcomeId()).isNull());
        assertThat(carriedEra.fulfillmentDeclarations()).isEmpty();
        assertThat(resolvingEra.eventOutcomes())
                .singleElement()
                .satisfies(fact -> assertThat(fact.writtenOutcomeId()).isEqualTo(newWrittenOutcomeId));
        assertThat(resolvingEra.fulfillmentDeclarations()).isEmpty();
    }

    @Test
    void getRequired_assemblesAndConsumesParadoxCascadeFacts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var playerEntity = new ScoringContextPlayerJpaEntity();
        playerEntity.setId(UUID.randomUUID());
        playerEntity.setGameId(gameId);
        playerEntity.setPlayerId(playerId);
        playerEntity.setFaction(Faction.PROPHETS.name());
        given(playerJpaRepository.findAllByGameId(gameId)).willReturn(List.of(playerEntity));

        var paradoxId = UUID.randomUUID();
        var affectedEventId = UUID.randomUUID();
        var detonatedByPlayerIds = List.of(UUID.randomUUID());
        var factEntity = new ScoringContextParadoxCascadeFactJpaEntity();
        factEntity.setId(UUID.randomUUID());
        factEntity.setGameId(gameId);
        factEntity.setEraNumber(1);
        factEntity.setParadoxId(paradoxId);
        factEntity.setAffectedEventId(affectedEventId);
        factEntity.setDetonatedByPlayerIds(detonatedByPlayerIds);
        factEntity.setConsumed(false);
        given(paradoxCascadeFactJpaRepository.findAllByGameIdAndConsumedFalseWithLock(gameId))
                .willReturn(List.of(factEntity));

        var context = adapter.getRequired(gameId, 2);

        assertThat(context.paradoxCascadeFacts())
                .containsExactly(new ParadoxCascadeScoringFact(paradoxId, affectedEventId, detonatedByPlayerIds, 1));
        assertThat(factEntity.isConsumed()).isTrue();
        var captor = ArgumentCaptor.forClass(List.class);
        then(paradoxCascadeFactJpaRepository).should().saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(factEntity);
    }

    @Test
    void recordParadoxCascadeFact_persistsUnconsumedFact() {
        var gameId = UUID.randomUUID();
        var paradoxId = UUID.randomUUID();
        var affectedEventId = UUID.randomUUID();
        var detonatedByPlayerIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        adapter.recordParadoxCascadeFact(gameId, 3, paradoxId, affectedEventId, detonatedByPlayerIds);

        var captor = ArgumentCaptor.forClass(ScoringContextParadoxCascadeFactJpaEntity.class);
        then(paradoxCascadeFactJpaRepository).should().save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGameId()).isEqualTo(gameId);
        assertThat(saved.getEraNumber()).isEqualTo(3);
        assertThat(saved.getParadoxId()).isEqualTo(paradoxId);
        assertThat(saved.getAffectedEventId()).isEqualTo(affectedEventId);
        assertThat(saved.getDetonatedByPlayerIds()).isEqualTo(detonatedByPlayerIds);
        assertThat(saved.isConsumed()).isFalse();
    }

    private ScoringContextRevisionistActionJpaEntity revisionistAction(
            UUID gameId, UUID playerId, SpecialAction specialAction, UUID eventId, UUID outcomeId) {
        var action = new ScoringContextRevisionistActionJpaEntity();
        action.setId(UUID.randomUUID());
        action.setGameId(gameId);
        action.setEraNumber(2);
        action.setPlayerId(playerId);
        action.setAction(specialAction.name());
        action.setTargetEventId(eventId);
        action.setTargetOutcomeId(outcomeId);
        return action;
    }

    private ScoringTimelineResolutionBarrierJpaEntity resolutionBarrier(UUID gameId, UUID eventId, UUID outcomeId) {
        var resolution = new EraResolutionCompleted(
                gameId,
                2,
                List.of(new EraResolutionCompleted.TerminalResolution(
                        eventId, 0, EraResolutionCompleted.TerminalState.OUTCOME_APPLIED, outcomeId)));
        return ScoringTimelineResolutionBarrierJpaEntity.fromDomain(resolution);
    }
}
