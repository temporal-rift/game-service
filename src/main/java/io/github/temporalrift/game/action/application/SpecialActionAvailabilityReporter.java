package io.github.temporalrift.game.action.application;

import java.time.Clock;
import java.util.Collection;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy.PlayerUsage;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionAvailabilityPolicy.RoundRules;
import io.github.temporalrift.game.action.domain.actionround.SpecialActionRoundWindows;
import io.github.temporalrift.game.action.domain.event.SpecialActionAvailabilityReported;
import io.github.temporalrift.game.action.domain.event.SpecialActionAvailabilityReported.SpecialAvailability;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionEventPublisher;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.action.domain.port.out.SealGameUsageRepository;
import io.github.temporalrift.game.action.domain.port.out.SpecialActionEraUsageRepository;
import io.github.temporalrift.game.action.domain.specialactionerausage.SpecialActionEraUsage;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

/**
 * Tells each active player, as an action round opens, which of their faction specials {@code submitAction}
 * would reject in that round for a reason independent of the target. Runs inside the transaction that opens the
 * round, so the report is committed with the round and before the round accepts a submission; a repeated open of
 * the same round is rejected by the round's uniqueness and rolls the reports back with it.
 */
@Component
@ConditionalOnBean({PlayerStateRepository.class, SpecialActionEraUsageRepository.class})
public class SpecialActionAvailabilityReporter {

    private final PlayerStateRepository playerStateRepository;
    private final SpecialActionEraUsageRepository eraUsageRepository;
    private final SealGameUsageRepository sealGameUsageRepository;
    private final ActionEventPublisher actionEventPublisher;
    private final GameRulesPort gameRules;
    private final Clock clock;

    SpecialActionAvailabilityReporter(
            PlayerStateRepository playerStateRepository,
            SpecialActionEraUsageRepository eraUsageRepository,
            SealGameUsageRepository sealGameUsageRepository,
            ActionEventPublisher actionEventPublisher,
            GameRulesPort gameRules,
            Clock clock) {
        this.playerStateRepository = playerStateRepository;
        this.eraUsageRepository = eraUsageRepository;
        this.sealGameUsageRepository = sealGameUsageRepository;
        this.actionEventPublisher = actionEventPublisher;
        this.gameRules = gameRules;
        this.clock = clock;
    }

    /** Publishes one report per player in {@code activePlayerIds} who has a faction. */
    public void report(UUID gameId, int eraNumber, int roundNumber, Collection<UUID> activePlayerIds) {
        var rules = new RoundRules(
                eraNumber,
                roundNumber,
                gameRules.maxEras(),
                new SpecialActionRoundWindows(gameRules.annihilateEarliestRound(), gameRules.rewriteLatestRound()),
                gameRules.onceEraBudgetedSpecials(),
                gameRules.sealMaxUsesPerGame());
        for (var playerState : playerStateRepository.findAllByGameId(gameId)) {
            if (playerState.faction() == null || !activePlayerIds.contains(playerState.playerId())) {
                continue;
            }
            publish(gameId, eraNumber, roundNumber, playerState, rules);
        }
    }

    private void publish(UUID gameId, int eraNumber, int roundNumber, PlayerState playerState, RoundRules rules) {
        var claimed = eraUsageRepository
                .findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerState.playerId())
                .map(SpecialActionEraUsage::claimedSpecials)
                .orElseGet(java.util.Set::of);
        var usage = new PlayerUsage(
                playerState.isJammed(),
                claimed,
                sealGameUsageRepository.countAcceptedSeals(gameId, playerState.playerId()));
        var specials = SpecialActionAvailabilityPolicy.evaluate(playerState.faction(), rules, usage).stream()
                .map(entry ->
                        new SpecialAvailability(entry.specialAction(), entry.available(), entry.unavailableReason()))
                .toList();
        actionEventPublisher.publish(DomainEventEnvelope.create(
                playerState.id(),
                PlayerState.AGGREGATE_TYPE,
                gameId,
                DomainEventEnvelope.SCHEMA_VERSION_V1,
                new SpecialActionAvailabilityReported(gameId, eraNumber, roundNumber, playerState.playerId(), specials),
                clock));
    }
}
