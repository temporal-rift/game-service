package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
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
