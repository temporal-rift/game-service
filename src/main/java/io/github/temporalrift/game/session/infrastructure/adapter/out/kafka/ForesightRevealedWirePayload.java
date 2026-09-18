package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Bridge until temporal-rift/apis#82 lands: replace with the generated session-event 2.1.0 type on
// adoption. The JSON shape here is byte-identical to that contract so the swap changes no behavior.
public record ForesightRevealedWirePayload(
        @NotNull UUID gameId,
        @Min(1) int eraNumber,
        @NotNull UUID playerId,
        @Min(1) int nextEraNumber,
        @NotNull List<@Valid RevealedEvent> revealedEvents,
        String emptyReason) {

    public record RevealedEvent(
            @NotNull UUID catalogEventId,
            @NotNull String title,
            @NotNull List<@Valid RevealedOutcome> outcomes) {}

    public record RevealedOutcome(
            @NotNull UUID catalogOutcomeId, @NotNull String description) {}
}
