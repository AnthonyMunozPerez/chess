package chess;

public record Square(int file, int rank) {
    public Square {
        if (!isValid(file, rank)) {
            throw new IllegalArgumentException("Square is outside the board: " + file + "," + rank);
        }
    }

    public int index() {
        return rank * 8 + file;
    }

    public static boolean isValid(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    public static Square parse(String value) {
        if (value == null || value.length() != 2) {
            throw new IllegalArgumentException("Square must look like e4");
        }
        int file = Character.toLowerCase(value.charAt(0)) - 'a';
        int rank = value.charAt(1) - '1';
        return new Square(file, rank);
    }

    @Override
    public String toString() {
        return "" + (char) ('a' + file) + (char) ('1' + rank);
    }
}
