package io.github.temporalrift.game.session.domain.event;

import java.util.UUID;

public record HostTransferred(UUID lobbyId, UUID previousHostId, UUID newHostId) {}
