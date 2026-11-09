# Chess

A graphical chess game and calculating engine written in Java. Pieces support drag-and-drop and click-to-move interaction, while human and engine players share the same `Player` interface.

## Requirements

- JDK 21
- Maven 3.9+

## Run

```bash
mvn test
mvn package
java -jar target/chess-1.0.0.jar
```

The packaged JAR opens the graphical board. Pieces can be dragged or selected and moved by clicking a highlighted destination. Game mode and engine depth are controlled from the top of the window.

The command-line interface remains available with:

```bash
java -cp target/chess-1.0.0.jar chess.App human-human
```

In the command-line interface, enter moves in coordinate notation (`e2e4`, `g1f3`, or `e7e8q` for promotion). Type `moves` to list legal moves and `quit` to end the game.

Available modes:

```bash
java -cp target/chess-1.0.0.jar chess.App human-human
java -cp target/chess-1.0.0.jar chess.App human-engine 4
java -cp target/chess-1.0.0.jar chess.App engine-human 4
java -cp target/chess-1.0.0.jar chess.App engine-engine 4
java -cp target/chess-1.0.0.jar chess.App human-random
java -cp target/chess-1.0.0.jar chess.App random-human
java -cp target/chess-1.0.0.jar chess.App random-random
```

The optional number is the engine's maximum search depth and defaults to 3. The baseline engine uses iterative deepening, alpha-beta pruning, capture/promotion ordering, material values, and simple activity bonuses. It reports its score, searched nodes, and nodes per second after every move. It has no claimed Elo yet; strength must be measured under a defined time control and test setup.

The random player remains useful as a wiring and rules stress test.

## Current rules

The game enforces checks, checkmate, stalemate, castling, en passant, promotion, the fifty-move rule, threefold repetition, and the common insufficient-material cases.

See [engine.md](engine.md) for the staged engine plan.
