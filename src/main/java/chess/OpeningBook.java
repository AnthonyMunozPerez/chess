package chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class OpeningBook {
    public static final int DEPTH = 16;
    private final List<Line> lines;
    private final Map<String, List<Move>> moves = new LinkedHashMap<>();

    public OpeningBook() {
        lines = List.of(
                line("London", "Classical", "d2d4 d7d5 g1f3 g8f6 c1f4 e7e6 e2e3 f8d6 f4g3 e8g8 f1d3 c7c5 c2c3 b8c6 b1d2 d8c7"),
                line("London", "Early c5", "d2d4 g8f6 g1f3 e7e6 c1f4 d7d5 e2e3 c7c5 c2c3 b8c6 b1d2 f8d6 f4g3 e8g8 f1d3 b7b6"),
                line("London", "Queen exchange", "d2d4 d7d5 c1f4 g8f6 e2e3 c7c5 c2c3 b8c6 g1f3 d8b6 d1b3 c5c4 b3b6 a7b6 b1d2 b6b5"),
                line("London", "Kingside fianchetto", "d2d4 g8f6 g1f3 g7g6 c1f4 f8g7 e2e3 e8g8 f1e2 d7d6 e1g1 b8d7 h2h3 d8e8 f4h2 e7e5"),

                line("King's Indian Attack", "Classical", "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6 e1g1 f8e7 d2d3 e8g8 b1d2 c7c5 e2e4 b8c6 f1e1 b7b6"),
                line("King's Indian Attack", "Symmetrical", "g1f3 g8f6 g2g3 g7g6 f1g2 f8g7 e1g1 e8g8 d2d3 d7d6 b1d2 e7e5 e2e4 b8c6 c2c3 a7a5"),
                line("King's Indian Attack", "French setup", "g1f3 d7d5 g2g3 c7c5 f1g2 b8c6 e1g1 e7e5 d2d3 g8f6 b1d2 f8e7 e2e4 e8g8 f1e1 d5d4"),
                line("King's Indian Attack", "Sicilian setup", "g1f3 c7c5 g2g3 b8c6 f1g2 g7g6 e1g1 f8g7 d2d3 e7e6 b1d2 g8e7 e2e4 e8g8 c2c3 d7d5"),

                line("Italian Game", "Giuoco Piano", "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6 e1g1 e8g8 f1e1 a7a6 c4b3 c5a7"),
                line("Italian Game", "Two Knights", "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 e1g1 d7d6 c2c3 e8g8 f1e1 a7a6 c4b3 c5a7"),
                line("Italian Game", "Evans Gambit", "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b2b4 c5b4 c2c3 b4a5 d2d4 e5d4 e1g1 d7d6 c3d4 a5b6"),
                line("Italian Game", "Fried Liver", "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 d7d5 e4d5 c6a5 c4b5 c7c6 d5c6 b7c6 b5d3 h7h6"),

                line("Modern Defense", "Standard", "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6 f2f4 a7a6 g1f3 b7b5 f1d3 c8b7 e1g1 b8d7 c1e3 c7c5"),
                line("Modern Defense", "Queenside expansion", "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6 g1f3 a7a6 a2a4 b7b6 f1d3 c8b7 e1g1 b8d7 f1e1 e7e6"),
                line("Modern Defense", "150 Attack", "d2d4 g7g6 e2e4 f8g7 b1c3 d7d6 c1e3 a7a6 d1d2 b7b5 f2f3 c8b7 e1c1 b8d7 h2h4 h7h5"),
                line("Modern Defense", "Gurgenidze", "e2e4 g7g6 d2d4 f8g7 b1c3 c7c6 f2f4 d7d5 g1f3 g8h6 f1d3 e8g8 e1g1 c8g4 c1e3 e7e6"),

                line("Alekhine Defense", "Main line", "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6 g1f3 d6e5 f3e5 c7c6 f1e2 c8f5 e1g1 b8d7 c2c4 d5b4"),
                line("Alekhine Defense", "Exchange", "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6 c2c4 d5b6 e5d6 c7d6 b1c3 g7g6 c1e3 f8g7 f1e2 e8g8"),
                line("Alekhine Defense", "Four Pawns", "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6 g1f3 c8g4 f1e2 e7e6 e1g1 f8e7 c2c4 d5b6 b1c3 e8g8"),
                line("Alekhine Defense", "Modern", "e2e4 g8f6 e4e5 f6d5 c2c4 d5b6 d2d4 d7d6 g1f3 g7g6 b1c3 f8g7 f1e2 e8g8 e1g1 b8c6"),

                line("Pirc Defense", "Austrian Attack", "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4 f8g7 g1f3 e8g8 f1d3 b8a6 e1g1 c7c5 d4d5 a6c7"),
                line("Pirc Defense", "150 Attack", "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 c1e3 f8g7 d1d2 e8g8 e1c1 c7c6 f2f3 b7b5 h2h4 h7h5"),
                line("Pirc Defense", "Classical", "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 g1f3 f8g7 f1e2 e8g8 e1g1 c7c6 a2a4 b8d7 f1e1 e7e5"),
                line("Pirc Defense", "Argentine Attack", "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f3 f8g7 c1e3 e8g8 d1d2 c7c6 g2g4 b7b5 h2h4 h7h5")
        );
        index();
    }

    public List<Line> lines() {
        return lines;
    }

    public Move choose(Position position, List<Move> legal, RandomGenerator random) {
        List<Move> candidates = moves.get(position.repetitionKey());
        if (candidates == null || candidates.isEmpty()) return null;
        List<Move> valid = candidates.stream().filter(legal::contains).toList();
        return valid.isEmpty() ? null : valid.get(random.nextInt(valid.size()));
    }

    private void index() {
        for (Line line : lines) {
            Position position = Position.initial();
            for (Move move : line.moves()) {
                moves.computeIfAbsent(position.repetitionKey(), ignored -> new ArrayList<>()).add(move);
                position = position.applyUnchecked(move);
            }
        }
    }

    private static Line line(String family, String name, String text) {
        List<Move> parsed = List.of(text.split(" ")).stream().map(Move::parse).toList();
        if (parsed.size() != DEPTH) throw new IllegalArgumentException(name + " must contain 16 plies");
        Game game = new Game();
        for (Move move : parsed) game.play(move);
        return new Line(family, name, parsed);
    }

    public record Line(String family, String name, List<Move> moves) {
        public Line {
            moves = List.copyOf(moves);
        }

        public String label() {
            return family + " — " + name;
        }
    }
}
