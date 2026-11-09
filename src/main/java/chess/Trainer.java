package chess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public final class Trainer {
    public static final int DEFAULT_DEPTH = 3;
    public static final long DEFAULT_INITIAL_MILLIS = 5_000;
    public static final long DEFAULT_INCREMENT_MILLIS = 100;
    public static final int DEFAULT_PAIRS = 6;
    public static final int DEFAULT_TABLE_MEGABYTES = 16;

    private final Config config;

    public Trainer(Config config) {
        this.config = config;
    }

    public static void main(String[] args) throws Exception {
        int generations = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int pairs = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PAIRS;
        Path output = args.length > 2 ? Path.of(args[2]) : Path.of("training");
        Summary summary = new Trainer(Config.fast(generations, pairs, output)).run();
        System.out.println(summary.display());
    }

    public Summary run() throws IOException, SQLException {
        Files.createDirectories(config.output());
        Files.createDirectories(config.output().resolve("models"));
        Files.createDirectories(config.output().resolve("games"));
        Path weightsPath = config.output().resolve("weights.properties");
        boolean newModel = !Files.exists(weightsPath);
        Eval champion = Eval.loadOrDefault(weightsPath);
        champion.save(weightsPath);
        if (newModel) {
            champion.save(config.output().resolve("models/champion-000000.properties"));
        }
        int generation = loadGeneration();
        ensureStatsHeader();
        int promotions = 0;
        int games = 0;
        Generation last = null;

        for (int count = 0; count < config.generations(); count++) {
            generation++;
            Eval before = champion;
            Eval.Mutation mutation = before.mutate(new Random(0x43484553534CL + generation));
            Eval candidate = mutation.weights();
            Path gameOutput = config.output().resolve("games/%06d".formatted(generation));
            Files.createDirectories(gameOutput);
            before.save(gameOutput.resolve("champion.properties"));
            candidate.save(gameOutput.resolve("candidate.properties"));

            Match.Config matchConfig = new Match.Config(config.pairs(), config.depth(),
                    config.initialMillis(), config.incrementMillis(), gameOutput);
            Match.Summary result = new Match(matchConfig, candidate, before,
                    config.tableMegabytes(), (generation - 1) * config.pairs(), false).run();
            boolean promoted = shouldPromote(result);
            if (promoted) {
                champion = candidate;
                champion.save(weightsPath);
                champion.save(config.output().resolve("models/champion-%06d.properties".formatted(generation)));
                promotions++;
            }
            games += result.games();
            last = new Generation(generation, mutation, result, promoted, before.id(), champion.id());
            appendStats(last, candidate);
            saveState(generation, champion.id());
            writeReport(champion);
            System.out.printf("Generation %d: candidate %d-%d-%d, %s (%s %d -> %d)%n",
                    generation, result.aWins(), result.bWins(), result.draws(),
                    promoted ? "promoted" : "rejected", mutation.field(),
                    mutation.oldValue(), mutation.newValue());
        }
        return new Summary(config.generations(), games, promotions, generation, champion, last, config.output());
    }

    public static boolean shouldPromote(Match.Summary result) {
        return result.aWins() - result.bWins() >= 2
                && (result.aWins() + result.draws() * 0.5) / result.games() >= 0.55;
    }

    private int loadGeneration() throws IOException {
        Path path = config.output().resolve("state.properties");
        if (!Files.exists(path)) return 0;
        Properties state = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            state.load(input);
        }
        return Integer.parseInt(state.getProperty("generation", "0"));
    }

    private void saveState(int generation, String champion) throws IOException {
        Properties state = new Properties();
        state.setProperty("generation", Integer.toString(generation));
        state.setProperty("champion", champion);
        try (OutputStream output = Files.newOutputStream(config.output().resolve("state.properties"))) {
            state.store(output, "Chess training state");
        }
    }

    private void ensureStatsHeader() throws IOException {
        Path path = config.output().resolve("stats.csv");
        if (Files.exists(path)) return;
        String header = "timestamp,generation,candidate,champion_before,mutation,old_value,new_value,"
                + "games,candidate_wins,champion_wins,draws,timeouts,candidate_points,score_rate,"
                + "promoted,champion_after," + String.join(",", Eval.NAMES) + "\n";
        Files.writeString(path, header, StandardOpenOption.CREATE_NEW);
    }

    private void appendStats(Generation generation, Eval candidate) throws IOException {
        Match.Summary result = generation.result();
        double points = result.aWins() + result.draws() * 0.5;
        String row = "%s,%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.1f,%.4f,%s,%s,%s%n".formatted(
                Instant.now(), generation.number(), candidate.id(), generation.championBefore(),
                generation.mutation().field(), generation.mutation().oldValue(),
                generation.mutation().newValue(), result.games(), result.aWins(), result.bWins(),
                result.draws(), result.timeouts(), points, points / result.games(),
                generation.promoted(), generation.championAfter(), candidate.csv());
        Files.writeString(config.output().resolve("stats.csv"), row,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void writeReport(Eval champion) throws IOException {
        List<String> rows = Files.readAllLines(config.output().resolve("stats.csv")).stream().skip(1).toList();
        int games = 0, promotions = 0;
        double totalRate = 0, recentRate = 0;
        Map<String, Integer> promotedFields = new HashMap<>();
        int recentStart = Math.max(0, rows.size() - 10);
        for (int index = 0; index < rows.size(); index++) {
            String[] columns = rows.get(index).split(",");
            games += Integer.parseInt(columns[7]);
            double rate = Double.parseDouble(columns[13]);
            totalRate += rate;
            if (index >= recentStart) recentRate += rate;
            if (Boolean.parseBoolean(columns[14])) {
                promotions++;
                promotedFields.merge(columns[4], 1, Integer::sum);
            }
        }
        String leadingField = promotedFields.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .orElse("none");
        int recentCount = rows.size() - recentStart;
        StringBuilder report = new StringBuilder()
                .append("Chess training trends\n\n")
                .append("Generations: ").append(rows.size()).append('\n')
                .append("Games: ").append(games).append('\n')
                .append("Promotions: ").append(promotions).append('\n')
                .append("Candidate score, all generations: ")
                .append("%.1f%%\n".formatted(rows.isEmpty() ? 0 : totalRate * 100 / rows.size()))
                .append("Candidate score, last ").append(recentCount).append(" generations: ")
                .append("%.1f%%\n".formatted(recentCount == 0 ? 0 : recentRate * 100 / recentCount))
                .append("Most promoted weight: ").append(leadingField).append('\n')
                .append("Champion: ").append(champion.id()).append("\n\nWeights\n");
        int[] splitWeights = java.util.Arrays.stream(champion.csv().split(","))
                .mapToInt(Integer::parseInt).toArray();
        for (int index = 0; index < Eval.NAMES.size(); index++) {
            report.append(Eval.NAMES.get(index)).append(": ").append(splitWeights[index]).append('\n');
        }
        Files.writeString(config.output().resolve("report.txt"), report);
    }

    public record Config(int generations, int pairs, int depth, long initialMillis,
                         long incrementMillis, int tableMegabytes, Path output) {
        public Config {
            if (generations < 1 || pairs < 1 || depth < 1 || initialMillis <= 0
                    || incrementMillis < 0 || tableMegabytes < 1) {
                throw new IllegalArgumentException("Training settings must be positive");
            }
        }

        public static Config fast(int generations, int pairs, Path output) {
            return new Config(generations, pairs, DEFAULT_DEPTH, DEFAULT_INITIAL_MILLIS,
                    DEFAULT_INCREMENT_MILLIS, DEFAULT_TABLE_MEGABYTES, output);
        }
    }

    public record Generation(int number, Eval.Mutation mutation, Match.Summary result,
                             boolean promoted, String championBefore, String championAfter) {
    }

    public record Summary(int generations, int games, int promotions, int lastGeneration,
                          Eval champion, Generation last, Path output) {
        public String display() {
            return "Completed %d generations and %d games. Promoted %d candidates. "
                    .formatted(generations, games, promotions)
                    + "Champion " + champion.id() + ". Data: " + output.toAbsolutePath();
        }
    }
}
