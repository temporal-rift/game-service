package com.temporalrift.game.session.infrastructure.adapter.out.persistence;

import org.mapstruct.Mapper;

import com.temporalrift.game.session.domain.game.Game;

@Mapper
interface GamePersistenceMapper {

    GameJpaEntity toEntity(Game game);
}
