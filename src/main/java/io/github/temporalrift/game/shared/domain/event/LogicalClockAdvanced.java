package io.github.temporalrift.game.shared.domain.event;

import java.time.Instant;

/** The isolated execution's logical clock reached {@code now}; due-time handlers run in response. */
public record LogicalClockAdvanced(Instant now) {}
