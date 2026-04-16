package io.github.temporalrift.game.session.application.command;

import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Component
class JoinCodeGenerator {

    private final LobbyRepository lobbyRepository;

    JoinCodeGenerator(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    String generate() {
        return Stream.generate(() -> UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 6)
                        .toUpperCase())
                .filter(code -> !lobbyRepository.existsByJoinCode(code))
                .findFirst()
                .orElseThrow();
    }
}
