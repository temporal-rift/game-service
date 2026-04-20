package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class PlayerAuthenticationConverterTest {

    @Mock
    private Jwt jwt;

    @InjectMocks
    private PlayerAuthenticationConverter converter;

    @Test
    @DisplayName("Given a JWT with a missing sub claim, when converting, then InvalidBearerTokenException is thrown")
    void givenMissingSubClaim_whenConverting_thenThrowsInvalidBearerTokenException() {
        // given
        given(jwt.getSubject()).willReturn(null);

        // when / then
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName("Given a JWT with a non-UUID sub claim, when converting, then InvalidBearerTokenException is thrown")
    void givenNonUuidSubClaim_whenConverting_thenThrowsInvalidBearerTokenException() {
        // given
        given(jwt.getSubject()).willReturn("not-a-uuid");

        // when / then
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName(
            "Given valid UUID sub claim, when converting, then returns PlayerAuthenticationToken with correct playerId")
    void givenValidUuidSubClaim_whenConverting_thenReturnsTokenWithCorrectPlayerId() {
        // given
        var playerId = UUID.randomUUID();
        given(jwt.getSubject()).willReturn(playerId.toString());

        // when
        var result = converter.convert(jwt);

        // then
        assertThat(result).isInstanceOf(PlayerAuthenticationToken.class);
        assertThat(result.getPrincipal()).isInstanceOf(io.github.temporalrift.game.shared.PlayerPrincipal.class);
        assertThat(((io.github.temporalrift.game.shared.PlayerPrincipal) result.getPrincipal()).playerId())
                .isEqualTo(playerId);
    }
}
