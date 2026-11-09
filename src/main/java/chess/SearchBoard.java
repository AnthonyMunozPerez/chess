package chess;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

final class SearchBoard {
    static final int PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
    static final int[] VALUE = {0, 100, 320, 330, 500, 900, 20_000};
    private static final int WK = 1, WQ = 2, BK = 4, BQ = 8;
    private static final int[][] KNIGHTS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KINGS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] STRAIGHTS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final long[][] PIECES = new long[12][64];
    private static final long[] CASTLING = new long[16];
    private static final long[] EN_PASSANT = new long[8];
    private static final long SIDE;

    static {
        SplittableRandom random = new SplittableRandom(0x43484553534C4F4EL);
        for (long[] piece : PIECES) for (int square = 0; square < 64; square++) piece[square] = random.nextLong();
        for (int i = 0; i < CASTLING.length; i++) CASTLING[i] = random.nextLong();
        for (int i = 0; i < EN_PASSANT.length; i++) EN_PASSANT[i] = random.nextLong();
        SIDE = random.nextLong();
    }

    private final int[] board = new int[64];
    private int side;
    private int castling;
    private int enPassant;
    private int halfmove;
    private int fullmove;
    private long hash;

    SearchBoard(Position position) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = position.pieceAt(file, rank);
                if (piece != null) board[rank * 8 + file] = encode(piece);
            }
        }
        side = position.sideToMove() == Color.WHITE ? 1 : -1;
        castling = (position.castling().whiteKing() ? WK : 0) | (position.castling().whiteQueen() ? WQ : 0)
                | (position.castling().blackKing() ? BK : 0) | (position.castling().blackQueen() ? BQ : 0);
        enPassant = position.enPassant() == null ? -1 : position.enPassant().index();
        halfmove = position.halfmoveClock();
        fullmove = position.fullmoveNumber();
        hash = computeHash();
    }

    int side() { return side; }
    int halfmove() { return halfmove; }
    long hash() { return hash; }
    int piece(int square) { return board[square]; }

    List<Move> legalMoves() {
        List<Move> pseudo = new ArrayList<>(48);
        generatePseudo(pseudo, false);
        List<Move> legal = new ArrayList<>(pseudo.size());
        int mover = side;
        for (Move move : pseudo) {
            Undo undo = make(move);
            if (!inCheck(mover)) legal.add(move);
            undo(undo);
        }
        return legal;
    }

    List<Move> tacticalMoves() {
        List<Move> legal = legalMoves();
        if (inCheck(side)) return legal;
        return legal.stream().filter(move -> isCapture(move) || move.promotion() != null).toList();
    }

    boolean inCheck() { return inCheck(side); }

    boolean inCheck(int color) {
        int king = 0;
        for (int square = 0; square < 64; square++) {
            if (board[square] == color * KING) { king = square; break; }
        }
        return attacked(king, -color);
    }

    boolean isCapture(Move move) {
        return board[move.to().index()] != 0
                || (Math.abs(board[move.from().index()]) == PAWN && move.to().index() == enPassant);
    }

    int captureScore(Move move) {
        int moving = Math.abs(board[move.from().index()]);
        int captured = Math.abs(board[move.to().index()]);
        if (captured == 0 && move.to().index() == enPassant) captured = PAWN;
        return VALUE[captured] * 10 - VALUE[moving] + (move.promotion() == null ? 0 : VALUE[type(move.promotion())]);
    }

    int staticExchange(Move move) {
        int captured = Math.abs(board[move.to().index()]);
        if (captured == 0 && move.to().index() == enPassant) captured = PAWN;
        int promotion = move.promotion() == null ? 0
                : VALUE[type(move.promotion())] - VALUE[PAWN];
        Undo undo = make(move);
        int reply = bestExchangeOn(move.to().index(), 0);
        undo(undo);
        return VALUE[captured] + promotion - reply;
    }

    private int bestExchangeOn(int square, int depth) {
        if (depth >= 16) return 0;
        int victim = Math.abs(board[square]);
        int best = 0;
        for (Move reply : legalMoves()) {
            if (reply.to().index() != square || !isCapture(reply)) continue;
            int promotion = reply.promotion() == null ? 0
                    : VALUE[type(reply.promotion())] - VALUE[PAWN];
            Undo undo = make(reply);
            int gain = VALUE[victim] + promotion - bestExchangeOn(square, depth + 1);
            undo(undo);
            best = Math.max(best, gain);
        }
        return best;
    }

    int evaluate() {
        int white = 0;
        for (int square = 0; square < 64; square++) {
            int code = board[square];
            if (code == 0) continue;
            int rank = square / 8, file = square % 8;
            int advancement = code > 0 ? rank : 7 - rank;
            int centrality = 14 - (Math.abs(2 * file - 7) + Math.abs(2 * rank - 7));
            int activity = switch (Math.abs(code)) {
                case PAWN -> advancement * 5;
                case KNIGHT -> centrality * 4;
                case BISHOP -> centrality * 2;
                case QUEEN -> centrality;
                default -> 0;
            };
            white += Integer.signum(code) * (VALUE[Math.abs(code)] + activity);
        }
        return side * white;
    }

    boolean hasNonPawnMaterial() {
        for (int code : board) if (code * side > 0 && Math.abs(code) > PAWN && Math.abs(code) < KING) return true;
        return false;
    }

    Undo make(Move move) {
        int from = move.from().index(), to = move.to().index();
        int moving = board[from], target = board[to];
        int capturedSquare = to, captured = target, rookFrom = -1, rookTo = -1, rookCode = 0;
        Undo undo = new Undo(move, moving, target, capturedSquare, captured, rookFrom, rookTo, rookCode,
                castling, enPassant, halfmove, fullmove, hash);

        board[from] = 0;
        if (Math.abs(moving) == PAWN && to == enPassant && target == 0) {
            capturedSquare = to - side * 8;
            captured = board[capturedSquare];
            board[capturedSquare] = 0;
        }
        int placed = move.promotion() == null ? moving : side * type(move.promotion());
        board[to] = placed;

        if (Math.abs(moving) == KING && Math.abs(to - from) == 2) {
            rookFrom = to > from ? from + 3 : from - 4;
            rookTo = to > from ? from + 1 : from - 1;
            rookCode = board[rookFrom];
            board[rookTo] = rookCode;
            board[rookFrom] = 0;
        }
        undo = new Undo(move, moving, target, capturedSquare, captured, rookFrom, rookTo, rookCode,
                undo.castling(), undo.enPassant(), undo.halfmove(), undo.fullmove(), undo.hash());

        long nextHash = hash ^ CASTLING[castling] ^ PIECES[pieceIndex(moving)][from];
        if (enPassant >= 0) nextHash ^= EN_PASSANT[enPassant % 8];
        if (captured != 0) nextHash ^= PIECES[pieceIndex(captured)][capturedSquare];
        nextHash ^= PIECES[pieceIndex(placed)][to];
        if (rookFrom >= 0) {
            nextHash ^= PIECES[pieceIndex(rookCode)][rookFrom];
            nextHash ^= PIECES[pieceIndex(rookCode)][rookTo];
        }

        if (Math.abs(moving) == KING) castling &= side > 0 ? ~(WK | WQ) : ~(BK | BQ);
        if (from == 0 || to == 0) castling &= ~WQ;
        if (from == 7 || to == 7) castling &= ~WK;
        if (from == 56 || to == 56) castling &= ~BQ;
        if (from == 63 || to == 63) castling &= ~BK;
        enPassant = Math.abs(moving) == PAWN && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
        halfmove = Math.abs(moving) == PAWN || captured != 0 ? 0 : halfmove + 1;
        if (side < 0) fullmove++;
        side = -side;
        nextHash ^= CASTLING[castling] ^ SIDE;
        if (enPassant >= 0) nextHash ^= EN_PASSANT[enPassant % 8];
        hash = nextHash;
        return undo;
    }

    void undo(Undo undo) {
        side = -side;
        int from = undo.move().from().index(), to = undo.move().to().index();
        board[from] = undo.moving();
        board[to] = undo.target();
        if (undo.capturedSquare() != to) board[undo.capturedSquare()] = undo.captured();
        if (undo.rookFrom() >= 0) {
            board[undo.rookFrom()] = undo.rookCode();
            board[undo.rookTo()] = 0;
        }
        castling = undo.castling(); enPassant = undo.enPassant(); halfmove = undo.halfmove();
        fullmove = undo.fullmove(); hash = undo.hash();
    }

    NullUndo makeNull() {
        NullUndo undo = new NullUndo(enPassant, halfmove, hash);
        if (enPassant >= 0) hash ^= EN_PASSANT[enPassant % 8];
        enPassant = -1; halfmove++; side = -side; hash ^= SIDE;
        return undo;
    }

    void undoNull(NullUndo undo) {
        side = -side; enPassant = undo.enPassant(); halfmove = undo.halfmove(); hash = undo.hash();
    }

    private void generatePseudo(List<Move> moves, boolean capturesOnly) {
        for (int from = 0; from < 64; from++) {
            int code = board[from];
            if (code * side <= 0) continue;
            int file = from % 8, rank = from / 8;
            switch (Math.abs(code)) {
                case PAWN -> pawns(moves, from, file, rank, capturesOnly);
                case KNIGHT -> steps(moves, from, file, rank, KNIGHTS, capturesOnly);
                case BISHOP -> slides(moves, from, file, rank, DIAGONALS, capturesOnly);
                case ROOK -> slides(moves, from, file, rank, STRAIGHTS, capturesOnly);
                case QUEEN -> { slides(moves, from, file, rank, DIAGONALS, capturesOnly); slides(moves, from, file, rank, STRAIGHTS, capturesOnly); }
                case KING -> { steps(moves, from, file, rank, KINGS, capturesOnly); if (!capturesOnly) castles(moves, from); }
            }
        }
    }

    private void pawns(List<Move> moves, int from, int file, int rank, boolean capturesOnly) {
        int nextRank = rank + side;
        if (!capturesOnly && valid(file, nextRank) && board[nextRank * 8 + file] == 0) {
            addPawn(moves, from, nextRank * 8 + file);
            int start = side > 0 ? 1 : 6, doubleRank = rank + side * 2;
            if (rank == start && board[doubleRank * 8 + file] == 0) moves.add(move(from, doubleRank * 8 + file));
        }
        for (int df : new int[]{-1, 1}) {
            int nf = file + df;
            if (!valid(nf, nextRank)) continue;
            int to = nextRank * 8 + nf;
            if ((board[to] * side < 0 && Math.abs(board[to]) != KING) || to == enPassant) addPawn(moves, from, to);
        }
    }

    private void addPawn(List<Move> moves, int from, int to) {
        int rank = to / 8;
        if (rank == 0 || rank == 7) {
            moves.add(move(from, to, PieceType.QUEEN)); moves.add(move(from, to, PieceType.ROOK));
            moves.add(move(from, to, PieceType.BISHOP)); moves.add(move(from, to, PieceType.KNIGHT));
        } else moves.add(move(from, to));
    }

    private void steps(List<Move> moves, int from, int file, int rank, int[][] offsets, boolean capturesOnly) {
        for (int[] offset : offsets) {
            int nf = file + offset[0], nr = rank + offset[1];
            if (!valid(nf, nr)) continue;
            int target = board[nr * 8 + nf];
            if (target == 0) { if (!capturesOnly) moves.add(move(from, nr * 8 + nf)); }
            else if (target * side < 0 && Math.abs(target) != KING) moves.add(move(from, nr * 8 + nf));
        }
    }

    private void slides(List<Move> moves, int from, int file, int rank, int[][] directions, boolean capturesOnly) {
        for (int[] direction : directions) {
            int nf = file + direction[0], nr = rank + direction[1];
            while (valid(nf, nr)) {
                int to = nr * 8 + nf, target = board[to];
                if (target == 0) { if (!capturesOnly) moves.add(move(from, to)); }
                else { if (target * side < 0 && Math.abs(target) != KING) moves.add(move(from, to)); break; }
                nf += direction[0]; nr += direction[1];
            }
        }
    }

    private void castles(List<Move> moves, int from) {
        int rank = side > 0 ? 0 : 7, opponent = -side;
        if (from != rank * 8 + 4 || inCheck(side)) return;
        int kingRight = side > 0 ? WK : BK, queenRight = side > 0 ? WQ : BQ;
        if ((castling & kingRight) != 0 && board[rank * 8 + 5] == 0 && board[rank * 8 + 6] == 0
                && board[rank * 8 + 7] == side * ROOK && !attacked(rank * 8 + 5, opponent) && !attacked(rank * 8 + 6, opponent))
            moves.add(move(from, rank * 8 + 6));
        if ((castling & queenRight) != 0 && board[rank * 8 + 1] == 0 && board[rank * 8 + 2] == 0 && board[rank * 8 + 3] == 0
                && board[rank * 8] == side * ROOK && !attacked(rank * 8 + 3, opponent) && !attacked(rank * 8 + 2, opponent))
            moves.add(move(from, rank * 8 + 2));
    }

    private boolean attacked(int square, int attacker) {
        int file = square % 8, rank = square / 8;
        int pawnRank = rank - attacker;
        for (int df : new int[]{-1, 1}) if (valid(file + df, pawnRank) && board[pawnRank * 8 + file + df] == attacker * PAWN) return true;
        for (int[] o : KNIGHTS) if (valid(file + o[0], rank + o[1]) && board[(rank + o[1]) * 8 + file + o[0]] == attacker * KNIGHT) return true;
        for (int[] o : KINGS) if (valid(file + o[0], rank + o[1]) && board[(rank + o[1]) * 8 + file + o[0]] == attacker * KING) return true;
        return sliderAttack(square, attacker, DIAGONALS, BISHOP) || sliderAttack(square, attacker, STRAIGHTS, ROOK);
    }

    private boolean sliderAttack(int square, int attacker, int[][] dirs, int type) {
        int file = square % 8, rank = square / 8;
        for (int[] d : dirs) {
            int nf = file + d[0], nr = rank + d[1];
            while (valid(nf, nr)) {
                int code = board[nr * 8 + nf];
                if (code != 0) { if (code == attacker * type || code == attacker * QUEEN) return true; break; }
                nf += d[0]; nr += d[1];
            }
        }
        return false;
    }

    private long computeHash() {
        long value = CASTLING[castling];
        if (side < 0) value ^= SIDE;
        if (enPassant >= 0) value ^= EN_PASSANT[enPassant % 8];
        for (int square = 0; square < 64; square++) if (board[square] != 0) value ^= PIECES[pieceIndex(board[square])][square];
        return value;
    }

    static int encodeMove(Move move) {
        int promotion = move.promotion() == null ? 0 : type(move.promotion());
        return move.from().index() | move.to().index() << 6 | promotion << 12;
    }

    static Move decodeMove(int code) {
        if (code == 0) return null;
        int from = code & 63, to = code >> 6 & 63, promotion = code >> 12 & 7;
        return promotion == 0 ? move(from, to) : move(from, to, pieceType(promotion));
    }

    private static int encode(Piece piece) { return (piece.color() == Color.WHITE ? 1 : -1) * type(piece.type()); }
    private static int type(PieceType type) { return switch (type) { case PAWN -> PAWN; case KNIGHT -> KNIGHT; case BISHOP -> BISHOP; case ROOK -> ROOK; case QUEEN -> QUEEN; case KING -> KING; }; }
    private static PieceType pieceType(int type) { return switch (type) { case KNIGHT -> PieceType.KNIGHT; case BISHOP -> PieceType.BISHOP; case ROOK -> PieceType.ROOK; case QUEEN -> PieceType.QUEEN; default -> throw new IllegalArgumentException(); }; }
    private static int pieceIndex(int code) { return (code > 0 ? 0 : 6) + Math.abs(code) - 1; }
    private static boolean valid(int file, int rank) { return file >= 0 && file < 8 && rank >= 0 && rank < 8; }
    private static Move move(int from, int to) { return new Move(new Square(from % 8, from / 8), new Square(to % 8, to / 8)); }
    private static Move move(int from, int to, PieceType promotion) { return new Move(new Square(from % 8, from / 8), new Square(to % 8, to / 8), promotion); }

    record Undo(Move move, int moving, int target, int capturedSquare, int captured,
                int rookFrom, int rookTo, int rookCode, int castling, int enPassant,
                int halfmove, int fullmove, long hash) {}
    record NullUndo(int enPassant, int halfmove, long hash) {}
}
