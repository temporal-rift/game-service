package io.github.temporalrift.game;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
class SecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Given no Authorization header, when /api/v1 called, then returns 401 with problem details")
    void givenNoAuthorizationHeader_whenApiEndpointCalled_thenReturns401WithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/games/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }
}
