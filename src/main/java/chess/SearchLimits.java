package chess;

public record SearchLimits(int maxDepth, long maxNodes, long softTimeMillis, long hardTimeMillis) {
    public SearchLimits {
        if (maxDepth < 1 || maxNodes < 1 || softTimeMillis < 1 || hardTimeMillis < softTimeMillis) {
            throw new IllegalArgumentException("Invalid search limits");
        }
    }

    public static SearchLimits fixedDepth(int depth) {
        return new SearchLimits(depth, Long.MAX_VALUE, Long.MAX_VALUE / 2, Long.MAX_VALUE / 2);
    }

    public static SearchLimits timed(long remainingMillis, long incrementMillis) {
        long reserve = Math.max(50, Math.min(500, remainingMillis / 50));
        long usable = Math.max(1, remainingMillis - reserve);
        long allocation = Math.max(10, remainingMillis / 30 + incrementMillis * 3 / 4);
        long soft = Math.min(usable, allocation);
        long hard = Math.min(usable, Math.max(soft, allocation * 3));
        return new SearchLimits(64, Long.MAX_VALUE, soft, hard);
    }
}
