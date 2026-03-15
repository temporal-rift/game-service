package com.temporalrift.game.session.domain.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class GameTest {

    static final int MAX_ERAS = 5;
    static final int MAX_CASCADED_PARADOXES = 3;
    static final int EVENTS_PER_ERA = 3;

    Game newGame() {
        var eventIds = IntStream.range(0, 30).mapToObj(_ -> UUID.randomUUID()).toList();
        return new Game(UUID.randomUUID(), UUID.randomUUID(), new ArrayList<>(eventIds),
                MAX_ERAS, MAX_CASCADED_PARADOXES, EVENTS_PER_ERA);
    }

    // --- Constructor ---

    @Test
    void constructor_nullId_throws() {
        assertThatNullPointerException().isThrownBy(() ->
                new Game(null, UUID.randomUUID(), new ArrayList<>(), MAX_ERAS, MAX_CASCADED_PARADOXES, EVENTS_PER_ERA));
    }

    @Test
    void constructor_nullLobbyId_throws() {
        assertThatNullPointerException().isThrownBy(() ->
                new Game(UUID.randomUUID(), null, new ArrayList<>(), MAX_ERAS, MAX_CASCADED_PARADOXES, EVENTS_PER_ERA));
    }

    @Test
    void constructor_nullAvailableEventIds_throws() {
        assertThatNullPointerException().isThrownBy(() ->
                new Game(UUID.randomUUID(), UUID.randomUUID(), null, MAX_ERAS, MAX_CASCADED_PARADOXES, EVENTS_PER_ERA));
    }

    @Test
    void constructor_initialStatusIsInProgress() {
        assertThat(newGame().status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    // --- startEra() ---

    @Test
    void startEra_happyPath_returnsDrawnEvents() {
        assertThat(newGame().startEra(0)).hasSize(EVENTS_PER_ERA);
    }

    @Test
    void startEra_removesDrawnEventsFromDeck() {
        var game = newGame();
        var before = game.availableEventIds().size();
        game.startEra(0);
        assertThat(game.availableEventIds()).hasSize(before - EVENTS_PER_ERA);
    }

    @Test
    void startEra_withCarryOver_drawsFewerEvents() {
        assertThat(newGame().startEra(1)).hasSize(EVENTS_PER_ERA - 1);
    }

    @Test
    void startEra_incrementsEraCounter() {
        var game = newGame();
        game.startEra(0);
        assertThat(game.eraCounter()).isEqualTo(1);
    }

    @Test
    void startEra_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(() -> game.startEra(0));
    }

    @Test
    void startEra_notEnoughEvents_throws() {
        var game = new Game(UUID.randomUUID(), UUID.randomUUID(), new ArrayList<>(),
                MAX_ERAS, MAX_CASCADED_PARADOXES, EVENTS_PER_ERA);
        assertThatExceptionOfType(InsufficientDeckException.class).isThrownBy(() -> game.startEra(0));
    }

    // --- recordCascadedParadox() ---

    @Test
    void recordCascadedParadox_incrementsCounter() {
        var game = newGame();
        game.recordCascadedParadox();
        assertThat(game.cascadedParadoxCounter()).isEqualTo(1);
    }

    @Test
    void recordCascadedParadox_thirdParadox_statusBecomesEndedByCollapse() {
        var game = newGame();
        game.recordCascadedParadox();
        game.recordCascadedParadox();
        game.recordCascadedParadox();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_COLLAPSE);
    }

    @Test
    void recordCascadedParadox_secondParadox_statusRemainsInProgress() {
        var game = newGame();
        game.recordCascadedParadox();
        game.recordCascadedParadox();
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void recordCascadedParadox_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(game::recordCascadedParadox);
    }

    // --- endEra() ---

    @Test
    void endEra_fifthEra_statusBecomesEndedByStabilization() {
        var game = newGame();
        for (int i = 0; i < MAX_ERAS; i++) game.startEra(0);
        game.endEra();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_STABILIZATION);
    }

    @Test
    void endEra_notFifthEra_statusRemainsInProgress() {
        var game = newGame();
        game.startEra(0);
        game.endEra();
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void endEra_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(game::endEra);
    }

    // --- end() ---

    @Test
    void end_statusBecomesEndedByWin() {
        var game = newGame();
        game.end();
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_WIN);
    }

    @Test
    void end_gameAlreadyOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(game::end);
    }
}
