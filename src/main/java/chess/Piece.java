package chess;

public record Piece(Color color, PieceType type) {
    public char symbol() {
        char symbol = type.symbol();
        return color == Color.WHITE ? Character.toUpperCase(symbol) : symbol;
    }
}
