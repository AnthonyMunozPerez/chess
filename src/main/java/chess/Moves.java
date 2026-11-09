package chess;

import java.util.ArrayList;
import java.util.List;

public final class Moves {
    private static final int[][] KNIGHT_STEPS = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2},
            {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_STEPS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1},
            {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    private static final int[][] DIAGONALS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] ORTHOGONALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Moves() {
    }

    public static List<Move> legal(Position position) {
        Color movingColor = position.sideToMove();
        List<Move> legal = new ArrayList<>();
        for (Move move : pseudoLegal(position)) {
            Position next = position.applyUnchecked(move);
            if (!inCheck(next, movingColor)) {
                legal.add(move);
            }
        }
        return List.copyOf(legal);
    }

    public static boolean inCheck(Position position, Color color) {
        Square king = findKing(position, color);
        return attacked(position, king, color.opposite());
    }

    public static boolean attacked(Position position, Square target, Color attacker) {
        int pawnSourceRank = target.rank() - attacker.pawnDirection();
        for (int fileOffset : new int[]{-1, 1}) {
            Piece piece = position.pieceAt(target.file() + fileOffset, pawnSourceRank);
            if (is(piece, attacker, PieceType.PAWN)) {
                return true;
            }
        }

        for (int[] step : KNIGHT_STEPS) {
            Piece piece = position.pieceAt(target.file() + step[0], target.rank() + step[1]);
            if (is(piece, attacker, PieceType.KNIGHT)) {
                return true;
            }
        }

        for (int[] step : KING_STEPS) {
            Piece piece = position.pieceAt(target.file() + step[0], target.rank() + step[1]);
            if (is(piece, attacker, PieceType.KING)) {
                return true;
            }
        }

        return attackedBySlider(position, target, attacker, DIAGONALS, PieceType.BISHOP)
                || attackedBySlider(position, target, attacker, ORTHOGONALS, PieceType.ROOK);
    }

    private static List<Move> pseudoLegal(Position position) {
        List<Move> moves = new ArrayList<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.pieceAt(file, rank);
                if (piece == null || piece.color() != position.sideToMove()) {
                    continue;
                }
                Square from = new Square(file, rank);
                switch (piece.type()) {
                    case PAWN -> addPawnMoves(position, from, piece.color(), moves);
                    case KNIGHT -> addSteps(position, from, piece.color(), KNIGHT_STEPS, moves);
                    case BISHOP -> addSlides(position, from, piece.color(), DIAGONALS, moves);
                    case ROOK -> addSlides(position, from, piece.color(), ORTHOGONALS, moves);
                    case QUEEN -> {
                        addSlides(position, from, piece.color(), DIAGONALS, moves);
                        addSlides(position, from, piece.color(), ORTHOGONALS, moves);
                    }
                    case KING -> {
                        addSteps(position, from, piece.color(), KING_STEPS, moves);
                        addCastles(position, from, piece.color(), moves);
                    }
                }
            }
        }
        return moves;
    }

    private static void addPawnMoves(Position position, Square from, Color color, List<Move> moves) {
        int direction = color.pawnDirection();
        int nextRank = from.rank() + direction;
        if (position.pieceAt(from.file(), nextRank) == null) {
            addPawnMove(from, new Square(from.file(), nextRank), color, moves);
            int doubleRank = from.rank() + 2 * direction;
            if (from.rank() == color.pawnStartRank()
                    && position.pieceAt(from.file(), doubleRank) == null) {
                moves.add(new Move(from, new Square(from.file(), doubleRank)));
            }
        }

        for (int fileOffset : new int[]{-1, 1}) {
            int targetFile = from.file() + fileOffset;
            if (!Square.isValid(targetFile, nextRank)) {
                continue;
            }
            Square to = new Square(targetFile, nextRank);
            Piece target = position.pieceAt(to);
            boolean capture = target != null && target.color() != color && target.type() != PieceType.KING;
            boolean enPassant = to.equals(position.enPassant())
                    && is(position.pieceAt(targetFile, from.rank()), color.opposite(), PieceType.PAWN);
            if (capture || enPassant) {
                addPawnMove(from, to, color, moves);
            }
        }
    }

    private static void addPawnMove(Square from, Square to, Color color, List<Move> moves) {
        if (to.rank() == color.promotionRank()) {
            moves.add(new Move(from, to, PieceType.QUEEN));
            moves.add(new Move(from, to, PieceType.ROOK));
            moves.add(new Move(from, to, PieceType.BISHOP));
            moves.add(new Move(from, to, PieceType.KNIGHT));
        } else {
            moves.add(new Move(from, to));
        }
    }

    private static void addSteps(Position position, Square from, Color color,
                                 int[][] steps, List<Move> moves) {
        for (int[] step : steps) {
            int file = from.file() + step[0];
            int rank = from.rank() + step[1];
            if (!Square.isValid(file, rank)) {
                continue;
            }
            Piece target = position.pieceAt(file, rank);
            if (target == null || (target.color() != color && target.type() != PieceType.KING)) {
                moves.add(new Move(from, new Square(file, rank)));
            }
        }
    }

    private static void addSlides(Position position, Square from, Color color,
                                  int[][] directions, List<Move> moves) {
        for (int[] direction : directions) {
            int file = from.file() + direction[0];
            int rank = from.rank() + direction[1];
            while (Square.isValid(file, rank)) {
                Piece target = position.pieceAt(file, rank);
                if (target == null) {
                    moves.add(new Move(from, new Square(file, rank)));
                } else {
                    if (target.color() != color && target.type() != PieceType.KING) {
                        moves.add(new Move(from, new Square(file, rank)));
                    }
                    break;
                }
                file += direction[0];
                rank += direction[1];
            }
        }
    }

    private static void addCastles(Position position, Square from, Color color, List<Move> moves) {
        int rank = color.homeRank();
        if (!from.equals(new Square(4, rank)) || inCheck(position, color)) {
            return;
        }
        Color opponent = color.opposite();
        if (position.castling().kingSide(color)
                && position.pieceAt(5, rank) == null
                && position.pieceAt(6, rank) == null
                && is(position.pieceAt(7, rank), color, PieceType.ROOK)
                && !attacked(position, new Square(5, rank), opponent)
                && !attacked(position, new Square(6, rank), opponent)) {
            moves.add(new Move(from, new Square(6, rank)));
        }
        if (position.castling().queenSide(color)
                && position.pieceAt(1, rank) == null
                && position.pieceAt(2, rank) == null
                && position.pieceAt(3, rank) == null
                && is(position.pieceAt(0, rank), color, PieceType.ROOK)
                && !attacked(position, new Square(3, rank), opponent)
                && !attacked(position, new Square(2, rank), opponent)) {
            moves.add(new Move(from, new Square(2, rank)));
        }
    }

    private static boolean attackedBySlider(Position position, Square target, Color attacker,
                                            int[][] directions, PieceType expected) {
        for (int[] direction : directions) {
            int file = target.file() + direction[0];
            int rank = target.rank() + direction[1];
            while (Square.isValid(file, rank)) {
                Piece piece = position.pieceAt(file, rank);
                if (piece != null) {
                    if (piece.color() == attacker
                            && (piece.type() == expected || piece.type() == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                file += direction[0];
                rank += direction[1];
            }
        }
        return false;
    }

    private static Square findKing(Position position, Color color) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                if (is(position.pieceAt(file, rank), color, PieceType.KING)) {
                    return new Square(file, rank);
                }
            }
        }
        throw new IllegalStateException("Position has no " + color + " king");
    }

    private static boolean is(Piece piece, Color color, PieceType type) {
        return piece != null && piece.color() == color && piece.type() == type;
    }
}
