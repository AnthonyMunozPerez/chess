# Chess

Chess is a Java 21 game and engine with a Swing board. You can drag pieces, click highlighted moves, play another person, play the engine, or watch two engines play.

## Requirements

- JDK 21
- Maven 3.9+

## Run

```bash
mvn test
mvn package
java -jar target/chess-1.0.0.jar
```

The JAR opens the graphical board. Every game uses a three-minute clock with a one-second increment. Reaching zero loses the game. **Max depth** is a safety limit because the clock normally stops the search first. **Book** enables or disables the opening book.

Click **Run Match** to start a paired two-game engine match. The second game swaps colors. The match writes `games.pgn` and `games.sqlite` to `Documents/Chess Matches`.

Click **Train 10** to run ten self-play generations. Training uses depth 3, a 5-second clock, and a 0.1-second increment. Each generation plays 12 games and saves its data in `Documents/Chess Training`. You can keep playing while training runs, but both tasks share the computer's processor.

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

The optional number is the engine's maximum search depth and defaults to 3 in the command-line game. The engine reports its score, searched nodes, and nodes per second after each move. The project does not claim an Elo rating yet. A rating requires many games against fixed opponents on stated hardware.

## Run timed matches

Package the application, then run a paired match with a 3+1 time control:

```bash
mvn package
java -cp target/chess-1.0.0.jar chess.Match 1 64 matches/test-1
```

The first argument sets the number of opening pairs. Each pair contains two games with swapped colors. The second argument sets the maximum depth. The last argument sets the output folder.

The SQLite database stores the position, clock, elapsed time, score, depth, node counts, transposition-table hits, cutoffs, and principal variation for every searched move. Book moves consume no clock time in match tests. The PGN file stores the same games in standard chess notation.

## Opening book

The built-in book contains 24 curated lines. Each line covers eight moves for both sides. It has four lines for each requested family:

- London System
- King's Indian Attack
- Italian Game
- Modern Defense
- Alekhine Defense
- Pirc Defense

## Train evaluation weights

Run training without the graphical interface with:

```bash
java -cp target/chess-1.0.0.jar chess.Trainer 10 6 "$HOME/Documents/Chess Training"
```

The arguments set the number of generations, the number of opening pairs per generation, and the data folder. One opening pair produces two games with swapped colors.

Each generation changes one evaluation weight and plays the candidate against the current champion. The trainer promotes the candidate only when it has at least a two-win margin and scores at least 55 percent. New graphical and command-line games load the latest promoted model from `Documents/Chess Training/weights.properties`.

The training folder contains these files:

- `weights.properties` contains the current champion.
- `stats.csv` has one row per generation for charts and analysis.
- `report.txt` summarizes game counts, promotion counts, recent scores, and current weights.
- `models/` keeps each promoted champion.
- `games/` contains PGN, SQLite telemetry, and both model files for every generation.

The trainer changes material, activity, mobility, pawn structure, passed-pawn, king-shelter, rook-file, bishop-pair, and tempo weights. It does not change the search algorithms or the opening book.

The random player remains useful as a wiring and rules stress test.

## Current rules

The game enforces checks, checkmate, stalemate, castling, en passant, promotion, the fifty-move rule, threefold repetition, and the common insufficient-material cases.

See [engine.md](engine.md) for the search and self-play design.
