# Chess960 — Project Plan

Working document for the effort to add Fischer Random / Chess960 support to
myChess. Captures the current understanding, the decisions taken so far, and
the phased work plan.

## Motivation

Cute Chess (and most other UCI GUIs) filter the engine list per game variant.
The selection criterion is the engine's UCI handshake: an engine is offered
for Chess960 only if it declares

```
option name UCI_Chess960 type check default false
```

during its response to `uci`. As long as myChess does not declare this option,
it disappears from the engine picker the moment the user selects "Fischer
Random".

A first handshake experiment with the option declared and a one-game session
in Cute Chess revealed that capability advertisement alone is not enough.
The GUI immediately sends a Shredder-style start FEN, e.g.

```
position fen rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1
```

and `Fen.parseCastlingState` rejects it with `Invalid castling-rights char 'F'`.
Full 960 support therefore touches FEN parsing, move generation, move
encoding, and FEN export. The capability advertisement is the trigger, but
the implementation has to follow through.

## Current state — where standard chess is hardcoded

A code survey identified four sites that today assume the standard-chess
castling layout (king on `e1`/`e8`, rooks on `a1`/`h1`/`a8`/`h8`):

1. **`GameStatus.castlingState`** — six-bit mask
   (`BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE`,
   `BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE`, …,
   `BIT_WHITE_HAS_CASTLED`, `BIT_BLACK_HAS_CASTLED`). It records *whether*
   a right is alive but carries no file information.
2. **`Fen.parseCastlingState`** — switch on the literals `K Q k q -`. Anything
   else throws.
3. **`MoveGenerator.calculateCastlingMoves` + the four `canDoXxxCastlingYyySide`
   helpers** — king source `e1`/`e8` literal, rook source implicit, path
   squares (`f1`/`g1`/`b1`/`c1`/`d1` and black mirrors) hardcoded both for
   "empty between" and for "attacked field" checks.
4. **`Board.makeMove` / `revertMove`** — castling move types execute the rook
   transfer `h1↔f1`, `a1↔d1` and the black mirrors via fixed square names.

`Fen.exportFEN` writes `KQkq` from the existing bitmask, which works only as
long as rooks are always on `a-` and `h-` files.

## Design decisions taken so far

### Where the rook starting files live

The starting files of the two castling rooks per side are a *game constant*:
they are determined by the starting position and do not change during play.
A side can only lose castling rights, never gain new ones, so once a right is
gone the file is irrelevant.

Conclusion: store the rook files on `Board`, not inside the per-snapshot
`GameStatus` stack.

Consequences:
- `GameStatus` stack size and undo material per move stay unchanged.
- Zobrist hashing is not affected — rook starting files are not part of the
  position hash, which matches the 960 community convention.
- Standard chess flows that never touch the new fields keep working with
  defaults.

### Array layout `{ WQ, WK, BQ, BK }`

`Board.castlingRookFiles` is a 4-element byte array indexed in the order

| Index | Slot                | Standard default |
|-------|---------------------|------------------|
| 0     | white queenside     | `0` (a-file)     |
| 1     | white kingside      | `7` (h-file)     |
| 2     | black queenside     | `0` (a-file)     |
| 3     | black kingside      | `7` (h-file)     |

Reading order follows the board layout left-to-right from White's
perspective. Standard-chess initializer is `{ 0, 7, 0, 7 }`. A sentinel value
of `-1` marks a slot where no castling right ever existed for the current
game (e.g. an asymmetric 960 setup with only one rook on a side — does not
occur in standard 960, but the encoding tolerates it).

### Named slots via enum

To keep `MoveGenerator`, `Fen`, and `UciMoveParser` free of raw `0`/`1`/`2`/`3`
indices, introduce a `CastlingSlot` enum with values
`WHITE_QUEENSIDE`, `WHITE_KINGSIDE`, `BLACK_QUEENSIDE`, `BLACK_KINGSIDE` and
helpers for color, side, and the matching `GameStatus.BIT_*` mask.

### `GameStatus.castlingState` remains as is

The four "right is alive" bits keep their meaning. The question "*which* rook
backs this right?" is now answered by `Board.castlingRookFiles[slot]`. No new
state per snapshot.

## Phase plan

