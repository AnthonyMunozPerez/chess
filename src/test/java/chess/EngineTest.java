package chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {
    @Test
    void choosesALegalMove() {
        Position position = Position.initial();
        List<Move> legal = Moves.legal(position);

        Move chosen = new Engine("Test", 2).chooseMove(position, legal);

        assertTrue(legal.contains(chosen));
    }

    @Test
    void findsMateInOne() {
        Position position = Position.fromFen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        Move chosen = new Engine("Test", 2).chooseMove(position, Moves.legal(position));
        Position next = position.applyUnchecked(chosen);

        assertTrue(Moves.legal(next).isEmpty());
        assertTrue(Moves.inCheck(next, Color.BLACK));
    }

    @Test
    void takesAnUnprotectedQueen() {
        Position position = Position.fromFen("q3k3/8/8/8/8/8/8/R3K3 w - - 0 1");

        Move chosen = new Engine("Test", 1).chooseMove(position, Moves.legal(position));

        assertEquals(Move.parse("a1a8"), chosen);
    }
}
