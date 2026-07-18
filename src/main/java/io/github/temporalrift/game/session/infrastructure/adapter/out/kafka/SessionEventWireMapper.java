package io.github.temporalrift.game.session.infrastructure.adapter.out.kafka;

import java.time.OffsetDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.github.temporalrift.game.session.domain.event.HostTransferred;
import io.github.temporalrift.game.session.domain.event.LobbyClosed;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.event.PlayerJoinedLobby;
import io.github.temporalrift.game.session.domain.event.PlayerLeftLobby;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.HostTransferredPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.LobbyClosedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.LobbyCreatedPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerJoinedLobbyPayload;
import io.github.temporalrift.game.session.infrastructure.adapter.out.kafka.model.PlayerLeftLobbyPayload;

@Mapper(componentModel = "spring")
interface SessionEventWireMapper {

    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toOffsetDateTime")
    LobbyCreatedPayload toWire(LobbyCreated event);

    PlayerJoinedLobbyPayload toWire(PlayerJoinedLobby event);

    PlayerLeftLobbyPayload toWire(PlayerLeftLobby event);

    LobbyClosedPayload toWire(LobbyClosed event);

    HostTransferredPayload toWire(HostTransferred event);

    @org.mapstruct.Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
    }
}
