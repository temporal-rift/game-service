package io.github.temporalrift.game.action.domain.playerstate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.shared.AggregateRoot;

public class PlayerState extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "PlayerState";

    private final UUID id;
    private final UUID gameId;
    private final UUID playerId;
    private final List<CardInstance> hand;
    private Faction faction;
    private boolean jammed;

    public PlayerState(UUID id, UUID gameId, UUID playerId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.faction = null;
        this.hand = new ArrayList<>();
        this.jammed = false;
    }

    private PlayerState(UUID id, UUID gameId, UUID playerId, Faction faction, List<CardInstance> hand, boolean jammed) {
        this.id = id;
        this.gameId = gameId;
        this.playerId = playerId;
        this.faction = faction;
        this.hand = new ArrayList<>(hand);
        this.jammed = jammed;
    }

    public static PlayerState reconstitute(
            UUID id, UUID gameId, UUID playerId, Faction faction, List<CardInstance> hand, boolean jammed) {
        return new PlayerState(id, gameId, playerId, faction, hand, jammed);
    }

    public void assignFaction(Faction faction) {
        if (this.faction != null) {
            throw new FactionImmutableException(playerId);
        }
        this.faction = Objects.requireNonNull(faction, "faction must not be null");
    }

    public void dealCard(CardInstance card, int maxHandSize) {
        if (hand.size() >= maxHandSize) {
            throw new HandFullException(maxHandSize);
        }
        hand.add(Objects.requireNonNull(card, "card must not be null"));
    }

    public void removeCard(UUID cardInstanecId) {
        var removed = hand.removeIf(card -> card.cardInstanceId().equals(cardInstanecId));
        if (!removed) {
            throw new CardNotInHandException(cardInstanecId);
        }
    }

    public void applyJam() {
        jammed = true;
    }

    public void clearJam() {
        jammed = false;
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public UUID playerId() {
        return playerId;
    }

    public List<CardInstance> hand() {
        return Collections.unmodifiableList(this.hand);
    }

    public Faction faction() {
        return faction;
    }

    public boolean isJammed() {
        return jammed;
    }

    public record CardInstance(UUID cardInstanceId, CardType cardType) {}
}
