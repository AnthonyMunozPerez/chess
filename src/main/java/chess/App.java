package chess;

import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.random.RandomGenerator;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "human-human" : args[0].toLowerCase();
        try (Scanner input = new Scanner(System.in)) {
            int depth = args.length < 2 ? 3 : Integer.parseInt(args[1]);
            Player[] players = players(mode, input, depth);
            play(players[0], players[1]);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            usage();
            System.exit(2);
        }
    }

    private static Player[] players(String mode, Scanner input, int depth) {
        RandomGenerator random = new Random();
        Eval eval = Eval.loadOrDefault(Eval.userModel());
        return switch (mode) {
            case "human-human" -> new Player[]{new Human("Player 1", input), new Human("Player 2", input)};
            case "human-random" -> new Player[]{new Human("Player", input), new RandomPlayer("Random", random)};
            case "random-human" -> new Player[]{new RandomPlayer("Random", random), new Human("Player", input)};
            case "random-random" -> new Player[]{new RandomPlayer("Random 1", random), new RandomPlayer("Random 2", random)};
            case "human-engine" -> new Player[]{new Human("Player", input), new Engine("Engine", depth, 128, eval)};
            case "engine-human" -> new Player[]{new Engine("Engine", depth, 128, eval), new Human("Player", input)};
            case "engine-engine" -> new Player[]{new Engine("Engine 1", depth, 128, eval),
                    new Engine("Engine 2", depth, 128, eval)};
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }

    private static void play(Player white, Player black) {
        Game game = new Game();
        System.out.println("White: " + white.name() + " | Black: " + black.name());
        System.out.println("Enter moves like e2e4. Type 'moves' for legal moves or 'quit' to stop.\n");
        try {
            while (!game.result().finished()) {
                Position position = game.position();
                System.out.println(position.display());
                if (Moves.inCheck(position, position.sideToMove())) {
                    System.out.println("Check!");
                }
                List<Move> legal = game.legalMoves();
                Player current = position.sideToMove() == Color.WHITE ? white : black;
                game.play(current.chooseMove(position, legal));
                System.out.println();
            }
            System.out.println(game.position().display());
            System.out.println(game.result().description());
        } catch (Quit ignored) {
            System.out.println("Game ended by player.");
        }
    }

    private static void usage() {
        System.err.println("Usage: java -jar chess.jar MODE [DEPTH]");
        System.err.println("Modes: human-human, human-engine, engine-human, engine-engine,"
                + " human-random, random-human, random-random");
    }
}
