package chess;

import java.util.List;

public record SearchStats(int depth, int score, long nodes, long quiescenceNodes,
                          long transpositionHits, long cutoffs, long elapsedMillis,
                          boolean stopped, List<Move> principalVariation) {
    public long nodesPerSecond() {
        return (nodes + quiescenceNodes) * 1_000 / Math.max(1, elapsedMillis);
    }
}
