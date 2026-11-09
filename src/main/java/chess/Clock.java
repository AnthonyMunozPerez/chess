package chess;

public final class Clock {
    public static final long DEFAULT_INITIAL_MILLIS = 180_000;
    public static final long DEFAULT_INCREMENT_MILLIS = 1_000;

    private final long incrementMillis;
    private long whiteMillis;
    private long blackMillis;

    public Clock() {
        this(DEFAULT_INITIAL_MILLIS, DEFAULT_INCREMENT_MILLIS);
    }

    public Clock(long initialMillis, long incrementMillis) {
        if (initialMillis <= 0 || incrementMillis < 0) {
            throw new IllegalArgumentException("Clock values must be positive");
        }
        this.incrementMillis = incrementMillis;
        whiteMillis = initialMillis;
        blackMillis = initialMillis;
    }

    public long remaining(Color color) {
        return color == Color.WHITE ? whiteMillis : blackMillis;
    }

    public long incrementMillis() {
        return incrementMillis;
    }

    public boolean completeMove(Color color, long elapsedMillis) {
        long remaining = remaining(color);
        if (elapsedMillis >= remaining) {
            set(color, 0);
            return false;
        }
        set(color, remaining - Math.max(0, elapsedMillis) + incrementMillis);
        return true;
    }

    public void setRemaining(Color color, long millis) {
        set(color, Math.max(0, millis));
    }

    private void set(Color color, long millis) {
        if (color == Color.WHITE) whiteMillis = millis;
        else blackMillis = millis;
    }

    public static String display(long millis) {
        long safe = Math.max(0, millis);
        long minutes = safe / 60_000;
        long seconds = safe % 60_000 / 1_000;
        long tenths = safe % 1_000 / 100;
        return safe < 20_000 ? "%d:%02d.%d".formatted(minutes, seconds, tenths)
                : "%d:%02d".formatted(minutes, seconds);
    }
}
