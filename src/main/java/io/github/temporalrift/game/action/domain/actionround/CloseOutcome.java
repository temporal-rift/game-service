package io.github.temporalrift.game.action.domain.actionround;

import java.util.List;
import java.util.UUID;

public sealed interface CloseOutcome permits CloseOutcome.Closed, CloseOutcome.AlreadyClosing {

    record Closed(List<UUID> skippedPlayerIds) implements CloseOutcome {}

    record AlreadyClosing() implements CloseOutcome {}
}
