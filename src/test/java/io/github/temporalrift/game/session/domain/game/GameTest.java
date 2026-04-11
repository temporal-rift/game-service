package io.github.temporalrift.game.session.domain.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class GameTest {

    static final int MAX_ERAS = 5;
    static final int MAX_CASCADED_PARADOXES = 3;
    static final int EVENTS_PER_ERA = 3;
    static final int NUMBER_OF_EVENTS = 30;

    Game newGame() {
        var eventIds = IntStream.range(0, NUMBER_OF_EVENTS)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        return new Game(UUID.randomUUID(), UUID.randomUUID(), new ArrayList<>(eventIds));
    }

    // --- Constructor ---

    @Test
    void constructor_nullId_throws() {
        var lobbyId = UUID.randomUUID();
        var eventIds = new ArrayList<UUID>();
        assertThatNullPointerException().isThrownBy(() -> new Game(null, lobbyId, eventIds));
    }

    @Test
    void constructor_nullLobbyId_throws() {
        var id = UUID.randomUUID();
        var eventIds = new ArrayList<UUID>();
        assertThatNullPointerException().isThrownBy(() -> new Game(id, null, eventIds));
    }

    @Test
    void constructor_nullAvailableEventIds_throws() {
        var id = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new Game(id, lobbyId, null));
    }

    @Test
    void constructor_initialStatusIsInProgress() {
        assertThat(newGame().status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    // --- startEra() ---

    @Test
    void startEra_happyPath_returnsDrawnEvents() {
        assertThat(newGame().startEra(0, EVENTS_PER_ERA)).hasSize(EVENTS_PER_ERA);
    }

    @Test
    void startEra_removesDrawnEventsFromDeck() {
        var game = newGame();
        var before = game.availableEventIds().size();
        game.startEra(0, EVENTS_PER_ERA);
        assertThat(game.availableEventIds()).hasSize(before - EVENTS_PER_ERA);
    }

    @Test
    void startEra_withCarryOver_drawsFewerEvents() {
        assertThat(newGame().startEra(1, EVENTS_PER_ERA)).hasSize(EVENTS_PER_ERA - 1);
    }

    @Test
    void startEra_incrementsEraCounter() {
        var game = newGame();
        game.startEra(0, EVENTS_PER_ERA);
        assertThat(game.eraCounter()).isEqualTo(1);
    }

    @Test
    void startEra_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(() -> game.startEra(0, EVENTS_PER_ERA));
    }

    @Test
    void startEra_notEnoughEvents_throws() {
        var game = new Game(UUID.randomUUID(), UUID.randomUUID(), new ArrayList<>());
        assertThatExceptionOfType(InsufficientDeckException.class).isThrownBy(() -> game.startEra(0, EVENTS_PER_ERA));
    }

    // --- recordCascadedParadox() ---

    @Test
    void recordCascadedParadox_incrementsCounter() {
        var game = newGame();
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        assertThat(game.cascadedParadoxCounter()).isEqualTo(1);
    }

    @Test
    void recordCascadedParadox_thirdParadox_statusBecomesEndedByCollapse() {
        var game = newGame();
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_COLLAPSE);
    }

    @Test
    void recordCascadedParadox_secondParadox_statusRemainsInProgress() {
        var game = newGame();
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        game.recordCascadedParadox(MAX_CASCADED_PARADOXES);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void recordCascadedParadox_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class)
                .isThrownBy(() -> game.recordCascadedParadox(MAX_CASCADED_PARADOXES));
    }

    // --- endEra() ---

    @Test
    void endEra_fifthEra_statusBecomesEndedByStabilization() {
        var game = newGame();
        for (int i = 0; i < MAX_ERAS; i++) {
            game.startEra(0, EVENTS_PER_ERA);
        }
        game.endEra(MAX_ERAS);
        assertThat(game.status()).isEqualTo(GameStatus.ENDED_BY_STABILIZATION);
    }

    @Test
    void endEra_notFifthEra_statusRemainsInProgress() {
        var game = newGame();
        game.startEra(0, EVENTS_PER_ERA);
        game.endEra(MAX_ERAS);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void endEra_gameOver_throws() {
        var game = newGame();
        game.end();
        assertThatExceptionOfType(GameAlreadyOverException.class).isThrownBy(() -> game.endEra(MAX_ERAS));
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
