# Engine design

The rules layer decides which moves are legal. Search uses a separate mutable board, but the engine returns its chosen move to `Game` for validation before play.

## Search

The engine runs iterative deepening until it reaches the soft deadline. It also has a hard deadline that interrupts the current iteration. If an interruption occurs, the engine returns the best move from the last complete iteration. A position with one legal move skips search.

Each iteration uses negamax, alpha-beta pruning, and principal-variation search. Aspiration windows start near the previous score and widen to a full window after a failed search. The engine searches likely moves first in this order:

1. The transposition-table move.
2. Captures and promotions.
3. Principal-variation moves.
4. Killer moves.
5. Moves with high history scores.

Late-move reductions search quiet moves at a lower depth after stronger candidates fail. Null-move pruning tests whether passing would still exceed beta. Both methods reduce work in positions where the result appears stable.

At depth zero, quiescence search continues through checks, captures, and promotions. Static exchange evaluation rejects captures that lose material after the likely recapture sequence. This prevents the main search from stopping in the middle of an exchange.

## Search state and cache

`SearchBoard` changes one board in place and restores it after each branch. Incremental Zobrist hashing updates the position key when a piece, castling right, en passant square, or side to move changes.

Each engine owns a 128 MB transposition table. Entries contain the key, depth, score bound, best move, and age. The table avoids repeated searches after transpositions and supplies the first move for ordering. One thread runs each engine, which keeps tests deterministic.

## Time management

Every normal game uses three minutes plus one second per completed move. `SearchLimits` derives a soft deadline and a hard deadline from the remaining time and increment. The game controller measures wall-clock time independently. Reaching zero loses, even if the search returns a move at the same moment.

The opening suite does not consume clock time during formal matches. This isolates search speed from book selection and gives both engines the same starting position.

## Opening suite

`OpeningBook` contains four 16-ply lines for each of the London System, King's Indian Attack, Italian Game, Modern Defense, Alekhine Defense, and Pirc Defense. Normal games select among legal book moves. Formal matches apply one complete line, then repeat the position with engine colors swapped.

## Measurements

`Match` writes standard PGN and an SQLite database. The database records the FEN position, elapsed time, remaining clock, depth, evaluation, regular and quiescence nodes, transposition-table hits, cutoffs, and principal variation for every move. These records support comparisons between engine versions without storing full search trees.

Elo is a measured result, not a search setting. A useful rating requires a fixed opponent pool, enough paired games, stated hardware, the same opening suite, and a confidence interval.

## Learning boundary

The current engine does not learn or change its evaluation after a game. The opening book and evaluation are static. This keeps the baseline reproducible.

The PGN and SQLite logs are the input for later learning work. A future training process can score positions or tune evaluation weights offline, then write a versioned model. The playing engine does not need an unlimited memory of past games.
