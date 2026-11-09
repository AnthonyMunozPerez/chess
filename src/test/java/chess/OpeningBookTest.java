package chess;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningBookTest {
    @Test
    void containsFourLegalSixteenPlyLinesForEveryFamily() {
        OpeningBook book = new OpeningBook();
        assertEquals(24, book.lines().size());
        assertEquals(Set.of("London", "King's Indian Attack", "Italian Game", "Modern Defense",
                        "Alekhine Defense", "Pirc Defense"),
                book.lines().stream().map(OpeningBook.Line::family).collect(Collectors.toSet()));
        assertTrue(book.lines().stream().allMatch(line -> line.moves().size() == 16));
        assertTrue(book.lines().stream().collect(Collectors.groupingBy(OpeningBook.Line::family,
                Collectors.counting())).values().stream().allMatch(count -> count == 4));
    }
}
