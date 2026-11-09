package chess;

import java.util.List;
import java.util.random.RandomGenerator;

public final class RandomPlayer implements Player {
    private final String name;
    private final RandomGenerator random;

    public RandomPlayer(String name, RandomGenerator random) {
        this.name = name;
        this.random = random;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Move chooseMove(Position position, List<Move> legalMoves) {
        Move move = legalMoves.get(random.nextInt(legalMoves.size()));
        System.out.println(name + " plays " + move);
        return move;
    }
}
