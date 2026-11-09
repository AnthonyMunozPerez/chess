package chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBoardTest {
    @Test
    void matchesReferenceMoveGeneratorFromInitialPosition() {
        SearchBoard board = new SearchBoard(Position.initial());

        assertEquals(20, perft(board, 1));
        assertEquals(400, perft(board, 2));
        assertEquals(8_902, perft(board, 3));
    }

    @Test
    void matchesKiwipeteIncludingCastlingAndTactics() {
        Position position = Position.fromFen(
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        SearchBoard board = new SearchBoard(position);

        assertEquals(48, perft(board, 1));
        assertEquals(2_039, perft(board, 2));
        assertEquals(97_862, perft(board, 3));
    }

    @Test
    void undoRestoresPositionAndHash() {
        SearchBoard board = new SearchBoard(Position.initial());
        long hash = board.hash();
        int score = board.evaluate();

        SearchBoard.Undo undo = board.make(Move.parse("e2e4"));
        board.undo(undo);

        assertEquals(hash, board.hash());
        assertEquals(score, board.evaluate());
        assertEquals(20, board.legalMoves().size());
    }

    @Test
    void incrementalHashMatchesARebuiltBoard() {
        Position position = Position.initial();
        SearchBoard board = new SearchBoard(position);
        for (String text : List.of("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4")) {
            Move move = Move.parse(text);
            board.make(move);
            position = position.applyUnchecked(move);
            assertEquals(new SearchBoard(position).hash(), board.hash());
        }
    }

    @Test
    void staticExchangeRejectsAObviouslyLosingCapture() {
        SearchBoard board = new SearchBoard(Position.fromFen("3rk3/8/8/3p4/4Q3/8/8/4K3 w - - 0 1"));
        assertTrue(board.staticExchange(Move.parse("e4d5")) < 0);
    }

    private static long perft(SearchBoard board, int depth) {
        if (depth == 0) return 1;
        long nodes = 0;
        for (Move move : board.legalMoves()) {
            SearchBoard.Undo undo = board.make(move);
            nodes += perft(board, depth - 1);
            board.undo(undo);
        }
        return nodes;
    }
}
