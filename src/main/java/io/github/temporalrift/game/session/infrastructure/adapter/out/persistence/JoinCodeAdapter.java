package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Component
class JoinCodeAdapter implements JoinCodePort {

    private final LobbyRepository lobbyRepository;

    JoinCodeAdapter(LobbyRepository lobbyRepository) {
        this.lobbyRepository = lobbyRepository;
    }

    @Override
    public String generate() {
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
