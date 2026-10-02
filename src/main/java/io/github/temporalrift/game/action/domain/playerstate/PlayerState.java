package io.github.temporalrift.game.action.domain.playerstate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.CardNotInHandException;
import io.github.temporalrift.game.action.domain.activisterastate.ActivistDeclarationMode;
import io.github.temporalrift.game.shared.domain.AggregateRoot;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;

public class PlayerState extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "PlayerState";

    private final UUID id;
    private final UUID gameId;
    private final UUID playerId;
    private final List<CardInstance> hand;
    // Hand cards an Intercept has revealed this era; always a subset of the hand.
    private final Set<UUID> revealedCardInstanceIds;
    private Faction faction;
    private boolean jammed;
    private boolean obscured;

    public PlayerState(UUID id, UUID gameId, UUID playerId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.faction = null;
        this.hand = new ArrayList<>();
        this.revealedCardInstanceIds = new LinkedHashSet<>();
        this.jammed = false;
        this.obscured = false;
    }

    private PlayerState(UUID id, UUID gameId, UUID playerId, Faction faction, PersistedState state) {
        this.id = id;
        this.gameId = gameId;
        this.playerId = playerId;
        this.faction = faction;
        this.hand = new ArrayList<>(state.hand());
        this.revealedCardInstanceIds = new LinkedHashSet<>(state.revealedCardInstanceIds());
        this.revealedCardInstanceIds.retainAll(cardInstanceIds(state.hand()));
        this.jammed = state.jammed();
        this.obscured = state.obscured();
    }

    public static PlayerState reconstitute(UUID id, UUID gameId, UUID playerId, Faction faction, PersistedState state) {
        return new PlayerState(id, gameId, playerId, faction, state);
    }

    /** The mutable per-player state a repository restores alongside the player's identity and faction. */
    public record PersistedState(
            List<CardInstance> hand, Set<UUID> revealedCardInstanceIds, boolean jammed, boolean obscured) {

        public PersistedState {
            hand = List.copyOf(hand);
            revealedCardInstanceIds = Set.copyOf(revealedCardInstanceIds);
        }
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

    public void removeCard(UUID cardInstanceId) {
        var removed = hand.removeIf(card -> card.cardInstanceId().equals(cardInstanceId));
        if (!removed) {
            throw new CardNotInHandException(cardInstanceId);
        }
        revealedCardInstanceIds.remove(cardInstanceId);
    }

    /** Replaces the hand with a newly selected one; nothing of the new hand has been revealed yet. */
    public void receiveHand(List<CardInstance> cards) {
        hand.clear();
        hand.addAll(List.copyOf(cards));
        revealedCardInstanceIds.clear();
    }

    /** Records that an Intercept revealed these cards; cards no longer in the hand are ignored. */
    public void markRevealed(Collection<CardInstance> cards) {
        var inHand = cardInstanceIds(hand);
        cards.stream()
                .map(CardInstance::cardInstanceId)
                .filter(inHand::contains)
                .forEach(revealedCardInstanceIds::add);
    }

    public void applyJam() {
        jammed = true;
    }

    public void clearJam() {
        jammed = false;
    }

    public void applyObscure() {
        obscured = true;
    }

    public void clearObscure() {
        obscured = false;
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

    /** Hand cards already revealed by an Intercept this era, in hand order. */
    public List<CardInstance> revealedCards() {
        return hand.stream()
                .filter(card -> revealedCardInstanceIds.contains(card.cardInstanceId()))
                .toList();
    }

    public Faction faction() {
        return faction;
    }

    public boolean isJammed() {
        return jammed;
    }

    /** The modes this player may declare in an open declaration window; empty for anyone but an unjammed Activist. */
    public List<ActivistDeclarationMode> eligibleDeclarationModes(boolean momentumEligible) {
        if (faction != Faction.ACTIVISTS || jammed) {
            return List.of();
        }
        return momentumEligible
                ? List.of(ActivistDeclarationMode.RALLY, ActivistDeclarationMode.MOMENTUM)
                : List.of(ActivistDeclarationMode.RALLY);
    }

    public boolean isObscured() {
        return obscured;
    }

    private static Set<UUID> cardInstanceIds(List<CardInstance> cards) {
        var ids = new LinkedHashSet<UUID>();
        cards.forEach(card -> ids.add(card.cardInstanceId()));
        return ids;
    }

    public record CardInstance(UUID cardInstanceId, CardType cardType, CardGrade grade) {

        public CardInstance(UUID cardInstanceId, CardType cardType) {
            this(cardInstanceId, cardType, CardGrade.I);
        }
    }
}
