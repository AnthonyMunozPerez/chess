package chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerTest {
    @TempDir Path output;

    @Test
    void persistsStatsWeightsGamesAndContinuesTheGenerationNumber() throws Exception {
        Trainer.Config config = new Trainer.Config(1, 1, 1, 1, 0, 1, output);

        Trainer.Summary first = new Trainer(config).run();
        Trainer.Summary second = new Trainer(config).run();

        assertEquals(1, first.lastGeneration());
        assertEquals(2, second.lastGeneration());
        assertTrue(Files.exists(output.resolve("weights.properties")));
        assertTrue(Files.exists(output.resolve("games/000001/games.sqlite")));
        assertTrue(Files.exists(output.resolve("games/000002/games.pgn")));
        assertEquals(3, Files.readAllLines(output.resolve("stats.csv")).size());
        assertTrue(Files.readString(output.resolve("stats.csv")).contains("candidate_points"));
        assertTrue(Files.readString(output.resolve("report.txt")).contains("Generations: 2"));
    }

    @Test
    void promotionRequiresAtLeastATwoWinMarginAndFiftyFivePercent() {
        Path ignored = output.resolve("ignored");
        assertTrue(Trainer.shouldPromote(new Match.Summary(12, 3, 1, 8, 0, ignored)));
        assertTrue(!Trainer.shouldPromote(new Match.Summary(12, 2, 1, 9, 0, ignored)));
    }
}
