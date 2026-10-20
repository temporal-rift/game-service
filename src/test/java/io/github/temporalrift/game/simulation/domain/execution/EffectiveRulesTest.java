package io.github.temporalrift.game.simulation.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EffectiveRulesTest {

    private static final String PUBLISHED_DIGEST = "2f4e77d6f2f5ad41eb3b05de091e524133995ef6f274a58f539adbf2d5b06fad";

    @Test
    @DisplayName("the published example canonicalises to its published bytes and digest, whatever the key order")
    void digest_publishedExample_matchesThePublishedValue() {
        var reversed = new LinkedHashMap<String, Object>();
        reversed.put("game.rules.win-score-threshold", 20);
        reversed.put("game.rules.probability.amplify-multiplier.II", 2);

        var rules = new EffectiveRules(reversed);

        assertThat(rules.canonicalJson())
                .isEqualTo(
                        "{\"game.rules.probability.amplify-multiplier.II\":2,\"game.rules.win-score-threshold\":20}");
        assertThat(rules.digest()).isEqualTo(PUBLISHED_DIGEST);
    }

    @Test
    @DisplayName("2.0 and 2 are the same rule with the same digest")
    void digest_numberSpelling_doesNotMatter() {
        var integral = new EffectiveRules(Map.of("game.rules.a", 2, "game.rules.b", 20));
        var decimal = new EffectiveRules(Map.of("game.rules.a", 2.0, "game.rules.b", new BigDecimal("2.0E+1")));

        assertThat(decimal).isEqualTo(integral);
        assertThat(decimal.digest()).isEqualTo(integral.digest());
    }

    @ParameterizedTest
    @CsvSource({
        "0.5,0.5",
        "-0.5,-0.5",
        "0.000001,0.000001",
        "0.0000001,1e-7",
        "123456789012345678901,123456789012345680000",
        "1e21,1e+21",
        "1.5e300,1.5e+300",
        "100,100",
        "0.1,0.1",
        "-0.0,0"
    })
    @DisplayName("numbers are written the way ECMAScript writes them")
    void canonicalJson_numbers_followEcmaScript(String number, String expected) {
        var rules = new EffectiveRules(Map.of("game.rules.n", new BigDecimal(number)));

        assertThat(rules.canonicalJson()).isEqualTo("{\"game.rules.n\":" + expected + "}");
    }

    @Test
    @DisplayName("strings are escaped as RFC 8785 requires and lists keep their order")
    void canonicalJson_stringsAndLists() {
        var rules = new EffectiveRules(Map.of(
                "game.rules.text", "a\"b\\c\n\u0001é", "game.rules.flag", true, "game.rules.list", List.of("B", "A")));

        assertThat(rules.canonicalJson())
                .isEqualTo("{\"game.rules.flag\":true,\"game.rules.list\":[\"B\",\"A\"],"
                        + "\"game.rules.text\":\"a\\\"b\\\\c\\n\\" + "u0001é\"}");
    }

    @Test
    @DisplayName("keys sort by UTF-16 code unit")
    void canonicalJson_keyOrder_isCodeUnitOrder() {
        var rules = new EffectiveRules(Map.of("game.rules.b", 1, "game.rules.B", 2, "game.rules.a.b", 3));

        assertThat(rules.canonicalJson()).isEqualTo("{\"game.rules.B\":2,\"game.rules.a.b\":3,\"game.rules.b\":1}");
    }

    @Test
    @DisplayName("bindable properties are text, with list elements under their index")
    void bindable_flattensListsAndKeepsPlainNumbers() {
        var rules = new EffectiveRules(Map.of(
                "game.rules.threshold", new BigDecimal("2.0E+1"),
                "game.rules.specials", List.of("SEAL", "STALL"),
                "game.rules.none", List.of()));

        assertThat(rules.bindable())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "game.rules.threshold", "20",
                        "game.rules.specials[0]", "SEAL",
                        "game.rules.specials[1]", "STALL"));
    }

    @Test
    @DisplayName("every key must start with game.rules.")
    void keys_outsideTheRulesNamespace_areRejected() {
        var foreign = Map.<String, Object>of("game.timers.x", 1);

        assertThatThrownBy(() -> new EffectiveRules(foreign))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("game.timers.x");
    }

    @Test
    @DisplayName("a value that is not a number, boolean, string or list of those is rejected, naming the key")
    void values_ofAnotherShape_areRejected() {
        var nested = Map.<String, Object>of("game.rules.a", Map.of("b", 1));
        var listOfLists = Map.<String, Object>of("game.rules.c", List.of(List.of(1)));
        var notFinite = Map.<String, Object>of("game.rules.d", Double.NaN);

        assertThatThrownBy(() -> new EffectiveRules(nested))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("game.rules.a");
        assertThatThrownBy(() -> new EffectiveRules(listOfLists))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("game.rules.c");
        assertThatThrownBy(() -> new EffectiveRules(notFinite))
                .isInstanceOf(InvalidExecutionContextException.class)
                .hasMessageContaining("game.rules.d");
    }
}
