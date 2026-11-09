package chess;

import java.util.Arrays;

public final class Position {
    private final Piece[] board;
    private final Color sideToMove;
    private final Castling castling;
    private final Square enPassant;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private Position(Piece[] board, Color sideToMove, Castling castling, Square enPassant,
                     int halfmoveClock, int fullmoveNumber) {
        this.board = board.clone();
        this.sideToMove = sideToMove;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Position initial() {
        return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public static Position fromFen(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN is required");
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length != 6) {
            throw new IllegalArgumentException("FEN must have six fields");
        }

        Piece[] board = new Piece[64];
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN board must have eight ranks");
        }
        for (int fenRank = 0; fenRank < 8; fenRank++) {
            int file = 0;
            for (char symbol : ranks[fenRank].toCharArray()) {
                if (Character.isDigit(symbol)) {
                    file += symbol - '0';
                    continue;
                }
                if (file >= 8) {
                    throw new IllegalArgumentException("Too many squares in FEN rank");
                }
                Color color = Character.isUpperCase(symbol) ? Color.WHITE : Color.BLACK;
                PieceType type = switch (Character.toLowerCase(symbol)) {
                    case 'k' -> PieceType.KING;
                    case 'q' -> PieceType.QUEEN;
                    case 'r' -> PieceType.ROOK;
                    case 'b' -> PieceType.BISHOP;
                    case 'n' -> PieceType.KNIGHT;
                    case 'p' -> PieceType.PAWN;
                    default -> throw new IllegalArgumentException("Unknown FEN piece: " + symbol);
                };
                int rank = 7 - fenRank;
                board[rank * 8 + file] = new Piece(color, type);
                file++;
            }
            if (file != 8) {
                throw new IllegalArgumentException("FEN rank does not contain eight squares");
            }
        }

        Color side = switch (parts[1]) {
            case "w" -> Color.WHITE;
            case "b" -> Color.BLACK;
            default -> throw new IllegalArgumentException("FEN side must be w or b");
        };
        String rights = parts[2];
        Castling castling = new Castling(rights.contains("K"), rights.contains("Q"),
                rights.contains("k"), rights.contains("q"));
        Square enPassant = parts[3].equals("-") ? null : Square.parse(parts[3]);

        try {
            return new Position(board, side, castling, enPassant,
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid FEN move counters", exception);
        }
    }

    public Piece pieceAt(Square square) {
        return board[square.index()];
    }

    Piece pieceAt(int file, int rank) {
        return Square.isValid(file, rank) ? board[rank * 8 + file] : null;
    }

    public Color sideToMove() {
        return sideToMove;
    }

    public Castling castling() {
        return castling;
    }

    public Square enPassant() {
        return enPassant;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    Position applyUnchecked(Move move) {
        Piece moving = pieceAt(move.from());
        if (moving == null) {
            throw new IllegalArgumentException("No piece on " + move.from());
        }

        Piece[] next = board.clone();
        Piece captured = next[move.to().index()];
        next[move.from().index()] = null;

        boolean enPassantCapture = moving.type() == PieceType.PAWN
                && move.to().equals(enPassant) && captured == null
                && move.from().file() != move.to().file();
        if (enPassantCapture) {
            int capturedIndex = move.from().rank() * 8 + move.to().file();
            captured = next[capturedIndex];
            next[capturedIndex] = null;
        }

        Piece placed = move.promotion() == null ? moving : new Piece(moving.color(), move.promotion());
        next[move.to().index()] = placed;

        if (moving.type() == PieceType.KING && Math.abs(move.to().file() - move.from().file()) == 2) {
            boolean kingSide = move.to().file() == 6;
            int rank = moving.color().homeRank();
            int rookFrom = rank * 8 + (kingSide ? 7 : 0);
            int rookTo = rank * 8 + (kingSide ? 5 : 3);
            next[rookTo] = next[rookFrom];
            next[rookFrom] = null;
        }

        Castling nextCastling = castling;
        if (moving.type() == PieceType.KING) {
            nextCastling = nextCastling.withoutKing(moving.color());
        } else if (moving.type() == PieceType.ROOK && move.from().rank() == moving.color().homeRank()) {
            if (move.from().file() == 0) {
                nextCastling = nextCastling.withoutRook(moving.color(), false);
            } else if (move.from().file() == 7) {
                nextCastling = nextCastling.withoutRook(moving.color(), true);
            }
        }
        if (captured != null && captured.type() == PieceType.ROOK
                && move.to().rank() == captured.color().homeRank()) {
            if (move.to().file() == 0) {
                nextCastling = nextCastling.withoutRook(captured.color(), false);
            } else if (move.to().file() == 7) {
                nextCastling = nextCastling.withoutRook(captured.color(), true);
            }
        }

        Square nextEnPassant = null;
        if (moving.type() == PieceType.PAWN && Math.abs(move.to().rank() - move.from().rank()) == 2) {
            nextEnPassant = new Square(move.from().file(),
                    (move.from().rank() + move.to().rank()) / 2);
        }
        int nextHalfmove = moving.type() == PieceType.PAWN || captured != null ? 0 : halfmoveClock + 1;
        int nextFullmove = sideToMove == Color.BLACK ? fullmoveNumber + 1 : fullmoveNumber;
        return new Position(next, sideToMove.opposite(), nextCastling, nextEnPassant,
                nextHalfmove, nextFullmove);
    }

    public String repetitionKey() {
        String fen = toFen();
        String[] fields = fen.split(" ");
        return String.join(" ", Arrays.copyOf(fields, 4));
    }

    public String toFen() {
        StringBuilder value = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = pieceAt(file, rank);
                if (piece == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        value.append(empty);
                        empty = 0;
                    }
                    value.append(piece.symbol());
                }
            }
            if (empty > 0) {
                value.append(empty);
            }
            if (rank > 0) {
                value.append('/');
            }
        }
        return value + " " + (sideToMove == Color.WHITE ? "w" : "b") + " " + castling
                + " " + (enPassant == null ? "-" : enPassant) + " " + halfmoveClock + " " + fullmoveNumber;
    }

    public String display() {
        StringBuilder value = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            value.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                Piece piece = pieceAt(file, rank);
                value.append(piece == null ? '.' : piece.symbol()).append(' ');
            }
            value.append('\n');
        }
        value.append("\n   a b c d e f g h");
        return value.toString();
    }
}
