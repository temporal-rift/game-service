package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockPropertySource;

class VictoryRulesPropertiesTest {

    private static final String CHAIN_LENGTH = "game.rules.victory.weaver-chain-length";

    @Test
    @DisplayName("unconfigured victory rules use the default objective thresholds")
    void defaults() {
        var properties = bind(new MockPropertySource().withProperty(CHAIN_LENGTH, "4"));

        assertThat(properties.eraserAnnihilations()).isEqualTo(4);
        assertThat(properties.prophetWrittenResolutions()).isEqualTo(3);
        assertThat(properties.revisionistSuccessfulEras()).isEqualTo(3);
        assertThat(properties.weaverChainLength()).isEqualTo(4);
        assertThat(properties.activistConsecutiveDeclarations()).isEqualTo(3);
    }

    @Test
    @DisplayName("a configured threshold overrides its default")
    void override() {
        var properties = bind(new MockPropertySource()
                .withProperty("game.rules.victory.prophet-written-resolutions", "5")
                .withProperty(CHAIN_LENGTH, "4"));

        assertThat(properties.prophetWrittenResolutions()).isEqualTo(5);
        assertThat(properties.eraserAnnihilations()).isEqualTo(4);
    }

    @Test
    @DisplayName("without table-size overrides every table size uses the base objectives")
    void noTableSizeOverridesByDefault() {
        var properties = bind(new MockPropertySource().withProperty(CHAIN_LENGTH, "4"));

        assertThat(properties.eraserAnnihilations(5)).isEqualTo(4);
        assertThat(properties.activistRequiresConsecutiveEras(5)).isTrue();
    }

    @Test
    @DisplayName("Prophet and Revisionist objectives can differ for a listed table size only")
    void prophetAndRevisionistTableSizeOverrides() {
        var properties = bind(new MockPropertySource()
                .withProperty(CHAIN_LENGTH, "4")
                .withProperty("game.rules.victory.prophet-written-resolutions-by-player-count[5]", "2")
                .withProperty("game.rules.victory.revisionist-successful-eras-by-player-count[5]", "2"));

        assertThat(properties.prophetWrittenResolutions(5)).isEqualTo(2);
        assertThat(properties.revisionistSuccessfulEras(5)).isEqualTo(2);
        assertThat(properties.prophetWrittenResolutions(4)).isEqualTo(3);
        assertThat(properties.revisionistSuccessfulEras(4)).isEqualTo(3);
    }

    @Test
    @DisplayName("a table-size objective below one is rejected")
    void tableSizeObjectiveBelowOneRejected() {
        var source = new MockPropertySource()
                .withProperty(CHAIN_LENGTH, "4")
                .withProperty("game.rules.victory.prophet-written-resolutions-by-player-count[5]", "0");

        Assertions.assertThatThrownBy(() -> bind(source)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("configured table-size overrides apply only to the listed player counts")
    void tableSizeOverrides() {
        var properties = bind(new MockPropertySource()
                .withProperty(CHAIN_LENGTH, "4")
                .withProperty("game.rules.victory.eraser-annihilations-by-player-count[5]", "3")
                .withProperty("game.rules.victory.activist-consecutive-eras-by-player-count[5]", "false"));

        assertThat(properties.eraserAnnihilations(5)).isEqualTo(3);
        assertThat(properties.eraserAnnihilations(4)).isEqualTo(4);
        assertThat(properties.activistRequiresConsecutiveEras(5)).isFalse();
        assertThat(properties.activistRequiresConsecutiveEras(4)).isTrue();
    }

    @Test
    @DisplayName("an Eraser override below one step is rejected")
    void eraserOverrideBelowOneIsRejected() {
        var source = new MockPropertySource()
                .withProperty(CHAIN_LENGTH, "4")
                .withProperty("game.rules.victory.eraser-annihilations-by-player-count[5]", "0");

        assertThatThrownBy(() -> bind(source)).hasStackTraceContaining("must be at least 1");
    }

    @Test
    @DisplayName("the Weaver chain length has no built-in default, so it never silently differs from timeline-service")
    void chainLengthIsRequired() {
        var unset = bind(new MockPropertySource());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(unset))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("weaverChainLength");
        }
    }

    private static VictoryRulesProperties bind(MockPropertySource source) {
        var sources = new MutablePropertySources();
        sources.addFirst(source);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bindOrCreate("game.rules.victory", VictoryRulesProperties.class);
    }
}
