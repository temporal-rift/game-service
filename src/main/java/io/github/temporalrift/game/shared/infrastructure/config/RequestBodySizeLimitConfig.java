package io.github.temporalrift.game.shared.infrastructure.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
class RequestBodySizeLimitConfig {

    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            RequestBodySizeLimitProperties properties) {
        var registration = new FilterRegistrationBean<>(new RequestBodySizeLimitFilter(properties.maxBytes()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
