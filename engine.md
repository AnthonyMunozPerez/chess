# Engine plan

The engine should evolve behind the existing `Player` interface. The rules layer remains the authority on legal moves, so a search bug cannot corrupt the game state.

## 1. Establish correctness and measurements

- Extend perft tests with standard test positions and depths.
- Add clocks and a `GameConfig` so every player receives a time budget.
- Record games as PGN and positions as FEN for reproducible failures.
- Add a benchmark command that reports nodes per second.

## 2. First calculating engine (baseline implemented)

- Negamax with alpha-beta pruning. **Implemented.**
- Iterative deepening with the prior best move searched first. **Implemented to a fixed maximum depth.**
- Material plus simple activity terms for evaluation. **Implemented as a starting point.**
- Quiescence search on captures and promotions to reduce the horizon effect. **Next.**
- Principal-variation move ordering, then captures, promotions, killer moves, and history scores. **Capture and promotion ordering implemented; the rest is next.**

Iterative deepening answers the question “which lines are worth calculating?” The previous iteration supplies the best candidate first; alpha-beta then rejects inferior branches much earlier. Tactical moves get searched before quiet moves, while quiescence avoids stopping at obviously unstable positions.

## 3. Make search efficient

- Replace or supplement the object board with bitboards only after profiling.
- Add incremental Zobrist hashes and a fixed-size transposition table.
- Add aspiration windows, null-move pruning, late-move reductions, and check extensions one at a time, each guarded by tactical and self-play tests.
- Use a single search thread first. Deterministic search is far easier to debug; parallel search comes after stable strength measurements.

## 4. Time controls and match runner

- Give each side a remaining clock plus increment.
- Allocate time using remaining time, increment, move number, and position volatility.
- Run engine-vs-engine matches from paired openings, swapping colors.
- Save wins, draws, losses, time losses, search depth, nodes, and principal variations.

## 5. Openings

Do not hard-code named opening scripts into search. Start with no book, then add a small Polyglot-format book built from high-quality games. Limit it by position, frequency, and performance, and select among credible moves with controlled randomness. In strength tests, use the same diverse opening suite for both engine versions and swap colors to reduce bias.

## 6. Strength and Elo

Elo is empirical, not a property we can assign from search depth alone. Measure it from many games against fixed reference engines under one stated time control. For development, use sequential testing such as SPRT to decide whether a change is stronger without wasting thousands of games.

User-selectable strength can combine:

- time and node limits;
- maximum depth;
- evaluation noise;
- choosing among near-best moves rather than deliberately hanging pieces;
- optional opening-book variety.

Publish ratings with the hardware, time control, opponent pool, opening suite, and confidence interval. A rating without those conditions is misleading.

## Recommended implementation order

1. Clocks, PGN, and benchmark/perft commands.
2. `SearchPlayer` with iterative-deepening alpha-beta.
3. Evaluation and quiescence.
4. Transposition table and move ordering.
5. Automated match runner and Elo estimation.
6. Opening book and strength levels.
7. UCI protocol support so the engine can play in external chess GUIs and against established engines.
