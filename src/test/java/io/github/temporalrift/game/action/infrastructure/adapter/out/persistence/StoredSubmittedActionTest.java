package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StoredSubmittedActionTest {

    @Test
    void toDomainRejectsUnknownStoredActionType() {
        var stored =
                new StoredSubmittedAction("UNKNOWN", UUID.randomUUID(), null, null, null, null, null, null, null, null);

        assertThatThrownBy(stored::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown submitted action type: UNKNOWN");
    }
}
