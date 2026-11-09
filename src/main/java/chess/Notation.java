package chess;

import java.util.List;

final class Notation {
    private Notation() {
    }

    static String san(Position position, Move move, List<Move> legal) {
        Piece piece = position.pieceAt(move.from());
        boolean castle = piece.type() == PieceType.KING
                && Math.abs(move.to().file() - move.from().file()) == 2;
        StringBuilder text = new StringBuilder();
        if (castle) {
            text.append(move.to().file() == 6 ? "O-O" : "O-O-O");
        } else {
            boolean capture = position.pieceAt(move.to()) != null
                    || piece.type() == PieceType.PAWN && move.to().equals(position.enPassant());
            if (piece.type() != PieceType.PAWN) {
                text.append(Character.toUpperCase(piece.type().symbol()));
                appendDisambiguation(text, position, move, legal, piece);
            } else if (capture) {
                text.append((char) ('a' + move.from().file()));
            }
            if (capture) text.append('x');
            text.append(move.to());
            if (move.promotion() != null) {
                text.append('=').append(Character.toUpperCase(move.promotion().symbol()));
            }
        }
        Position next = position.applyUnchecked(move);
        if (Moves.inCheck(next, next.sideToMove())) {
            text.append(Moves.legal(next).isEmpty() ? '#' : '+');
        }
        return text.toString();
    }

    private static void appendDisambiguation(StringBuilder text, Position position, Move move,
                                             List<Move> legal, Piece piece) {
        List<Move> others = legal.stream().filter(candidate -> !candidate.equals(move)
                && candidate.to().equals(move.to())
                && piece.equals(position.pieceAt(candidate.from()))).toList();
        if (others.isEmpty()) return;
        boolean sameFile = others.stream().anyMatch(candidate -> candidate.from().file() == move.from().file());
        boolean sameRank = others.stream().anyMatch(candidate -> candidate.from().rank() == move.from().rank());
        if (!sameFile) text.append((char) ('a' + move.from().file()));
        else if (!sameRank) text.append(move.from().rank() + 1);
        else text.append(move.from());
    }
}
