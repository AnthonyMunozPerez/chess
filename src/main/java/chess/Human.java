package chess;

import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public final class Human implements Player {
    private final String name;
    private final Scanner input;

    public Human(String name, Scanner input) {
        this.name = name;
        this.input = input;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Move chooseMove(Position position, List<Move> legalMoves) {
        while (true) {
            System.out.print(name + " (" + displayColor(position.sideToMove()) + ") move: ");
            if (!input.hasNextLine()) {
                throw new Quit();
            }
            String value = input.nextLine().trim();
            if (value.equalsIgnoreCase("quit") || value.equalsIgnoreCase("exit")) {
                throw new Quit();
            }
            if (value.equalsIgnoreCase("moves")) {
                System.out.println(legalMoves.stream()
                        .sorted(Comparator.comparing(Move::toString))
                        .map(Move::toString)
                        .toList());
                continue;
            }
            try {
                Move move = Move.parse(value);
                if (legalMoves.contains(move)) {
                    return move;
                }
                System.out.println("That move is not legal. Type 'moves' to list legal moves.");
            } catch (IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    private static String displayColor(Color color) {
        return color == Color.WHITE ? "White" : "Black";
    }
}
