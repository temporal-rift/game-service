package io.github.temporalrift.game.action.infrastructure.adapter.in.rest.v1.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardActionRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("SubmitActionRequest deserializes sourceOutcomeId for card actions")
    void submitActionRequestDeserializesSourceOutcomeId() throws Exception {
        var cardInstanceId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var json = """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetEventId": "%s",
                  "sourceOutcomeId": "%s",
                  "targetOutcomeId": "%s"
                }
                """.formatted(cardInstanceId, targetEventId, sourceOutcomeId, targetOutcomeId);

        var request = objectMapper.readValue(json, SubmitActionRequest.class);

        assertThat(request).isInstanceOfSatisfying(CardActionRequest.class, card -> {
            assertThat(card.getCardInstanceId()).isEqualTo(cardInstanceId);
            assertThat(card.getTargetEventId()).isEqualTo(targetEventId);
            assertThat(card.getSourceOutcomeId()).isEqualTo(sourceOutcomeId);
            assertThat(card.getTargetOutcomeId()).isEqualTo(targetOutcomeId);
        });
    }

    @Test
    @DisplayName("SubmitActionRequest deserializes targetPlayerId for a player-targeting card, no targetEventId")
    void submitActionRequestDeserializesTargetPlayerId() throws Exception {
        var cardInstanceId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var json = """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetPlayerId": "%s"
                }
                """.formatted(cardInstanceId, targetPlayerId);

        var request = objectMapper.readValue(json, SubmitActionRequest.class);

        assertThat(request).isInstanceOfSatisfying(CardActionRequest.class, card -> {
            assertThat(card.getCardInstanceId()).isEqualTo(cardInstanceId);
            assertThat(card.getTargetPlayerId()).isEqualTo(targetPlayerId);
            assertThat(card.getTargetEventId()).isNull();
        });
    }

    @Test
    @DisplayName("SubmitActionRequest deserializes the SCAN event selection")
    void submitActionRequestDeserializesTargetEventIds() throws Exception {
        var cardInstanceId = UUID.randomUUID();
        var eventIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        var json = """
                {
                  "actionType": "CARD",
                  "cardInstanceId": "%s",
                  "targetEventIds": ["%s", "%s"]
                }
                """.formatted(cardInstanceId, eventIds.get(0), eventIds.get(1));

        var request = objectMapper.readValue(json, SubmitActionRequest.class);

        assertThat(request).isInstanceOfSatisfying(CardActionRequest.class, card -> {
            assertThat(card.getCardInstanceId()).isEqualTo(cardInstanceId);
            assertThat(card.getTargetEventId()).isNull();
            assertThat(card.getTargetEventIds()).containsExactlyElementsOf(eventIds);
        });
    }
}
