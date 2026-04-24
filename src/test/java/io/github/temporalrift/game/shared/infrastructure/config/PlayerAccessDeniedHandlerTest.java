package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

class PlayerAccessDeniedHandlerTest {

    private final PlayerAccessDeniedHandler handler = new PlayerAccessDeniedHandler(new ObjectMapper());

    @Test
    @DisplayName("Given an AccessDeniedException, handle writes 403 with application/problem+json content type")
    void handle_accessDenied_writes403WithProblemJson() throws IOException {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var exception = new AccessDeniedException("Access is denied");

        // when
        handler.handle(request, response, exception);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":403");
    }
}
