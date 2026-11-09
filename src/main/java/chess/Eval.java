package chess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.random.RandomGenerator;

public record Eval(int pawn, int knight, int bishop, int rook, int queen,
                   int pawnAdvance, int knightCenter, int bishopCenter, int queenCenter,
                   int mobility, int bishopPair, int doubledPawn, int isolatedPawn,
                   int passedPawn, int kingShield, int rookOpenFile, int tempo) {
    public static final int VERSION = 1;
    public static final List<String> NAMES = List.of("pawn", "knight", "bishop", "rook", "queen",
            "pawnAdvance", "knightCenter", "bishopCenter", "queenCenter", "mobility",
            "bishopPair", "doubledPawn", "isolatedPawn", "passedPawn", "kingShield",
            "rookOpenFile", "tempo");

    public Eval {
        if (pawn <= 0 || knight <= 0 || bishop <= 0 || rook <= 0 || queen <= 0
                || pawnAdvance < 0 || knightCenter < 0 || bishopCenter < 0 || queenCenter < 0
                || mobility < 0 || bishopPair < 0 || doubledPawn < 0 || isolatedPawn < 0
                || passedPawn < 0 || kingShield < 0 || rookOpenFile < 0 || tempo < 0) {
            throw new IllegalArgumentException("Evaluation weights must be positive or zero");
        }
    }

    public static Eval standard() {
        return new Eval(100, 320, 330, 500, 900, 5, 4, 2, 1, 2,
                30, 12, 10, 14, 10, 18, 8);
    }

    public static Path userModel() {
        return Path.of(System.getProperty("user.home"), "Documents", "Chess Training", "weights.properties");
    }

    public static Eval load(Path path) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        int version = Integer.parseInt(values.getProperty("version", "0"));
        if (version != VERSION) throw new IOException("Unsupported evaluation version: " + version);
        int[] loaded = new int[NAMES.size()];
        for (int i = 0; i < loaded.length; i++) {
            String name = NAMES.get(i);
            loaded[i] = Integer.parseInt(values.getProperty(name));
        }
        return fromArray(loaded);
    }

    public static Eval loadOrDefault(Path path) {
        try {
            return Files.exists(path) ? load(path) : standard();
        } catch (IOException | RuntimeException exception) {
            System.err.println("Could not load evaluation weights from " + path + ": " + exception.getMessage());
            return standard();
        }
    }

    public void save(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties values = new Properties();
        values.setProperty("version", Integer.toString(VERSION));
        int[] array = toArray();
        for (int i = 0; i < array.length; i++) values.setProperty(NAMES.get(i), Integer.toString(array[i]));
        try (OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Chess evaluation weights");
        }
    }

    public Mutation mutate(RandomGenerator random) {
        int index = random.nextInt(NAMES.size());
        int[] values = toArray();
        int oldValue = values[index];
        int step = step(index);
        int change = random.nextBoolean() ? step : -step;
        values[index] = clamp(index, oldValue + change);
        if (values[index] == oldValue) values[index] = clamp(index, oldValue - change);
        return new Mutation(fromArray(values), NAMES.get(index), oldValue, values[index]);
    }

    public String csv() {
        StringBuilder value = new StringBuilder();
        for (int number : toArray()) {
            if (!value.isEmpty()) value.append(',');
            value.append(number);
        }
        return value.toString();
    }

    public String id() {
        return Integer.toUnsignedString(java.util.Arrays.hashCode(toArray()), 36);
    }

    int value(int piece) {
        return switch (piece) {
            case SearchBoard.PAWN -> pawn;
            case SearchBoard.KNIGHT -> knight;
            case SearchBoard.BISHOP -> bishop;
            case SearchBoard.ROOK -> rook;
            case SearchBoard.QUEEN -> queen;
            case SearchBoard.KING -> 20_000;
            default -> 0;
        };
    }

    private int[] toArray() {
        return new int[]{pawn, knight, bishop, rook, queen, pawnAdvance, knightCenter,
                bishopCenter, queenCenter, mobility, bishopPair, doubledPawn, isolatedPawn,
                passedPawn, kingShield, rookOpenFile, tempo};
    }

    private static Eval fromArray(int[] v) {
        return new Eval(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8],
                v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16]);
    }

    private static int step(int index) {
        return switch (index) {
            case 0 -> 2;
            case 1, 2 -> 4;
            case 3 -> 6;
            case 4 -> 10;
            default -> 2;
        };
    }

    private static int clamp(int index, int value) {
        return switch (index) {
            case 0 -> Math.clamp(value, 70, 140);
            case 1, 2 -> Math.clamp(value, 240, 420);
            case 3 -> Math.clamp(value, 380, 650);
            case 4 -> Math.clamp(value, 700, 1_100);
            default -> Math.clamp(value, 0, 200);
        };
    }

    public record Mutation(Eval weights, String field, int oldValue, int newValue) {
    }
}
