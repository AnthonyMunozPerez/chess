package chess;

final class TranspositionTable {
    static final byte EXACT = 0, LOWER = 1, UPPER = 2;
    private final long[] keys;
    private final int[] moves;
    private final int[] scores;
    private final short[] depths;
    private final byte[] flags;
    private final byte[] ages;
    private final boolean[] used;
    private final int mask;
    private byte age = 1;

    TranspositionTable(int megabytes) {
        long target = Math.max(1, megabytes) * 1024L * 1024L / 24L;
        int size = 1;
        while ((long) size * 2 <= target && size < 1 << 24) size *= 2;
        keys = new long[size];
        moves = new int[size];
        scores = new int[size];
        depths = new short[size];
        flags = new byte[size];
        ages = new byte[size];
        used = new boolean[size];
        mask = size - 1;
    }

    void nextSearch() { if (++age == 0) age = 1; }
    int probe(long key) { int i = index(key); return used[i] && keys[i] == key ? i : -1; }
    int move(int i) { return moves[i]; }
    int score(int i) { return scores[i]; }
    int depth(int i) { return depths[i]; }
    byte flag(int i) { return flags[i]; }

    void store(long key, int depth, int score, byte flag, Move move) {
        int i = index(key);
        if (!used[i] || keys[i] == key || ages[i] != age || depth >= depths[i]) {
            used[i] = true;
            keys[i] = key;
            depths[i] = (short) depth;
            scores[i] = score;
            flags[i] = flag;
            moves[i] = move == null ? 0 : SearchBoard.encodeMove(move);
            ages[i] = age;
        }
    }

    private int index(long key) { return (int) (key ^ key >>> 32) & mask; }
}
