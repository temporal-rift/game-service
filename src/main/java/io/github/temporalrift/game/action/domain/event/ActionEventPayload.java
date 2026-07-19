package io.github.temporalrift.game.action.domain.event;

/**
 * Closed set of action-module payloads published through the action Kafka/outbox port.
 *
 * <p>Adding a payload requires updating this permits list and the exhaustive publisher switch, so
 * an un-routed action event cannot reach production unnoticed.
 */
public sealed interface ActionEventPayload
        permits ActionRoundStarted,
                ActionRoundTimerExpired,
                BandedProbabilityPublished,
                CardPlayed,
                PlayerSkipped,
                RoundSummaryPublished,
                SpecialActionPlayed {}
