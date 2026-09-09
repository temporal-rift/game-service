package io.github.temporalrift.game.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.request-body-limit")
@Validated
record RequestBodySizeLimitProperties(@Min(1) long maxBytes) {}