| Phase | Scope                                                        | What it unlocks                                              |
|-------|--------------------------------------------------------------|--------------------------------------------------------------|
| 1     | FEN import for Shredder-FEN castling rights                  | Engine loads any 960 position; can play it without castling. |
| 2     | Generalize castling move generation, `Board.makeMove` undo   | Engine plays full 960 — including castling.                  |
| 3     | UCI castle move notation (`e1h1` form, in / out)             | Round-trip with cutechess and other 960-aware GUIs works.    |
| 4     | FEN export in Shredder style when files deviate from default | FEN round-trip closes for 960.                               |
| 5     | Test consolidation and edge cases                            | 960 mirror-eval, opening-DB hooks, full round-trip suite.    |

### Phase 1 — FEN import (next step)

1. Introduce `Board.castlingRookFiles` and `CastlingSlot` enum.
2. `Board.createNewGame()` initializes to `{ 0, 7, 0, 7 }`.
3. `Fen.parseCastlingState` accepts both the classical `K/Q/k/q` letters
   *and* Shredder letters `A–H` (white) / `a–h` (black). Both branches set
   the existing castling bits and populate `castlingRookFiles`.
4. Tests:
   - `FenChess960ImportTest` — a handful of 960 FENs including the
     `rkbbnrnq/.../RKBBNRNQ w FAfa` position seen from Cute Chess.
   - Regression test: standard FEN imports produce bit-identical `Board`
     state to today, including default rook files.

### Phase 2 — castling move generation

1. Replace literal king and rook squares in `MoveGenerator` with lookups
   driven by king position (already on the board) and
   `Board.castlingRookFiles[slot]`.
2. Generalize the "all path squares empty" and "no path square attacked"
   checks to use the slot's actual king path and rook path.
3. Update `Board.makeMove` and `revertMove` for `typeCastlingKingSide` /
   `typeCastlingQueenSide` to use the slot's rook source and target.
4. Key edge cases to cover in tests:
   - King and rook adjacent on the back rank.
   - Rook source square equals king target square (rook needs to vacate it).
   - Rook target square equals king source square (mutually overlapping).
   - The 960-correct path semantics: every square between king source and
     king target must be empty *except* the rook source square; every square
     between rook source and rook target must be empty *except* the king
     source square.

### Phase 3 — UCI castle move notation

1. `UciMoveParser.parse` accepts the king-to-rook-source form
   (`e1h1` = kingside, `e1a1` = queenside) in addition to the legacy
   king-destination form (`e1g1`, `e1c1`).
2. `UciMoveParser.toUci` emits the king-to-rook-source form when the
   `UCI_Chess960` option is on, the king-destination form otherwise.
3. `UciHandler` tracks the `UCI_Chess960` option state set via `setoption`
   and threads it into the parser/formatter calls.

### Phase 4 — FEN export

1. `Fen.exportFEN` detects whether all four `castlingRookFiles` entries match
   the standard defaults; if so, emit `KQkq` for backward compatibility.
2. Otherwise emit Shredder letters derived from the actual rook files.

### Phase 5 — Tests and integration

1. Mirror-eval test on a small set of 960 starting positions, equivalent to
   the existing `MirrorEvalTest` for standard chess.
2. Verify that the opening-DB lookup path tolerates 960 positions (it should,
   since lookups are pure Zobrist-keyed) — the existing DB will simply miss
   on 960 starts, which is the desired behavior.
3. End-to-end FEN round-trip: import 960 FEN, play a move, export FEN,
   import again — Zobrist hash matches the first import after one undo.

## Open items / pending decisions

- **Branching.** Phases are small enough to keep on `master` with green tests
  between each. A feature branch becomes attractive if ELO measurement runs
  start to overlap with development of phase 2 (where engine play behavior
  changes).

## Notes

- **`UCI_Chess960` option declaration stays in place.** It was added to
  `handleUci` to make Cute Chess offer myChess in the 960 variant picker.
  Between phase 1 and the end of phase 2 the engine can load 960 positions
  but does not generate castling moves, which is weak play but not a
  protocol violation — the GUI cannot tell the difference between "engine
  chose not to castle" and "engine cannot castle". Keeping the option
  declared during development is also the only way to drive 960 sessions
  through the GUI for testing. After phase 2 the claim becomes fully true,
  so nothing to revisit.
