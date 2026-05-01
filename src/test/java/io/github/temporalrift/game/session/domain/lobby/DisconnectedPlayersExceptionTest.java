package io.github.temporalrift.game.session.domain.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisconnectedPlayersExceptionTest {

    @Test
    @DisplayName("message includes the number of disconnected players")
    void message_includesCount() {
        // given
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        // when
        var ex = new DisconnectedPlayersException(ids);

        // then
        assertThat(ex.getMessage()).contains("2");
    }

    @Test
    @DisplayName("disconnectedPlayerIds returns all provided IDs")
    void disconnectedPlayerIds_returnsAllIds() {
        // given
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // when
        var ex = new DisconnectedPlayersException(ids);

        // then
        assertThat(ex.disconnectedPlayerIds()).containsExactlyElementsOf(ids);
    }

    @Test
    @DisplayName("disconnectedPlayerIds is an unmodifiable defensive copy")
    void disconnectedPlayerIds_isDefensiveCopy() {
        // given
        var uuid = UUID.randomUUID();
        var mutable = new ArrayList<UUID>();
        mutable.add(uuid);
        var disconnectedPlayerIds = new DisconnectedPlayersException(mutable).disconnectedPlayerIds();

        // when / then
        assertThat(disconnectedPlayerIds).hasSize(1);
        assertThatThrownBy(() -> disconnectedPlayerIds.add(uuid))
                .as("disconnectedPlayerIds should be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
