package chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovesTest {
    @Test
    void initialPositionHasExpectedPerftCounts() {
        Position position = Position.initial();

        assertEquals(20, perft(position, 1));
        assertEquals(400, perft(position, 2));
        assertEquals(8_902, perft(position, 3));
    }

    @Test
    void tacticalPositionHasExpectedPerftCounts() {
        Position position = Position.fromFen(
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");

        assertEquals(48, perft(position, 1));
        assertEquals(2_039, perft(position, 2));
        assertEquals(97_862, perft(position, 3));
    }

    @Test
    void castlingMovesTheRookAndKing() {
        Position position = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Move castle = Move.parse("e1g1");

        assertTrue(Moves.legal(position).contains(castle));
        Position next = position.applyUnchecked(castle);
        assertEquals(new Piece(Color.WHITE, PieceType.KING), next.pieceAt(Square.parse("g1")));
        assertEquals(new Piece(Color.WHITE, PieceType.ROOK), next.pieceAt(Square.parse("f1")));
        assertFalse(next.castling().kingSide(Color.WHITE));
        assertFalse(next.castling().queenSide(Color.WHITE));
    }

    @Test
    void enPassantRemovesThePassedPawn() {
        Game game = new Game();
        play(game, "e2e4", "a7a6", "e4e5", "d7d5", "e5d6");

        assertEquals(new Piece(Color.WHITE, PieceType.PAWN), game.position().pieceAt(Square.parse("d6")));
        assertEquals(null, game.position().pieceAt(Square.parse("d5")));
    }

    @Test
    void pawnOffersAllPromotionChoices() {
        Position position = Position.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        List<Move> legal = Moves.legal(position);

        assertTrue(legal.contains(Move.parse("a7a8q")));
        assertTrue(legal.contains(Move.parse("a7a8r")));
        assertTrue(legal.contains(Move.parse("a7a8b")));
        assertTrue(legal.contains(Move.parse("a7a8n")));
    }

    @Test
    void pinnedPieceCannotExposeItsKing() {
        Position position = Position.fromFen("4r1k1/8/8/8/8/8/4R3/4K3 w - - 0 1");

        assertFalse(Moves.legal(position).contains(Move.parse("e2d2")));
        assertTrue(Moves.legal(position).contains(Move.parse("e2e8")));
    }

    private static long perft(Position position, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move move : Moves.legal(position)) {
            nodes += perft(position.applyUnchecked(move), depth - 1);
        }
        return nodes;
    }

    private static void play(Game game, String... moves) {
        for (String move : moves) {
            game.play(Move.parse(move));
        }
    }
}
