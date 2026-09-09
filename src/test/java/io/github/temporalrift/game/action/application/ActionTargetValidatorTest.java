package io.github.temporalrift.game.action.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.action.domain.actionround.UnknownActionTargetException;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionTargetValidator")
class ActionTargetValidatorTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 1;

    @Mock
    FutureEventDefinitionPort futureEventDefinitionPort;

    @InjectMocks
    ActionTargetValidator validator;

    @Test
    @DisplayName("validate — event and outcomes belong to the current era — does not throw")
    void validateKnownTargetsDoesNotThrow() {
        // given
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(List.of(new FutureEventDefinitionPort.EventDefinition(
                        eventId, List.of(new FutureEventDefinitionPort.OutcomeDefinition(outcomeId, 50)))));

        // when / then
        assertThatCode(() -> validator.validate(GAME_ID, ERA, eventId, outcomeId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate — null outcome ids are ignored")
    void validateIgnoresNullOutcomeIds() {
        // given
        var eventId = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(List.of(new FutureEventDefinitionPort.EventDefinition(eventId, List.of())));

        // when / then
        assertThatCode(() -> validator.validate(GAME_ID, ERA, eventId, (UUID) null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate — targetless action — skips event lookup")
    void validateTargetlessActionSkipsEventLookup() {
        // when / then
        assertThatCode(() -> validator.validate(GAME_ID, ERA, null, (UUID) null))
                .doesNotThrowAnyException();

        then(futureEventDefinitionPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("validate — unknown event id — throws UnknownActionTargetException")
    void validateUnknownEventThrows() {
        // given
        var eventId = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(List.of());

        // when / then
        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validator.validate(GAME_ID, ERA, eventId));
    }

    @Test
    @DisplayName("validate — outcome id supplied without a target event — throws UnknownActionTargetException")
    void validateOutcomeWithoutEventThrows() {
        // given
        var strayOutcomeId = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA)).willReturn(List.of());

        // when / then
        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validator.validate(GAME_ID, ERA, null, strayOutcomeId));
    }

    @Test
    @DisplayName("validate — unknown outcome id — throws UnknownActionTargetException")
    void validateUnknownOutcomeThrows() {
        // given
        var eventId = UUID.randomUUID();
        var unknownOutcomeId = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(List.of(new FutureEventDefinitionPort.EventDefinition(eventId, List.of())));

        // when / then
        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validator.validate(GAME_ID, ERA, eventId, unknownOutcomeId));
    }

    @Test
    @DisplayName("validateCardTargets — validates the whole list with one definition lookup")
    void validateCardTargetsUsesOneDefinitionLookup() {
        var event1 = UUID.randomUUID();
        var event2 = UUID.randomUUID();
        var event3 = UUID.randomUUID();
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(List.of(
                        new FutureEventDefinitionPort.EventDefinition(event1, List.of()),
                        new FutureEventDefinitionPort.EventDefinition(event2, List.of()),
                        new FutureEventDefinitionPort.EventDefinition(event3, List.of())));

        var knownIds = validator.validateCardTargets(GAME_ID, ERA, null, List.of(event1, event3));

        assertThat(knownIds).containsExactlyInAnyOrder(event1, event2, event3);
        then(futureEventDefinitionPort).should().findByGameIdAndEraNumber(GAME_ID, ERA);
        then(futureEventDefinitionPort).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("validateCardTargets — any unknown list id is rejected")
    void validateCardTargetsRejectsUnknownListId() {
        var known = UUID.randomUUID();
        var unknown = UUID.randomUUID();
        var requestedIds = List.of(known, unknown);
        given(futureEventDefinitionPort.findByGameIdAndEraNumber(GAME_ID, ERA))
                .willReturn(List.of(new FutureEventDefinitionPort.EventDefinition(known, List.of())));

        assertThatExceptionOfType(UnknownActionTargetException.class)
                .isThrownBy(() -> validator.validateCardTargets(GAME_ID, ERA, null, requestedIds))
                .withMessageContaining(unknown.toString());

        then(futureEventDefinitionPort).should().findByGameIdAndEraNumber(GAME_ID, ERA);
        then(futureEventDefinitionPort).shouldHaveNoMoreInteractions();
    }
}
