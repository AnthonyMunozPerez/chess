package chess;

public enum PieceType {
    KING('k'), QUEEN('q'), ROOK('r'), BISHOP('b'), KNIGHT('n'), PAWN('p');

    private final char symbol;

    PieceType(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    public static PieceType promotion(char symbol) {
        return switch (Character.toLowerCase(symbol)) {
            case 'q' -> QUEEN;
            case 'r' -> ROOK;
            case 'b' -> BISHOP;
            case 'n' -> KNIGHT;
            default -> throw new IllegalArgumentException("Promotion must be q, r, b, or n");
        };
    }
}
