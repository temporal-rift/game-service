package io.github.temporalrift.game.action.domain.handselection;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import io.github.temporalrift.game.shared.HandDealt;
import io.github.temporalrift.game.shared.HandSelected;

public record HandSelection(
        UUID id,
        UUID gameId,
        int eraNumber,
        UUID playerId,
        HandSelectionStatus status,
        Instant selectionExpiresAt,
        List<HandDealt.CardInstance> dealtCards,
        List<HandDealt.CardInstance> selectedCards,
        HandSelected.SelectionOrigin selectionOrigin) {

    public static HandSelection open(HandDealt event) {
        return new HandSelection(
                UUID.nameUUIDFromBytes((event.gameId() + ":" + event.eraNumber() + ":" + event.playerId())
                        .getBytes(StandardCharsets.UTF_8)),
                event.gameId(),
                event.eraNumber(),
                event.playerId(),
                HandSelectionStatus.OPEN,
                event.selectionExpiresAt(),
                List.copyOf(event.cards()),
                List.of(),
                null);
    }

    public HandSelection select(Set<UUID> keptCardInstanceIds, Instant now) {
        assertOpen(now);
        if (keptCardInstanceIds.size() != 5
                || !dealtCards.stream()
                        .map(HandDealt.CardInstance::cardInstanceId)
                        .collect(java.util.stream.Collectors.toSet())
                        .containsAll(keptCardInstanceIds)) {
            throw new InvalidHandSelectionException();
        }
        return resolved(
                dealtCards.stream()
                        .filter(card -> keptCardInstanceIds.contains(card.cardInstanceId()))
                        .toList(),
                HandSelected.SelectionOrigin.PLAYER);
    }

    public HandSelection selectRandomOnExpiry(Instant now, RandomGenerator random) {
        if (status != HandSelectionStatus.OPEN || now.isBefore(selectionExpiresAt)) {
            return this;
        }
        var firstDiscardIndex = random.nextInt(dealtCards.size());
        var secondDiscardIndex = random.nextInt(dealtCards.size() - 1);
        if (secondDiscardIndex >= firstDiscardIndex) {
            secondDiscardIndex++;
        }
        var finalSecondDiscardIndex = secondDiscardIndex;
        return resolved(
                java.util.stream.IntStream.range(0, dealtCards.size())
                        .filter(index -> index != firstDiscardIndex && index != finalSecondDiscardIndex)
                        .mapToObj(dealtCards::get)
                        .toList(),
                HandSelected.SelectionOrigin.TIMEOUT_RANDOM);
    }

    public HandSelected toEvent() {
        return new HandSelected(gameId, eraNumber, playerId, selectionOrigin, selectedCards);
    }

    private void assertOpen(Instant now) {
        if (status == HandSelectionStatus.SELECTED) {
            throw new HandSelectionAlreadyResolvedException(gameId, eraNumber, playerId);
        }
        if (!now.isBefore(selectionExpiresAt)) {
            throw new HandSelectionNotOpenException(gameId, eraNumber, playerId);
        }
    }

    private HandSelection resolved(List<HandDealt.CardInstance> cards, HandSelected.SelectionOrigin origin) {
        return new HandSelection(
                id,
                gameId,
                eraNumber,
                playerId,
                HandSelectionStatus.SELECTED,
                selectionExpiresAt,
                dealtCards,
                List.copyOf(cards),
                origin);
    }
}
