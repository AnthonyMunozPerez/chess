package chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTest {
    @TempDir Path output;

    @Test
    void pairedMatchSwapsColorsAndWritesPgnAndSqlite() throws Exception {
        Match.Summary summary = new Match(new Match.Config(1, 1, 1, 0, output)).run();

        assertEquals(2, summary.games());
        assertEquals(2, summary.timeouts());
        assertTrue(Files.size(output.resolve("games.pgn")) > 0);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + output.resolve("games.sqlite"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("select count(*), count(distinct white) from games")) {
            assertTrue(result.next());
            assertEquals(2, result.getInt(1));
            assertEquals(2, result.getInt(2));
        }
    }
}
