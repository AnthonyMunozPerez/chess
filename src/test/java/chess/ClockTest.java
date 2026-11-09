package chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClockTest {
    @Test
    void addsIncrementOnlyAfterACompletedMove() {
        Clock clock = new Clock(10_000, 1_000);
        assertTrue(clock.completeMove(Color.WHITE, 2_500));
        assertEquals(8_500, clock.remaining(Color.WHITE));
    }

    @Test
    void reachingZeroIsAStrictLossWithoutIncrement() {
        Clock clock = new Clock(10_000, 1_000);
        assertFalse(clock.completeMove(Color.BLACK, 10_000));
        assertEquals(0, clock.remaining(Color.BLACK));
    }
}
