package io.github.temporalrift.game.shared.infrastructure.config;

import java.util.Collections;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import io.github.temporalrift.game.shared.PlayerPrincipal;

public class PlayerAuthenticationToken extends AbstractAuthenticationToken {

    private final PlayerPrincipal principal;

    public PlayerAuthenticationToken(PlayerPrincipal principal) {
        super(Collections.emptyList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public PlayerPrincipal getPrincipal() {
        return principal;
    }
}
