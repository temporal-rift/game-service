package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockPropertySource;

class VictoryRulesPropertiesTest {

    @Test
    @DisplayName("unconfigured victory rules use the default objective thresholds")
    void defaults() {
        var properties = bind(new MockPropertySource());

        assertThat(properties.eraserAnnihilations()).isEqualTo(4);
        assertThat(properties.prophetWrittenResolutions()).isEqualTo(4);
        assertThat(properties.revisionistSuccessfulEras()).isEqualTo(3);
        assertThat(properties.weaverChainLength()).isEqualTo(3);
        assertThat(properties.activistConsecutiveDeclarations()).isEqualTo(3);
    }

    @Test
    @DisplayName("a configured threshold overrides its default")
    void override() {
        var properties =
                bind(new MockPropertySource().withProperty("game.rules.victory.prophet-written-resolutions", "5"));

        assertThat(properties.prophetWrittenResolutions()).isEqualTo(5);
        assertThat(properties.eraserAnnihilations()).isEqualTo(4);
    }

    private static VictoryRulesProperties bind(MockPropertySource source) {
        var sources = new MutablePropertySources();
        sources.addFirst(source);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bindOrCreate("game.rules.victory", VictoryRulesProperties.class);
    }
}
