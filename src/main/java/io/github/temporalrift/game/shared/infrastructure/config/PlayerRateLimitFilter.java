package io.github.temporalrift.game.shared.infrastructure.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.shared.PlayerPrincipal;

/**
 * Runs after authentication so the limit key is the verified {@code playerId} from the JWT, not a
 * spoofable request attribute. Unauthenticated requests are left to the entry point.
 */
class PlayerRateLimitFilter extends OncePerRequestFilter {

    private final PlayerRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    PlayerRateLimitFilter(PlayerRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof PlayerPrincipal(var playerId)
                && !rateLimiter.tryAcquire(playerId)) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Request rate limit exceeded");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
