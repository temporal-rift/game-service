package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;

@Component
class LobbyRepositoryAdapter implements LobbyRepository {

    private final LobbyJpaRepository jpaRepository;

    LobbyRepositoryAdapter(LobbyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Lobby save(Lobby lobby) {
        jpaRepository.save(toEntity(lobby));
        return lobby;
    }

    @Override
    public Optional<Lobby> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Lobby> findByJoinCode(String joinCode) {
        return jpaRepository.findByJoinCode(joinCode).map(this::toDomain);
    }

    private LobbyJpaEntity toEntity(Lobby lobby) {
        var entity = new LobbyJpaEntity();
        entity.setId(lobby.id());
        entity.setGameId(lobby.gameId());
        entity.setHostPlayerId(lobby.hostPlayerId());
        entity.setJoinCode(lobby.joinCode());
        entity.setStatus(lobby.status().name());

        var players = lobby.currentPlayers().stream()
                .map(p -> toPlayerEntity(p, entity))
                .toList();
        entity.setPlayers(players);

        return entity;
    }

    private LobbyPlayerJpaEntity toPlayerEntity(LobbyPlayer player, LobbyJpaEntity lobbyEntity) {
        var entity = new LobbyPlayerJpaEntity();
        entity.setId(player.playerId());
        entity.setLobby(lobbyEntity);
        entity.setPlayerId(player.playerId());
        entity.setPlayerName(player.playerName());
        entity.setHost(player.isHost());
        entity.setFaction(player.faction() != null ? player.faction().name() : null);
        entity.setJoinedAt(Instant.now());
        return entity;
    }

    private Lobby toDomain(LobbyJpaEntity entity) {
        var players = entity.getPlayers().stream()
                .map(p -> new LobbyPlayer(
                        p.getPlayerId(),
                        p.getPlayerName(),
                        p.isHost(),
                        p.getFaction() != null ? Faction.valueOf(p.getFaction()) : null))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        return Lobby.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getHostPlayerId(),
                entity.getJoinCode(),
                players,
                LobbyStatus.valueOf(entity.getStatus()));
    }
}
