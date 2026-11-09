package chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchLimitsTest {
    @Test
    void timedSearchKeepsAClockReserve() {
        SearchLimits limits = SearchLimits.timed(180_000, 1_000);

        assertEquals(64, limits.maxDepth());
        assertTrue(limits.softTimeMillis() < limits.hardTimeMillis());
        assertTrue(limits.hardTimeMillis() < 180_000);
    }

    @Test
    void emergencySearchStillGetsTime() {
        SearchLimits limits = SearchLimits.timed(20, 0);

        assertTrue(limits.softTimeMillis() >= 1);
        assertTrue(limits.hardTimeMillis() <= 20);
    }
}
