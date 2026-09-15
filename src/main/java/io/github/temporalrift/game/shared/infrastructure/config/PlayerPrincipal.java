package io.github.temporalrift.game.shared.infrastructure.config;

import java.io.Serializable;
import java.util.UUID;

public record PlayerPrincipal(UUID playerId) implements Serializable {}
