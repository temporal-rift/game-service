package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;

@Component
class LobbyRepositoryAdapter implements LobbyRepository {

    private final LobbyJpaRepository jpaRepository;

    private final SessionEventPublisher eventPublisher;

    private final Clock clock;

    LobbyRepositoryAdapter(LobbyJpaRepository jpaRepository, SessionEventPublisher eventPublisher, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Lobby save(Lobby lobby) {
        jpaRepository.save(toEntity(lobby));
        lobby.pullEvents()
                .forEach(event -> eventPublisher.publish(
                        EventEnvelope.create(lobby.id(), Lobby.AGGREGATE_TYPE, lobby.gameId(), 1, event)));
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

    @Override
    public boolean existsByJoinCode(String joinCode) {
        return jpaRepository.existsByJoinCode(joinCode);
    }

    private LobbyJpaEntity toEntity(Lobby lobby) {
        var entity = new LobbyJpaEntity();
        entity.setId(lobby.id());
        entity.setGameId(lobby.gameId());
        entity.setHostPlayerId(lobby.hostPlayerId());
        entity.setJoinCode(lobby.joinCode());
        entity.setStatus(lobby.status().name());
        entity.setMinPlayers(lobby.minPlayers());
        entity.setMaxPlayers(lobby.maxPlayers());

        var players = lobby.currentPlayers().stream()
                .map(p -> toPlayerEntity(p, entity))
                .toList();
        entity.setPlayers(players);

        return entity;
    }

    private LobbyPlayerJpaEntity toPlayerEntity(LobbyPlayer player, LobbyJpaEntity lobbyEntity) {
        var entity = new LobbyPlayerJpaEntity();
        entity.setId(new LobbyPlayerPk(lobbyEntity.getId(), player.playerId()));
        entity.setLobby(lobbyEntity);
        entity.setPlayerName(player.playerName());
        entity.setFaction(player.faction() != null ? player.faction().name() : null);
        entity.setJoinedAt(player.joinedAt());
        entity.setConnected(player.connected());
        return entity;
    }

    private Lobby toDomain(LobbyJpaEntity entity) {
        var players = entity.getPlayers().stream()
                .map(p -> new LobbyPlayer(
                        p.getId().getPlayerId(),
                        p.getPlayerName(),
                        p.getFaction() != null ? Faction.valueOf(p.getFaction()) : null,
                        p.getJoinedAt(),
                        p.isConnected()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        return Lobby.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getHostPlayerId(),
                entity.getJoinCode(),
                players,
                LobbyStatus.valueOf(entity.getStatus()),
                entity.getMinPlayers(),
                entity.getMaxPlayers(),
                clock);
    }
}
