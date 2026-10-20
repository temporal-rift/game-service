package io.github.temporalrift.game;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import io.github.temporalrift.game.contract.ActionConcurrencyPortContracts;
import io.github.temporalrift.game.contract.ActionPortContracts;
import io.github.temporalrift.game.contract.ProcessedEventPortContract;
import io.github.temporalrift.game.contract.ScoringPortContracts;
import io.github.temporalrift.game.contract.SessionPortContracts;
import io.github.temporalrift.game.contract.SimulationPortContracts;

/** Runs every port contract against the adapters over the in-memory store. */
@InMemoryPersistenceTest
class InMemoryPortContractTest
        implements ProcessedEventPortContract,
                SessionPortContracts.LobbyPortContract,
                SessionPortContracts.GamePortContract,
                SessionPortContracts.StartGameSagaPortContract,
                SessionPortContracts.EraSagaPortContract,
                SessionPortContracts.EndGameSagaPortContract,
                SessionPortContracts.PlayerReconnectSagaPortContract,
                SessionPortContracts.EraSagaScoresUpdatedInboxPortContract,
                SessionPortContracts.ForesightRevealPortContract,
                ActionPortContracts.ParadoxResolutionPhasePortContract,
                ActionPortContracts.DeclarationPhasePortContract,
                ActionPortContracts.ReactiveOfferPortContract,
                ActionPortContracts.ActionRoundPortContract,
                ActionPortContracts.PlayerStatePortContract,
                ActionPortContracts.ActivistEraStatePortContract,
                ActionPortContracts.ActionRoundSagaPortContract,
                ActionPortContracts.SpecialActionEraUsagePortContract,
                ActionPortContracts.SealGameUsagePortContract,
                ActionPortContracts.HandSelectionPortContract,
                ActionConcurrencyPortContracts.HandSelectionConcurrencyPortContract,
                ActionConcurrencyPortContracts.DeclarationPhaseConcurrencyPortContract,
                ActionConcurrencyPortContracts.ParadoxResolutionPhaseConcurrencyPortContract,
                ActionConcurrencyPortContracts.PlayerStateConcurrencyPortContract,
                ActionConcurrencyPortContracts.ReactiveOfferConcurrencyPortContract,
                ActionConcurrencyPortContracts.ActionRoundConcurrencyPortContract,
                ActionConcurrencyPortContracts.ActionRoundSagaConcurrencyPortContract,
                ScoringPortContracts.EraScoringContextPortContract,
                ScoringPortContracts.PlayerScorePortContract,
                ScoringPortContracts.RevisionistExposurePortContract,
                ScoringPortContracts.ScoringEraCompletionPortContract,
                ScoringPortContracts.ScoringGameVisibilityPortContract,
                ScoringPortContracts.ScoringPlayerPortContract,
                ScoringPortContracts.ScoringReadPortContract,
                ScoringPortContracts.TimelineOutcomeInboxPortContract,
                SimulationPortContracts.ExecutionPortContract,
                SimulationPortContracts.ExecutionCloseContract,
                SimulationPortContracts.EntropyDecisionPortContract,
                SimulationPortContracts.ClockOperationPortContract,
                SimulationPortContracts.OutboxProbePortContract,
                SimulationPortContracts.ExecutionProbePortContract {

    @Autowired
    ApplicationContext context;

    @Override
    public ApplicationContext context() {
        return context;
    }
}
