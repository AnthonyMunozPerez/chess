package chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Engine implements Player {
    private static final int MATE = 1_000_000;
    private static final int[] VALUE = new int[PieceType.values().length];

    static {
        VALUE[PieceType.PAWN.ordinal()] = 100;
        VALUE[PieceType.KNIGHT.ordinal()] = 320;
        VALUE[PieceType.BISHOP.ordinal()] = 330;
        VALUE[PieceType.ROOK.ordinal()] = 500;
        VALUE[PieceType.QUEEN.ordinal()] = 900;
        VALUE[PieceType.KING.ordinal()] = 20_000;
    }

    private final String name;
    private final int maxDepth;
    private long nodes;

    public Engine(String name, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Engine depth must be at least 1");
        }
        this.name = name;
        this.maxDepth = maxDepth;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Move chooseMove(Position position, List<Move> legalMoves) {
        Move best = legalMoves.getFirst();
        int score = Integer.MIN_VALUE;
        nodes = 0;
        long started = System.nanoTime();

        for (int depth = 1; depth <= maxDepth; depth++) {
            Root result = searchRoot(position, legalMoves, depth, best);
            best = result.move();
            score = result.score();
        }

        long elapsedMillis = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        long nodesPerSecond = nodes * 1_000 / elapsedMillis;
        System.out.printf("%s plays %s (depth %d, score %s, %,d nodes, %,d nps)%n",
                name, best, maxDepth, displayScore(score), nodes, nodesPerSecond);
        return best;
    }

    private Root searchRoot(Position position, List<Move> legalMoves, int depth, Move previousBest) {
        Move best = previousBest;
        int bestScore = Integer.MIN_VALUE;
        int alpha = -MATE;
        int beta = MATE;
        for (Move move : ordered(position, legalMoves, previousBest)) {
            int score = -negamax(position.applyUnchecked(move), depth - 1, -beta, -alpha, 1);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
            alpha = Math.max(alpha, score);
        }
        return new Root(best, bestScore);
    }

    private int negamax(Position position, int depth, int alpha, int beta, int ply) {
        nodes++;
        List<Move> legal = Moves.legal(position);
        if (legal.isEmpty()) {
            return Moves.inCheck(position, position.sideToMove()) ? -MATE + ply : 0;
        }
        if (position.halfmoveClock() >= 100) {
            return 0;
        }
        if (depth == 0) {
            return evaluate(position);
        }

        int best = -MATE;
        for (Move move : ordered(position, legal, null)) {
            int score = -negamax(position.applyUnchecked(move), depth - 1, -beta, -alpha, ply + 1);
            best = Math.max(best, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    private static int evaluate(Position position) {
        int white = 0;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.pieceAt(file, rank);
                if (piece == null) {
                    continue;
                }
                int score = VALUE[piece.type().ordinal()] + activity(piece, file, rank);
                white += piece.color() == Color.WHITE ? score : -score;
            }
        }
        return position.sideToMove() == Color.WHITE ? white : -white;
    }

    private static int activity(Piece piece, int file, int rank) {
        int advancement = piece.color() == Color.WHITE ? rank : 7 - rank;
        int centrality = 14 - (Math.abs(2 * file - 7) + Math.abs(2 * rank - 7));
        return switch (piece.type()) {
            case PAWN -> advancement * 5;
            case KNIGHT -> centrality * 4;
            case BISHOP -> centrality * 2;
            case QUEEN -> centrality;
            default -> 0;
        };
    }

    private static List<Move> ordered(Position position, List<Move> moves, Move preferred) {
        List<Move> ordered = new ArrayList<>(moves);
        ordered.sort(Comparator.comparingInt((Move move) -> moveScore(position, move, preferred)).reversed()
                .thenComparing(Move::toString));
        return ordered;
    }

    private static int moveScore(Position position, Move move, Move preferred) {
        if (move.equals(preferred)) {
            return 1_000_000;
        }
        Piece moving = position.pieceAt(move.from());
        Piece captured = position.pieceAt(move.to());
        int score = move.promotion() == null ? 0 : VALUE[move.promotion().ordinal()] + 10_000;
        if (captured != null) {
            score += 20_000 + 10 * VALUE[captured.type().ordinal()] - VALUE[moving.type().ordinal()];
        } else if (moving.type() == PieceType.PAWN && move.to().equals(position.enPassant())) {
            score += 20_000 + 10 * VALUE[PieceType.PAWN.ordinal()] - VALUE[PieceType.PAWN.ordinal()];
        }
        return score;
    }

    private static String displayScore(int score) {
        if (Math.abs(score) > MATE - 1_000) {
            int plies = MATE - Math.abs(score);
            return (score > 0 ? "+M" : "-M") + Math.max(1, (plies + 1) / 2);
        }
        return String.format("%+.2f", score / 100.0);
    }

    private record Root(Move move, int score) {
    }
}
