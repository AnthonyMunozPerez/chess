package chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTest {
    @Test
    void detectsFoolsMate() {
        Game game = new Game();
        play(game, "f2f3", "e7e5", "g2g4", "d8h4");

        assertEquals(Result.Status.CHECKMATE, game.result().status());
        assertEquals(Color.BLACK, game.result().winner());
    }

    @Test
    void detectsStalemate() {
        Game game = new Game(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"));

        assertEquals(Result.Status.STALEMATE, game.result().status());
    }

    @Test
    void detectsThreefoldRepetition() {
        Game game = new Game();
        play(game,
                "g1f3", "g8f6", "f3g1", "f6g8",
                "g1f3", "g8f6", "f3g1", "f6g8");

        assertEquals(Result.Status.REPETITION, game.result().status());
    }

    @Test
    void detectsFiftyMoveRule() {
        Game game = new Game(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 100 51"));

        assertEquals(Result.Status.FIFTY_MOVE, game.result().status());
    }

    @Test
    void detectsInsufficientMaterial() {
        Game game = new Game(Position.fromFen("4k3/8/8/8/8/8/8/2B1K3 w - - 0 1"));

        assertEquals(Result.Status.INSUFFICIENT_MATERIAL, game.result().status());
    }

    private static void play(Game game, String... moves) {
        for (String move : moves) {
            game.play(Move.parse(move));
        }
    }
}
