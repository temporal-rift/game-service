package io.github.temporalrift.game.action.infrastructure.adapter.in.rest.model;

import static org.assertj.core.api.Assertions.assertThat;

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
}
