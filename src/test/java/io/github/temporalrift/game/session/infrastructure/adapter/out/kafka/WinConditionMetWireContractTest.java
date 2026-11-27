package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.WinCondition;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;

class WinConditionMetWireContractTest {

    // Match the non-null inclusion used by the outbound event publisher.
    private final JsonMapper objectMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    private final SessionEventWireMapper mapper = Mappers.getMapper(SessionEventWireMapper.class);

    @Test
    void wirePayload_preservesBothWinConditionsInOrder() {
        var payload = mapper.toWire(new WinConditionMet(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PROPHETS",
                24,
                "SCORE_THRESHOLD",
                List.of("SCORE_THRESHOLD", "FACTION_OBJECTIVE")));

        JsonNode json = objectMapper.valueToTree(payload);

        assertThat(payload.winConditions())
                .containsExactly(WinCondition.SCORE_THRESHOLD, WinCondition.FACTION_OBJECTIVE);
        assertThat(json.path("winType").asText()).isEqualTo("SCORE_THRESHOLD");
        assertThat(json.path("winConditions")).isEqualTo(objectMapper.readTree("""
                ["SCORE_THRESHOLD", "FACTION_OBJECTIVE"]
                """));
    }

    @Test
    void wirePayload_omitsWinConditionsForLastPlayerStanding() {
        var payload = mapper.toWire(
                new WinConditionMet(UUID.randomUUID(), UUID.randomUUID(), "WEAVERS", 9, "LAST_PLAYER_STANDING", null));

        JsonNode json = objectMapper.valueToTree(payload);

        assertThat(payload.winConditions()).isNull();
        assertThat(json.path("winType").asText()).isEqualTo("LAST_PLAYER_STANDING");
        assertThat(json.has("winConditions")).isFalse();
    }
}
