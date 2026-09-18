package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ForesightRevealedWireContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final jakarta.validation.Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void wirePayload_carriesOnlyTheViewerScopedPreview() throws Exception {
        var payload = new ForesightRevealedWirePayload(
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                3,
                List.of(new ForesightRevealedWirePayload.RevealedEvent(
                        UUID.randomUUID(),
                        "Storm",
                        List.of(new ForesightRevealedWirePayload.RevealedOutcome(UUID.randomUUID(), "Flood")))),
                null);

        assertThat(validator.validate(payload)).isEmpty();
        var json = objectMapper.writeValueAsString(payload);

        assertThat(json).contains("gameId", "eraNumber", "playerId", "nextEraNumber", "revealedEvents");
        assertThat(json).contains("catalogEventId", "title", "outcomes", "catalogOutcomeId", "description");
        assertThat(json.toLowerCase())
                .doesNotContain("actor", "probability", "band", "deck", "influencer", "jammer", "interceptor");
    }

    @Test
    void wirePayload_finalEraEmptyPreview_staysValid() throws Exception {
        var payload =
                new ForesightRevealedWirePayload(UUID.randomUUID(), 5, UUID.randomUUID(), 6, List.of(), "final-era");

        assertThat(validator.validate(payload)).isEmpty();
        assertThat(objectMapper.writeValueAsString(payload)).contains("final-era");
    }

    @Test
    void wirePayload_rejectsAMissingViewer() {
        var payload = new ForesightRevealedWirePayload(UUID.randomUUID(), 2, null, 3, List.of(), "deck-exhausted");

        assertThat(validator.validate(payload)).isNotEmpty();
    }
}
