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

    @Test
    void returnsImmediatelyWhenOnlyOneMoveIsLegal() {
        Position position = Position.fromFen("7k/8/8/8/8/8/6r1/7K w - - 0 1");
        List<Move> legal = Moves.legal(position);

        SearchResult result = new Engine("Test", 64, 1)
                .search(position, legal, new SearchLimits(64, Long.MAX_VALUE, 100, 200));

        assertEquals(1, legal.size());
        assertEquals(Move.parse("h1g2"), result.move());
        assertEquals(0, result.stats().depth());
    }

    @Test
    void hardDeadlineReturnsTheLastCompletedIteration() {
        Position position = Position.initial();
        List<Move> legal = Moves.legal(position);

        SearchResult result = new Engine("Test", 64, 4)
                .search(position, legal, new SearchLimits(64, Long.MAX_VALUE, 5, 20));

        assertTrue(legal.contains(result.move()));
        assertTrue(result.stats().elapsedMillis() < 1_000);
    }
}
