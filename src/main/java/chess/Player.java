package chess;

import java.util.List;

public interface Player {
    String name();

    Move chooseMove(Position position, List<Move> legalMoves);
}
