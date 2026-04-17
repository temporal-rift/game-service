package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@ExtendWith(MockitoExtension.class)
class JoinCodeAdapterTest {

    @Mock
    LobbyRepository lobbyRepository;

    @InjectMocks
    JoinCodeAdapter adapter;

    @Test
    @DisplayName("returns a 6-character uppercase alphanumeric code")
    void generate_returnsValidCode() {
        // given
        given(lobbyRepository.existsByJoinCode(anyString())).willReturn(false);

        // when
        var code = adapter.generate();

        // then
        assertThat(code).hasSize(6).isUpperCase();
    }

    @Test
    @DisplayName("retries until a code not already in use is found")
    void generate_collision_retriesUntilUnique() {
        // given
        given(lobbyRepository.existsByJoinCode(anyString()))
                .willReturn(true)
                .willReturn(true)
                .willReturn(false);

        // when
        var code = adapter.generate();

        // then
        assertThat(code).hasSize(6);
        then(lobbyRepository).should(times(3)).existsByJoinCode(anyString());
    }
}
