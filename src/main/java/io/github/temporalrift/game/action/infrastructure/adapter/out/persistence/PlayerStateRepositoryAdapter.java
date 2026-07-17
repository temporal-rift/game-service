package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Component
class PlayerStateRepositoryAdapter implements PlayerStateRepository {

    private final PlayerStateJpaRepository jpaRepository;

    PlayerStateRepositoryAdapter(PlayerStateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PlayerState save(PlayerState playerState) {
        jpaRepository.save(toEntity(playerState));
        return playerState;
    }

    @Override
    public Optional<PlayerState> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<PlayerState> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return jpaRepository.findByGameIdAndPlayerId(gameId, playerId).map(this::toDomain);
    }

    @Override
    public Optional<PlayerState> findByGameIdAndPlayerIdWithLock(UUID gameId, UUID playerId) {
        return jpaRepository.findByGameIdAndPlayerIdWithLock(gameId, playerId).map(this::toDomain);
    }

    @Override
    public List<PlayerState> findAllByGameId(UUID gameId) {
        return jpaRepository.findAllByGameId(gameId).stream()
                .map(this::toDomain)
                .toList();
    }

    private PlayerStateJpaEntity toEntity(PlayerState state) {
        var entity = new PlayerStateJpaEntity();
        entity.setId(state.id());
        entity.setGameId(state.gameId());
        entity.setPlayerId(state.playerId());
        entity.setFaction(state.faction() == null ? null : state.faction().name());
        entity.setJammed(state.isJammed());
        entity.setHand(
                state.hand().stream().map(PlayerHandCardValue::fromDomain).toList());
        return entity;
    }

    private PlayerState toDomain(PlayerStateJpaEntity entity) {
        return PlayerState.reconstitute(
                entity.getId(),
                entity.getGameId(),
                entity.getPlayerId(),
                entity.getFaction() == null ? null : Faction.valueOf(entity.getFaction()),
                entity.getHand().stream()
                        .map(PlayerHandCardValue::toDomain)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll),
                entity.isJammed());
    }
}
