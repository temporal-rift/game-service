package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

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
                .containsExactly(
                        new io.github.temporalrift.game.shared.ActivistDeclarationResolved(gameId, 2, playerId, false));
        assertThat(declaration.getResolutionSucceeded()).isFalse();
        then(outcomeInboxJpaRepository).should(never()).findByGameIdAndEraNumberAndEventId(any(), anyInt(), any());
        then(actionFactJpaRepository).shouldHaveNoInteractions();
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
                .containsExactly(
                        new io.github.temporalrift.game.shared.ActivistDeclarationResolved(gameId, 2, playerId, true));
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
                io.github.temporalrift.game.shared.SpecialAction.MOMENTUM,
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
