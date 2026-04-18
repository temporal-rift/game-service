package io.github.temporalrift.game.session.domain.lobby;

public class NotEnoughPlayersException extends RuntimeException {

    public NotEnoughPlayersException() {
        super("Not enough players to start the game");
    }

    public NotEnoughPlayersException(int currentPlayerCount, int minPlayers) {
        super("Not enough players to start the game: " + currentPlayerCount + " < " + minPlayers);
    }
}
