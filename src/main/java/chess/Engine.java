package chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Engine implements Player {
    private static final int MATE = 1_000_000;
    private static final int INFINITY = 1_100_000;
    private static final int MAX_PLY = 128;
    private final String name;
    private final int maxDepth;
    private final TranspositionTable table;
    private final Eval eval;
    private final boolean verbose;
    private final Move[][] killers = new Move[MAX_PLY][2];
    private final int[][][] history = new int[2][64][64];
    private long nodes, qnodes, ttHits, cutoffs, started, softDeadline, hardDeadline;
    private SearchLimits limits;

    public Engine(String name, int maxDepth) { this(name, maxDepth, 128, Eval.standard(), true); }

    public Engine(String name, int maxDepth, int tableMegabytes) {
        this(name, maxDepth, tableMegabytes, Eval.standard(), true);
    }

    public Engine(String name, int maxDepth, int tableMegabytes, Eval eval) {
        this(name, maxDepth, tableMegabytes, eval, true);
    }

    public Engine(String name, int maxDepth, int tableMegabytes, Eval eval, boolean verbose) {
        if (maxDepth < 1) throw new IllegalArgumentException("Engine depth must be at least 1");
        this.name = name;
        this.maxDepth = maxDepth;
        this.table = new TranspositionTable(tableMegabytes);
        this.eval = java.util.Objects.requireNonNull(eval);
        this.verbose = verbose;
    }

    @Override public String name() { return name; }

    @Override
    public Move chooseMove(Position position, List<Move> legalMoves) {
        return search(position, legalMoves, SearchLimits.fixedDepth(maxDepth)).move();
    }

    @Override
    public Move chooseMove(Position position, List<Move> legalMoves, SearchLimits limits) {
        return search(position, legalMoves, limits).move();
    }

    public SearchResult search(Position position, List<Move> suppliedMoves, SearchLimits requested) {
        if (suppliedMoves.isEmpty()) throw new IllegalArgumentException("No legal move is available");
        started = System.nanoTime();
        limits = requested;
        nodes = qnodes = ttHits = cutoffs = 0;
        softDeadline = deadline(requested.softTimeMillis());
        hardDeadline = deadline(requested.hardTimeMillis());
        table.nextSearch();
        Move best = suppliedMoves.getFirst();
        if (suppliedMoves.size() == 1) return result(best, 0, 0, false, List.of(best));

        SearchBoard board = new SearchBoard(position);
        int completedDepth = 0, score = 0, previousScore = 0, stable = 0;
        boolean stopped = false;
        for (int depth = 1; depth <= Math.min(maxDepth, requested.maxDepth()); depth++) {
            try {
                int alpha = depth >= 3 ? previousScore - 40 : -INFINITY;
                int beta = depth >= 3 ? previousScore + 40 : INFINITY;
                Root root = root(board, depth, alpha, beta, best);
                if (root.score() <= alpha || root.score() >= beta) {
                    root = root(board, depth, -INFINITY, INFINITY, root.move());
                }
                stable = root.move().equals(best) && Math.abs(root.score() - previousScore) < 25 ? stable + 1 : 0;
                best = root.move();
                score = root.score();
                previousScore = score;
                completedDepth = depth;
                long now = System.nanoTime();
                if (now >= softDeadline
                        || (depth >= 4 && stable >= 2 && now - started >= (softDeadline - started) / 2)) break;
            } catch (Stopped ignored) {
                stopped = true;
                break;
            }
        }
        List<Move> pv = principalVariation(new SearchBoard(position), completedDepth, best);
        SearchResult result = result(best, completedDepth, score, stopped, pv);
        if (verbose) {
            System.out.printf("%s plays %s (depth %d, score %s, %,d nodes, %,d nps)%n",
                    name, best, completedDepth, displayScore(score), nodes + qnodes,
                    result.stats().nodesPerSecond());
        }
        return result;
    }

    private Root root(SearchBoard board, int depth, int alpha, int beta, Move preferred) {
        int originalAlpha = alpha, bestScore = -INFINITY;
        Move best = preferred;
        List<Move> moves = ordered(board, board.legalMoves(), preferred, 0);
        int index = 0;
        for (Move move : moves) {
            checkStop();
            SearchBoard.Undo undo = board.make(move);
            int score;
            if (index++ == 0) score = -search(board, depth - 1, -beta, -alpha, 1, true);
            else {
                score = -search(board, depth - 1, -alpha - 1, -alpha, 1, true);
                if (score > alpha && score < beta) score = -search(board, depth - 1, -beta, -alpha, 1, true);
            }
            board.undo(undo);
            if (score > bestScore) { bestScore = score; best = move; }
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }
        byte flag = bestScore <= originalAlpha ? TranspositionTable.UPPER
                : bestScore >= beta ? TranspositionTable.LOWER : TranspositionTable.EXACT;
        table.store(board.hash(), depth, toTable(bestScore, 0), flag, best);
        return new Root(best, bestScore);
    }

    private int search(SearchBoard board, int depth, int alpha, int beta, int ply, boolean allowNull) {
        checkStop();
        nodes++;
        if (ply >= MAX_PLY - 1) return board.evaluate(eval);
        if (board.halfmove() >= 100) return 0;
        boolean inCheck = board.inCheck();
        if (depth <= 0) return quiescence(board, alpha, beta, ply);

        int originalAlpha = alpha;
        int hit = table.probe(board.hash());
        Move ttMove = null;
        if (hit >= 0) {
            ttHits++;
            ttMove = SearchBoard.decodeMove(table.move(hit));
            if (table.depth(hit) >= depth) {
                int value = fromTable(table.score(hit), ply);
                if (table.flag(hit) == TranspositionTable.EXACT) return value;
                if (table.flag(hit) == TranspositionTable.LOWER) alpha = Math.max(alpha, value);
                else beta = Math.min(beta, value);
                if (alpha >= beta) return value;
            }
        }

        if (allowNull && depth >= 3 && !inCheck && board.hasNonPawnMaterial()) {
            SearchBoard.NullUndo undo = board.makeNull();
            int reduction = 2 + depth / 6;
            int score = -search(board, depth - 1 - reduction, -beta, -beta + 1, ply + 1, false);
            board.undoNull(undo);
            if (score >= beta) return beta;
        }

        List<Move> legal = board.legalMoves();
        if (legal.isEmpty()) return inCheck ? -MATE + ply : 0;
        List<Move> moves = ordered(board, legal, ttMove, ply);
        Move bestMove = null;
        int best = -INFINITY, index = 0;
        for (Move move : moves) {
            boolean quiet = !board.isCapture(move) && move.promotion() == null;
            SearchBoard.Undo undo = board.make(move);
            int score;
            if (index == 0) score = -search(board, depth - 1, -beta, -alpha, ply + 1, true);
            else {
                int reduction = depth >= 3 && index >= 4 && quiet && !inCheck ? 1 : 0;
                score = -search(board, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);
                if (score > alpha && reduction > 0) {
                    score = -search(board, depth - 1, -alpha - 1, -alpha, ply + 1, true);
                }
                if (score > alpha && score < beta) {
                    score = -search(board, depth - 1, -beta, -alpha, ply + 1, true);
                }
            }
            board.undo(undo);
            index++;
            if (score > best) { best = score; bestMove = move; }
            if (score > alpha) alpha = score;
            if (alpha >= beta) {
                cutoffs++;
                if (quiet) {
                    if (!move.equals(killers[ply][0])) {
                        killers[ply][1] = killers[ply][0];
                        killers[ply][0] = move;
                    }
                    int color = board.side() > 0 ? 0 : 1;
                    history[color][move.from().index()][move.to().index()] += depth * depth;
                }
                break;
            }
        }
        byte flag = best <= originalAlpha ? TranspositionTable.UPPER
                : best >= beta ? TranspositionTable.LOWER : TranspositionTable.EXACT;
        table.store(board.hash(), depth, toTable(best, ply), flag, bestMove);
        return best;
    }

    private int quiescence(SearchBoard board, int alpha, int beta, int ply) {
        checkStop();
        qnodes++;
        boolean inCheck = board.inCheck();
        int standPat = board.evaluate(eval);
        if (!inCheck) {
            if (standPat >= beta) return beta;
            if (standPat > alpha) alpha = standPat;
        }
        List<Move> moves = ordered(board, board.tacticalMoves(), null, ply);
        if (moves.isEmpty()) return inCheck ? -MATE + ply : alpha;
        for (Move move : moves) {
            if (!inCheck && board.captureScore(move) < 1_000 && board.staticExchange(move) < 0) continue;
            SearchBoard.Undo undo = board.make(move);
            int score = -quiescence(board, -beta, -alpha, ply + 1);
            board.undo(undo);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private List<Move> ordered(SearchBoard board, List<Move> moves, Move preferred, int ply) {
        List<Move> result = new ArrayList<>(moves);
        result.sort(Comparator.comparingInt((Move move) -> orderScore(board, move, preferred, ply)).reversed());
        return result;
    }

    private int orderScore(SearchBoard board, Move move, Move preferred, int ply) {
        if (move.equals(preferred)) return 2_000_000;
        if (board.isCapture(move)) return 1_000_000 + board.captureScore(move);
        if (move.promotion() != null) return 900_000 + promotionValue(move.promotion());
        if (ply < MAX_PLY && move.equals(killers[ply][0])) return 800_000;
        if (ply < MAX_PLY && move.equals(killers[ply][1])) return 700_000;
        return history[board.side() > 0 ? 0 : 1][move.from().index()][move.to().index()];
    }

    private List<Move> principalVariation(SearchBoard board, int depth, Move root) {
        List<Move> pv = new ArrayList<>();
        Move move = root;
        for (int ply = 0; ply < depth && move != null; ply++) {
            if (!board.legalMoves().contains(move)) break;
            pv.add(move);
            board.make(move);
            int hit = table.probe(board.hash());
            move = hit < 0 ? null : SearchBoard.decodeMove(table.move(hit));
        }
        return List.copyOf(pv);
    }

    private SearchResult result(Move move, int depth, int score, boolean stopped, List<Move> pv) {
        long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        return new SearchResult(move,
                new SearchStats(depth, score, nodes, qnodes, ttHits, cutoffs, elapsed, stopped, pv));
    }

    private void checkStop() {
        long total = nodes + qnodes;
        if ((total & 1023) == 0 && (total >= limits.maxNodes()
                || System.nanoTime() >= hardDeadline || Thread.currentThread().isInterrupted())) {
            throw Stopped.INSTANCE;
        }
    }

    private long deadline(long millis) {
        if (millis >= Long.MAX_VALUE / 2_000_000) return Long.MAX_VALUE;
        long delta = millis * 1_000_000;
        return Long.MAX_VALUE - started < delta ? Long.MAX_VALUE : started + delta;
    }

    private static int promotionValue(PieceType type) {
        return switch (type) {
            case KNIGHT -> 320; case BISHOP -> 330; case ROOK -> 500; case QUEEN -> 900; default -> 0;
        };
    }

    private static int toTable(int score, int ply) {
        return score > MATE - 1000 ? score + ply : score < -MATE + 1000 ? score - ply : score;
    }

    private static int fromTable(int score, int ply) {
        return score > MATE - 1000 ? score - ply : score < -MATE + 1000 ? score + ply : score;
    }

    private static String displayScore(int score) {
        if (Math.abs(score) > MATE - 1000) {
            return (score > 0 ? "+M" : "-M") + Math.max(1, (MATE - Math.abs(score) + 1) / 2);
        }
        return String.format("%+.2f", score / 100.0);
    }

    private record Root(Move move, int score) {}

    private static final class Stopped extends RuntimeException {
        static final Stopped INSTANCE = new Stopped();
        private Stopped() { super(null, null, false, false); }
    }
}
