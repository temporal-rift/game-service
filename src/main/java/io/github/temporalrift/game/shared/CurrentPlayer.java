package io.github.temporalrift.game.shared;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves the authenticated player identity for generated controller method signatures. */
public final class CurrentPlayer {

    private CurrentPlayer() {}

    public static UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlayerPrincipal(var playerId)) {
            return playerId;
        }
        throw new AccessDeniedException("No authenticated player in security context");
    }
}
