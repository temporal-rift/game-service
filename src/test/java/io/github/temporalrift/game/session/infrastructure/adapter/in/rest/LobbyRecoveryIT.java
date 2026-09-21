package io.github.temporalrift.game.session.infrastructure.adapter.in.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerAuthenticationToken;
import io.github.temporalrift.game.shared.infrastructure.config.PlayerPrincipal;

@GameServiceIntegrationTest
class LobbyRecoveryIT {

    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID NON_MEMBER_ID = UUID.randomUUID();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    Clock clock;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void lobbyRecovery_returnsAuthenticatedMemberAndWithholdsRosterFromNonMember() throws Exception {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        saveLobby(lobbyId, gameId);

        mockMvc.perform(get("/api/v1/lobbies/{lobbyId}", lobbyId).with(auth(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lobbyId").value(lobbyId.toString()))
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.hostPlayerId").value(HOST_ID.toString()))
                .andExpect(jsonPath("$.currentPlayerId").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.members[0].playerId").value(HOST_ID.toString()))
                .andExpect(jsonPath("$.members[1].playerId").value(MEMBER_ID.toString()));

        mockMvc.perform(get("/api/v1/lobbies/{lobbyId}", lobbyId).with(auth(NON_MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403-01"))
                .andExpect(jsonPath("$.currentPlayerId").doesNotExist())
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    private void saveLobby(UUID lobbyId, UUID gameId) {
        var members = new ArrayList<>(List.of(
                new LobbyPlayer(HOST_ID, "Host", null, Instant.parse("2026-01-01T00:00:00Z"), true),
                new LobbyPlayer(MEMBER_ID, "Member", null, Instant.parse("2026-01-01T00:00:01Z"), true)));
        var lobby = Lobby.reconstitute(
                lobbyId,
                gameId,
                HOST_ID,
                members,
                LobbyStatus.WAITING,
                new LobbyConfig(lobbyId.toString().substring(0, 6).toUpperCase(), 3, 5, clock));
        transactionTemplate.executeWithoutResult(_ -> lobbyRepository.save(lobby));
    }

    private RequestPostProcessor auth(UUID playerId) {
        return authentication(new PlayerAuthenticationToken(new PlayerPrincipal(playerId)));
    }
}
