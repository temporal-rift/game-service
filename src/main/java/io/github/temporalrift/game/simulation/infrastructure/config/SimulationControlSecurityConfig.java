package io.github.temporalrift.game.simulation.infrastructure.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.shared.infrastructure.adapter.in.rest.ProblemDetails;

/**
 * Guards the control routes by the {@code simulation:control} scope, independently of participant authentication.
 * The chain exists only in an isolated simulation deployment, so an ordinary one never knows these routes.
 */
@Configuration
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
public class SimulationControlSecurityConfig {

    private static final String CONTROL_AUTHORITY = "SCOPE_simulation:control";

    @Bean
    @Order(1)
    SecurityFilterChain simulationControlFilterChain(HttpSecurity http, ObjectMapper objectMapper) {
        return http.securityMatcher("/internal/simulation/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(CONTROL_AUTHORITY))
                .oauth2ResourceServer(
                        oauth2 -> oauth2.authenticationEntryPoint((request, response, exception) -> writeProblem(
                                        objectMapper,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED",
                                        "A valid bearer token is required"))
                                .jwt(Customizer.withDefaults()))
                .exceptionHandling(
                        exceptions -> exceptions.accessDeniedHandler((request, response, exception) -> writeProblem(
                                objectMapper,
                                response,
                                HttpStatus.FORBIDDEN,
                                "SIMULATION_CONTROL_FORBIDDEN",
                                "The simulation:control scope is required")))
                .build();
    }

    private static void writeProblem(
            ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(objectMapper.writeValueAsString(ProblemDetails.of(status, detail, code)));
    }
}
