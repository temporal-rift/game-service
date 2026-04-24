package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class UnauthorizedEntryPointTest {

    private final UnauthorizedEntryPoint entryPoint = new UnauthorizedEntryPoint(new ObjectMapper());

    @Test
    @DisplayName("Given an AuthenticationException, commence writes 401 with application/problem+json content type")
    void commence_unauthenticated_writes401WithProblemJson() throws IOException {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var exception = new BadCredentialsException("Full authentication is required");

        // when
        entryPoint.commence(request, response, exception);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":401");
    }
}
