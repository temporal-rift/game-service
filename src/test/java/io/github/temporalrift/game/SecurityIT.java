package io.github.temporalrift.game;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.temporalrift.game.action.application.port.in.GetRoundStatusUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlaySpecialActionUseCase;
import io.github.temporalrift.game.session.application.port.in.CreateLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.GetGameStateUseCase;
import io.github.temporalrift.game.session.application.port.in.JoinLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.LeaveLobbyUseCase;
import io.github.temporalrift.game.session.application.port.in.StartGameUseCase;
import io.github.temporalrift.game.shared.infrastructure.config.SecurityConfig;

@WebMvcTest
@Import({SecurityConfig.class, TestSecurityConfig.class})
class SecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayCardUseCase playCardUseCase;

    @MockitoBean
    private PlaySpecialActionUseCase playSpecialActionUseCase;

    @MockitoBean
    private GetRoundStatusUseCase getRoundStatusUseCase;

    @MockitoBean
    private CreateLobbyUseCase createLobbyUseCase;

    @MockitoBean
    private JoinLobbyUseCase joinLobbyUseCase;

    @MockitoBean
    private LeaveLobbyUseCase leaveLobbyUseCase;

    @MockitoBean
    private StartGameUseCase startGameUseCase;

    @MockitoBean
    private GetGameStateUseCase getGameStateUseCase;

    @Test
    @DisplayName("Given no Authorization header, when /api/v1 called, then returns 401 with problem details")
    void givenNoAuthorizationHeader_whenApiEndpointCalled_thenReturns401WithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/games/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }
}
