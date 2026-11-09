package chess;

public enum Color {
    WHITE(1, 1, 0, 7),
    BLACK(-1, 6, 7, 0);

    private final int pawnDirection;
    private final int pawnStartRank;
    private final int homeRank;
    private final int promotionRank;

    Color(int pawnDirection, int pawnStartRank, int homeRank, int promotionRank) {
        this.pawnDirection = pawnDirection;
        this.pawnStartRank = pawnStartRank;
        this.homeRank = homeRank;
        this.promotionRank = promotionRank;
    }

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public int pawnDirection() {
        return pawnDirection;
    }

    public int pawnStartRank() {
        return pawnStartRank;
    }

    public int homeRank() {
        return homeRank;
    }

    public int promotionRank() {
        return promotionRank;
    }
}
