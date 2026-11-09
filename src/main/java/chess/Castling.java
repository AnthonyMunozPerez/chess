package chess;

public record Castling(boolean whiteKing, boolean whiteQueen, boolean blackKing, boolean blackQueen) {
    public static Castling initial() {
        return new Castling(true, true, true, true);
    }

    public static Castling none() {
        return new Castling(false, false, false, false);
    }

    public boolean kingSide(Color color) {
        return color == Color.WHITE ? whiteKing : blackKing;
    }

    public boolean queenSide(Color color) {
        return color == Color.WHITE ? whiteQueen : blackQueen;
    }

    public Castling withoutKing(Color color) {
        return color == Color.WHITE
                ? new Castling(false, false, blackKing, blackQueen)
                : new Castling(whiteKing, whiteQueen, false, false);
    }

    public Castling withoutRook(Color color, boolean kingSide) {
        if (color == Color.WHITE) {
            return kingSide
                    ? new Castling(false, whiteQueen, blackKing, blackQueen)
                    : new Castling(whiteKing, false, blackKing, blackQueen);
        }
        return kingSide
                ? new Castling(whiteKing, whiteQueen, false, blackQueen)
                : new Castling(whiteKing, whiteQueen, blackKing, false);
    }

    @Override
    public String toString() {
        String value = (whiteKing ? "K" : "") + (whiteQueen ? "Q" : "")
                + (blackKing ? "k" : "") + (blackQueen ? "q" : "");
        return value.isEmpty() ? "-" : value;
    }
}
