package io.github.temporalrift.game.shared.infrastructure.config;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.github.temporalrift.game.shared.PlayerPrincipal;

public class PlayerAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        var sub = source.getSubject();
        if (sub == null) {
            throw new InvalidBearerTokenException("Missing sub claim");
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("sub claim is not a valid UUID");
        }
        return new PlayerAuthenticationToken(new PlayerPrincipal(playerId));
    }
}
