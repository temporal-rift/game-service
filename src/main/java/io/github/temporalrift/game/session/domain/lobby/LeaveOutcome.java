package io.github.temporalrift.game.session.domain.lobby;

import java.util.UUID;

public sealed interface LeaveOutcome {

    record NonHostLeft() implements LeaveOutcome {}

    record HostTransferred(UUID newHostId) implements LeaveOutcome {}

    record LobbyClosed() implements LeaveOutcome {}
}
