package io.github.temporalrift.game.session.domain.futureevent;

public record ProbabilityBounds(int floor, int ceiling) {

    public ProbabilityBounds {
        if (floor < 0 || ceiling > 100 || floor >= ceiling) {
            throw new IllegalArgumentException("Probability bounds must satisfy 0 <= floor < ceiling <= 100");
        }
    }

    public boolean contains(int probability) {
        return probability >= floor && probability <= ceiling;
    }
}
