package chess;

import java.util.List;

public interface Player {
    String name();

    Move chooseMove(Position position, List<Move> legalMoves);

    default Move chooseMove(Position position, List<Move> legalMoves, SearchLimits limits) {
        return chooseMove(position, legalMoves);
    }
}
