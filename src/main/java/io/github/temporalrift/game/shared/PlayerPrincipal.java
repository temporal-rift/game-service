package io.github.temporalrift.game.shared;

import java.io.Serializable;
import java.util.UUID;

public record PlayerPrincipal(UUID playerId) implements Serializable {}
