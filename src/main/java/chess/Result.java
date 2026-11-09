package chess;

public record Result(Status status, Color winner, String description) {
    public enum Status {
        ONGOING, CHECKMATE, STALEMATE, FIFTY_MOVE, REPETITION, INSUFFICIENT_MATERIAL
    }

    public static Result ongoing() {
        return new Result(Status.ONGOING, null, "Game in progress");
    }

    public boolean finished() {
        return status != Status.ONGOING;
    }
}
