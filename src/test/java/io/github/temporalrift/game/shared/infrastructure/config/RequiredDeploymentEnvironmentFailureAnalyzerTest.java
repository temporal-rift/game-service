package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequiredDeploymentEnvironmentFailureAnalyzerTest {

    private final RequiredDeploymentEnvironmentFailureAnalyzer analyzer =
            new RequiredDeploymentEnvironmentFailureAnalyzer();

    @Test
    @DisplayName("missing required deployment setting explains how to configure it")
    void analyze_requiredDeploymentSettingMissing_returnsActionableGuidance() {
        var cause = new IllegalArgumentException(
                "Could not resolve placeholder 'KAFKA_SECURITY_PROTOCOL' in value \"${KAFKA_SECURITY_PROTOCOL}\"");

        var analysis = analyzer.analyze(cause);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("KAFKA_SECURITY_PROTOCOL");
        assertThat(analysis.getAction())
                .contains("KAFKA_SECURITY_PROTOCOL")
                .contains("JWT_ISSUER_URI")
                .contains("SPRING_DATASOURCE_USERNAME");
    }

    @Test
    @DisplayName("unrelated missing setting is left to Spring's standard diagnostics")
    void analyze_unrelatedSettingMissing_returnsNoAnalysis() {
        var cause = new IllegalArgumentException(
                "Could not resolve placeholder 'UNRELATED_SETTING' in value \"${UNRELATED_SETTING}\"");

        assertThat(analyzer.analyze(cause)).isNull();
    }
}
