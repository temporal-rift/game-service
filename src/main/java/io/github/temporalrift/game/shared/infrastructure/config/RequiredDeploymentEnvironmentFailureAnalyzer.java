package io.github.temporalrift.game.shared.infrastructure.config;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Provides actionable guidance when a required production environment variable is absent. */
public final class RequiredDeploymentEnvironmentFailureAnalyzer
        extends AbstractFailureAnalyzer<IllegalArgumentException> {

    private static final Pattern MISSING_PLACEHOLDER_PATTERN =
            Pattern.compile("Could not resolve placeholder '([^']+)' in value");

    private static final Set<String> REQUIRED_ENVIRONMENT_VARIABLES = Set.of(
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "KAFKA_BOOTSTRAP_SERVERS",
            "KAFKA_SECURITY_PROTOCOL",
            "JWT_ISSUER_URI");

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, IllegalArgumentException cause) {
        var missingVariable = missingRequiredEnvironmentVariable(cause.getMessage());
        if (missingVariable == null) {
            return null;
        }

        return new FailureAnalysis(
                "The required environment variable '" + missingVariable + "' is not configured. "
                        + "No default is provided because it controls an external dependency or security boundary.",
                "Set '" + missingVariable + "' in the deployment environment. Required variables are: "
                        + String.join(", ", REQUIRED_ENVIRONMENT_VARIABLES)
                        + ". See README.md for deployment configuration.",
                cause);
    }

    private String missingRequiredEnvironmentVariable(String message) {
        if (message == null) {
            return null;
        }

        var matcher = MISSING_PLACEHOLDER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        var variable = matcher.group(1);
        return REQUIRED_ENVIRONMENT_VARIABLES.contains(variable) ? variable : null;
    }
}
