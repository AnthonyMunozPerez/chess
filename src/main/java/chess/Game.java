package chess;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class Game {
    private Position position;
    private final Map<String, Integer> repetitions = new HashMap<>();
    private final List<Move> history = new ArrayList<>();

    public Game() {
        this(Position.initial());
    }

    public Game(Position position) {
        this.position = position;
        recordPosition();
    }

    public Position position() {
        return position;
    }

    public List<Move> legalMoves() {
        return Moves.legal(position);
    }

    public List<Move> history() {
        return List.copyOf(history);
    }

    public void play(Move move) {
        List<Move> legal = legalMoves();
        if (!legal.contains(move)) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }
        position = position.applyUnchecked(move);
        history.add(move);
        recordPosition();
    }

    public Result result() {
        List<Move> legal = legalMoves();
        if (legal.isEmpty()) {
            if (Moves.inCheck(position, position.sideToMove())) {
                Color winner = position.sideToMove().opposite();
                return new Result(Result.Status.CHECKMATE, winner,
                        "Checkmate. " + displayColor(winner) + " wins.");
            }
            return new Result(Result.Status.STALEMATE, null, "Draw by stalemate.");
        }
        if (position.halfmoveClock() >= 100) {
            return new Result(Result.Status.FIFTY_MOVE, null, "Draw by the fifty-move rule.");
        }
        if (repetitions.getOrDefault(position.repetitionKey(), 0) >= 3) {
            return new Result(Result.Status.REPETITION, null, "Draw by threefold repetition.");
        }
        if (insufficientMaterial()) {
            return new Result(Result.Status.INSUFFICIENT_MATERIAL, null,
                    "Draw by insufficient material.");
        }
        return Result.ongoing();
    }

    private void recordPosition() {
        repetitions.merge(position.repetitionKey(), 1, Integer::sum);
    }

    private boolean insufficientMaterial() {
        Map<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        int bishopSquareColor = -1;
        boolean bishopsShareColor = true;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.pieceAt(file, rank);
                if (piece == null || piece.type() == PieceType.KING) {
                    continue;
                }
                counts.merge(piece.type(), 1, Integer::sum);
                if (piece.type() == PieceType.BISHOP) {
                    int color = (file + rank) % 2;
                    if (bishopSquareColor == -1) {
                        bishopSquareColor = color;
                    } else if (bishopSquareColor != color) {
                        bishopsShareColor = false;
                    }
                }
            }
        }
        if (counts.isEmpty()) {
            return true;
        }
        if (counts.containsKey(PieceType.PAWN) || counts.containsKey(PieceType.ROOK)
                || counts.containsKey(PieceType.QUEEN)) {
            return false;
        }
        int bishops = counts.getOrDefault(PieceType.BISHOP, 0);
        int knights = counts.getOrDefault(PieceType.KNIGHT, 0);
        if (bishops + knights == 1) {
            return true;
        }
        return knights == 0 && bishops > 0 && bishopsShareColor;
    }

    private static String displayColor(Color color) {
        return color == Color.WHITE ? "White" : "Black";
    }
}
