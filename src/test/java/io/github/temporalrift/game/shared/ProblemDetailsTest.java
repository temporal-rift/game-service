package io.github.temporalrift.game.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProblemDetailsTest {

    @Test
    void addsTheStableErrorCodeExtension() {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "Lobby was not found", "404-01");

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Lobby was not found");
        assertThat(problem.getProperties()).containsEntry("code", "404-01");
    }
}
