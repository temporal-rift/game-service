package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.sessionevents.GeneratedChannelContract.ForesightRevealedPayload;
import io.github.temporalrift.game.shared.domain.event.ForesightRevealed;

class ForesightRevealedWireContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final jakarta.validation.Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final SessionEventWireMapper mapper = Mappers.getMapper(SessionEventWireMapper.class);

    @Test
    void wirePayload_carriesOnlyTheViewerScopedPreview() throws Exception {
        var gameId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var catalogEventId = UUID.randomUUID();
        var catalogOutcomeId = UUID.randomUUID();
        var payload = mapper.toWire(new ForesightRevealed(
                gameId,
                2,
                viewerId,
                3,
                List.of(new ForesightRevealed.RevealedEvent(
                        catalogEventId,
                        "Storm",
                        List.of(new ForesightRevealed.RevealedOutcome(catalogOutcomeId, "Flood")))),
                null));

        assertThat(validator.validate(payload)).isEmpty();
        assertThat(payload)
                .isInstanceOf(ForesightRevealedPayload.class)
                .extracting(
                        ForesightRevealedPayload::gameId,
                        ForesightRevealedPayload::eraNumber,
                        ForesightRevealedPayload::playerId,
                        ForesightRevealedPayload::nextEraNumber,
                        ForesightRevealedPayload::emptyReason)
                .containsExactly(gameId, 2, viewerId, 3, null);
        assertThat(payload.revealedEvents()).singleElement().satisfies(event -> {
            assertThat(event.catalogEventId()).isEqualTo(catalogEventId);
            assertThat(event.title()).isEqualTo("Storm");
            assertThat(event.outcomes()).singleElement().satisfies(outcome -> {
                assertThat(outcome.catalogOutcomeId()).isEqualTo(catalogOutcomeId);
                assertThat(outcome.description()).isEqualTo("Flood");
            });
        });
        var json = objectMapper.writeValueAsString(payload);

        assertThat(json)
                .contains("gameId", "eraNumber", "playerId", "nextEraNumber", "revealedEvents")
                .contains("catalogEventId", "title", "outcomes", "catalogOutcomeId", "description");
        assertThat(json.toLowerCase())
                .doesNotContain("actor", "probability", "band", "deck", "influencer", "jammer", "interceptor");
    }

    @Test
    void wirePayload_finalEraEmptyPreview_staysValid() throws Exception {
        var payload = mapper.toWire(
                new ForesightRevealed(UUID.randomUUID(), 5, UUID.randomUUID(), 6, List.of(), "final-era"));

        assertThat(validator.validate(payload)).isEmpty();
        assertThat(payload.revealedEvents()).isEmpty();
        assertThat(payload.emptyReason()).isEqualTo("final-era");
        assertThat(objectMapper.writeValueAsString(payload)).contains("final-era");
    }

    @Test
    void wirePayload_rejectsAMissingViewer() {
        var payload = new ForesightRevealedPayload(UUID.randomUUID(), 2, null, 3, List.of(), "deck-exhausted");

        assertThat(validator.validate(payload)).isNotEmpty();
    }
}
