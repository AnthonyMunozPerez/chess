package chess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Match {
    private final Config config;
    private final OpeningBook book = new OpeningBook();
    private final Eval evalA;
    private final Eval evalB;
    private final int tableMegabytes;
    private final int openingOffset;
    private final boolean verbose;

    public Match(Config config) {
        this(config, Eval.standard(), Eval.standard(), 128, 0, true);
    }

    public Match(Config config, Eval evalA, Eval evalB, int tableMegabytes,
                 int openingOffset, boolean verbose) {
        this.config = config;
        this.evalA = java.util.Objects.requireNonNull(evalA);
        this.evalB = java.util.Objects.requireNonNull(evalB);
        this.tableMegabytes = Math.max(1, tableMegabytes);
        this.openingOffset = Math.floorMod(openingOffset, book.lines().size());
        this.verbose = verbose;
    }

    public static void main(String[] args) throws Exception {
        int pairs = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int depth = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        Path output = args.length > 2 ? Path.of(args[2])
                : Path.of("matches", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Summary summary = new Match(new Config(pairs, depth, Clock.DEFAULT_INITIAL_MILLIS,
                Clock.DEFAULT_INCREMENT_MILLIS, output)).run();
        System.out.println(summary.display());
    }

    public Summary run() throws IOException, SQLException {
        Files.createDirectories(config.output());
        List<GameRecord> games = new ArrayList<>();
        int aWins = 0, bWins = 0, draws = 0, timeouts = 0;
        try (Store store = new Store(config.output().resolve("games.sqlite"))) {
            for (int pair = 0; pair < config.pairs(); pair++) {
                OpeningBook.Line line = book.lines().get((pair + openingOffset) % book.lines().size());
                for (boolean swap : new boolean[]{false, true}) {
                    int number = games.size() + 1;
                    GameRecord record = play(number, line, swap);
                    games.add(record);
                    store.save(record);
                    appendPgn(config.output().resolve("games.pgn"), record);
                    if (record.reason().equals("timeout")) timeouts++;
                    if (record.winner() == null) draws++;
                    else if (record.winner().equals("Engine A")) aWins++;
                    else bWins++;
                    if (verbose) System.out.printf("Game %d: %s (%s)%n", number, record.score(), record.reason());
                }
            }
        }
        return new Summary(games.size(), aWins, bWins, draws, timeouts, config.output());
    }

    private GameRecord play(int number, OpeningBook.Line line, boolean swap) {
        String white = swap ? "Engine B" : "Engine A";
        String black = swap ? "Engine A" : "Engine B";
        Eval whiteEval = swap ? evalB : evalA;
        Eval blackEval = swap ? evalA : evalB;
        Engine whiteEngine = new Engine(white, config.maxDepth(), tableMegabytes, whiteEval, verbose);
        Engine blackEngine = new Engine(black, config.maxDepth(), tableMegabytes, blackEval, verbose);
        Game game = new Game();
        Clock clock = new Clock(config.initialMillis(), config.incrementMillis());
        List<Ply> plies = new ArrayList<>();

        for (Move move : line.moves()) {
            addPly(game, clock, plies, move, 0, true, null);
            game.play(move);
        }

        String winner = null;
        String reason = "draw";
        while (!game.result().finished() && plies.size() < 400) {
            Color color = game.position().sideToMove();
            Engine engine = color == Color.WHITE ? whiteEngine : blackEngine;
            long remaining = clock.remaining(color);
            long started = System.nanoTime();
            SearchResult searched = engine.search(game.position(), game.legalMoves(),
                    SearchLimits.timed(remaining, config.incrementMillis()));
            long elapsed = Math.max(1, (System.nanoTime() - started + 999_999) / 1_000_000);
            if (!clock.completeMove(color, elapsed)) {
                winner = color == Color.WHITE ? black : white;
                reason = "timeout";
                break;
            }
            Move move = searched.move();
            addPly(game, clock, plies, move, elapsed, false, searched.stats());
            game.play(move);
        }
        if (game.result().finished()) {
            Result result = game.result();
            reason = result.status().name().toLowerCase();
            if (result.winner() != null) winner = result.winner() == Color.WHITE ? white : black;
        } else if (plies.size() >= 400) {
            reason = "move-limit";
        }
        String score = winner == null ? "1/2-1/2" : winner.equals(white) ? "1-0" : "0-1";
        return new GameRecord(number, white, black, line.label(), score, winner, reason, plies,
                config.initialMillis(), config.incrementMillis());
    }

    private static void addPly(Game game, Clock clock, List<Ply> plies, Move move, long elapsed,
                               boolean book, SearchStats stats) {
        Position before = game.position();
        Color color = before.sideToMove();
        plies.add(new Ply(plies.size() + 1, color, before.toFen(), move.toString(),
                Notation.san(before, move, game.legalMoves()), book, elapsed,
                clock.remaining(color), stats));
    }

    private static void appendPgn(Path path, GameRecord game) throws IOException {
        StringBuilder pgn = new StringBuilder()
                .append("[Event \"Timed engine match\"]\n")
                .append("[Round \"").append(game.number()).append("\"]\n")
                .append("[White \"").append(game.white()).append("\"]\n")
                .append("[Black \"").append(game.black()).append("\"]\n")
                .append("[Result \"").append(game.score()).append("\"]\n")
                .append("[TimeControl \"").append(game.initialMillis() / 1000).append('+')
                .append(game.incrementMillis() / 1000).append("\"]\n")
                .append("[Opening \"").append(game.opening()).append("\"]\n")
                .append("[Termination \"").append(game.reason()).append("\"]\n\n");
        for (Ply ply : game.plies()) {
            if (ply.color() == Color.WHITE) pgn.append((ply.number() + 1) / 2).append(". ");
            pgn.append(ply.san()).append(' ');
        }
        pgn.append(game.score()).append("\n\n");
        Files.writeString(path, pgn, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    public record Config(int pairs, int maxDepth, long initialMillis, long incrementMillis, Path output) {
        public Config {
            if (pairs < 1 || maxDepth < 1 || initialMillis <= 0 || incrementMillis < 0) {
                throw new IllegalArgumentException("Pairs, depth, and clock values must be valid");
            }
        }
    }

    public record Ply(int number, Color color, String fen, String uci, String san, boolean book,
                      long elapsedMillis, long remainingMillis, SearchStats stats) {
    }

    public record GameRecord(int number, String white, String black, String opening, String score,
                             String winner, String reason, List<Ply> plies,
                             long initialMillis, long incrementMillis) {
        public GameRecord {
            plies = List.copyOf(plies);
        }
    }

    public record Summary(int games, int aWins, int bWins, int draws, int timeouts, Path output) {
        public String display() {
            return "Completed %d games: Engine A %d wins, Engine B %d wins, %d draws, %d timeouts. Results: %s"
                    .formatted(games, aWins, bWins, draws, timeouts, output.toAbsolutePath());
        }
    }

    private static final class Store implements AutoCloseable {
        private final Connection connection;

        Store(Path path) throws SQLException {
            connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table if not exists games (
                          id integer primary key, white text, black text, opening text,
                          score text, winner text, reason text, initial_ms integer, increment_ms integer
                        )""");
                statement.executeUpdate("""
                        create table if not exists moves (
                          game_id integer, ply integer, color text, fen text, uci text, san text, book integer,
                          elapsed_ms integer, remaining_ms integer, depth integer, score integer,
                          nodes integer, qnodes integer, tt_hits integer, cutoffs integer, pv text,
                          primary key (game_id, ply)
                        )""");
            }
        }

        void save(GameRecord game) throws SQLException {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into games values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setInt(1, game.number()); statement.setString(2, game.white());
                statement.setString(3, game.black()); statement.setString(4, game.opening());
                statement.setString(5, game.score()); statement.setString(6, game.winner());
                statement.setString(7, game.reason()); statement.setLong(8, game.initialMillis());
                statement.setLong(9, game.incrementMillis()); statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into moves values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Ply ply : game.plies()) {
                    SearchStats stats = ply.stats();
                    statement.setInt(1, game.number()); statement.setInt(2, ply.number());
                    statement.setString(3, ply.color().name()); statement.setString(4, ply.fen());
                    statement.setString(5, ply.uci()); statement.setString(6, ply.san());
                    statement.setInt(7, ply.book() ? 1 : 0);
                    statement.setLong(8, ply.elapsedMillis()); statement.setLong(9, ply.remainingMillis());
                    statement.setInt(10, stats == null ? 0 : stats.depth());
                    statement.setInt(11, stats == null ? 0 : stats.score());
                    statement.setLong(12, stats == null ? 0 : stats.nodes());
                    statement.setLong(13, stats == null ? 0 : stats.quiescenceNodes());
                    statement.setLong(14, stats == null ? 0 : stats.transpositionHits());
                    statement.setLong(15, stats == null ? 0 : stats.cutoffs());
                    statement.setString(16, stats == null ? "" : stats.principalVariation().toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }

        @Override public void close() throws SQLException { connection.close(); }
    }
}
