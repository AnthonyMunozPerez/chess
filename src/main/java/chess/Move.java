package chess;

public record Move(Square from, Square to, PieceType promotion) {
    public Move(Square from, Square to) {
        this(from, to, null);
    }

    public static Move parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Move is required");
        }
        String normalized = value.trim().toLowerCase().replace("-", "");
        if (normalized.length() != 4 && normalized.length() != 5) {
            throw new IllegalArgumentException("Use coordinate notation such as e2e4 or e7e8q");
        }
        PieceType promotion = normalized.length() == 5
                ? PieceType.promotion(normalized.charAt(4))
                : null;
        return new Move(
                Square.parse(normalized.substring(0, 2)),
                Square.parse(normalized.substring(2, 4)),
                promotion);
    }

    @Override
    public String toString() {
        return from + to.toString() + (promotion == null ? "" : promotion.symbol());
    }
}
