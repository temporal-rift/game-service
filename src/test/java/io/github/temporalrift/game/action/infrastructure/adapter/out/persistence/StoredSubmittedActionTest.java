package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

class StoredSubmittedActionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toDomainRejectsUnknownStoredActionType() {
        var stored =
                new StoredSubmittedAction("UNKNOWN", UUID.randomUUID(), null, null, null, null, null, null, null, null);

        assertThatThrownBy(stored::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown submitted action type: UNKNOWN");
    }

    @Test
    void scanTargetListSurvivesJsonRoundTripAndDomainRehydration() throws Exception {
        var eventIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        var action = new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), CardType.SCAN, CardGrade.II, null, eventIds, null, null, null);

        var stored = StoredSubmittedAction.fromDomain(action);
        var json = objectMapper.writeValueAsString(stored);
        var rehydrated =
                objectMapper.readValue(json, StoredSubmittedAction.class).toDomain();

        assertThat(rehydrated).isEqualTo(action);
        assertThat(json).contains("targetEventIds");
    }

    @Test
    void legacyJsonWithoutTargetEventIdsRehydratesWithNullList() throws Exception {
        var playerId = UUID.randomUUID();
        var cardId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var json = """
                {
                  "type": "CARD",
                  "playerId": "%s",
                  "cardInstanceId": "%s",
                  "cardType": "PUSH",
                  "cardGrade": "I",
                  "targetEventId": "%s"
                }
                """.formatted(playerId, cardId, eventId);

        var action = (SubmittedAction.CardAction)
                objectMapper.readValue(json, StoredSubmittedAction.class).toDomain();

        assertThat(action.targetEventId()).isEqualTo(eventId);
        assertThat(action.targetEventIds()).isNull();
    }
}
