package chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotationTest {
    @Test
    void writesPawnMovesCastlingAndCheckmate() {
        Game game = new Game();
        assertEquals("e4", san(game, "e2e4"));
        play(game, "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6");
        assertEquals("O-O", san(game, "e1g1"));

        game = new Game();
        play(game, "e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6");
        assertEquals("Qxf7#", san(game, "h5f7"));
    }

    private static String san(Game game, String move) {
        return Notation.san(game.position(), Move.parse(move), game.legalMoves());
    }

    private static void play(Game game, String... moves) {
        for (String move : moves) game.play(Move.parse(move));
    }
}
