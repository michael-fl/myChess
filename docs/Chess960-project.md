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

A code survey identified seven sites that today assume the standard-chess
starting position (king on `e1`/`e8`, rooks on `a1`/`h1`/`a8`/`h8`, plus
the minor pieces and pawns on their classical squares):

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
5. **`WeightingFunction.calculateOpeningState`** — penalizes White if a
   knight still sits on `b1`/`g1`, a bishop on `c1`/`f1`, or a pawn still
   on `b2`/`c2`/`d2`/`e2`/`g2` (and the black mirror). The whole heuristic
   assumes the standard back rank; in 960 these squares may not even host
   the relevant piece type at game start.
6. **`WeightingFunction.calculateCastlingState`** — applies a graded
   penalty for sides that have not yet castled (or have lost rights). The
   underlying assumption — castling is essentially always desirable — does
   not hold in 960. For some starting positions the corner is exposed or
   the king is already comfortably placed, so refusing to castle is
   legitimate strategy.
7. **`PieceSquareTables.kingTable`** — the king PST has aggressive
   back-rank bonuses (`b1=+30`, `g1=+30`, `c1=+10`, `a1=+20`, `h1=+20`)
   designed to incentivize castling toward the standard-chess corners.
   In 960 roughly half the Scharnagl starting positions place the king
   on one of these high-bonus squares already, which both nullifies the
   castling pull and (for queenside) actively pushes against it. The
   other piece tables encode position-agnostic principles
   (centralization, diagonals, 7th-rank pressure) and likely need no
   adjustment, but deserve a deliberate sanity-check at phase start.

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

| Phase | Status        | Scope                                                        | What it unlocks                                              |
|-------|---------------|--------------------------------------------------------------|--------------------------------------------------------------|
| 1     | done          | FEN import + export for Shredder-FEN castling rights, auto-detection cached on `Board` | Engine loads, exports, and recognises any 960 position; can play it without castling. |
| 2     | in progress   | Generalize castling move generation, `Board.makeMove` undo   | Engine plays full 960 — including castling.                  |
| 3     | pending       | UCI castle move notation (`e1h1` form, in / out)             | Round-trip with cutechess and other 960-aware GUIs works.    |
| 4     | done in P1    | FEN export in Shredder style when files deviate from default | FEN round-trip closes for 960. Landed alongside Phase 1.     |
| 5     | partial       | Test consolidation and edge cases                            | 960 mirror-eval, opening-DB hooks, full round-trip suite.    |
| 6     | pending       | Evaluation adjustments for 960                               | Eval no longer penalizes 960-legitimate piece placements.    |

### Phase 1 — FEN import, export, auto-detection (done — commits 89871df, b8bac8c, 4ea58d2)

1. Introduce `Board.castlingRookFiles` and `CastlingSlot` enum.
2. `Board.createNewGame()` initializes to `{ 0, 7, 0, 7 }`.
3. `Fen.parseCastlingState` accepts both the classical `K/Q/k/q` letters
   *and* Shredder letters `A–H` (white) / `a–h` (black). Both branches set
   the existing castling bits and populate `castlingRookFiles`.
4. **Phase 4 also landed here:** `Fen.exportShredderFEN` plus a Shredder
   fallback inside `exportFEN` when any rook file deviates from
   `{ 0, 7, 0, 7 }`. Standard chess still emits classical `KQkq` for
   backward compatibility.
5. **Variant auto-detection (extension beyond the original plan):**
   `Board.isChess960Position` walks a three-stage detector (rook-file
   check → king-file-with-alive-castling check → structural fallback
   against the standard back rank). Result is cached at construction
   time in the final `is960` field and exposed via `isChess960()` /
   `isStandardChess()`. The flag survives the copy constructor, which
   is pinned by `BoardTest.is960_isCarriedByCopyConstructor`. Moved
   from `Game` to `Board` in commit 4ea58d2 so callers in the
   move-generator hot path can ask `board.isStandardChess()` without
   re-running the detector. **Known limitation, intentional:** a 960
   game with rook files `{ 0, 7 }` and king on `e1`/`e8` that has
   already left the starting position is classified as standard
   chess. Such a position is rules-equivalent to standard chess in
   every relevant aspect, so the misclassification is harmless.
6. Tests:
   - `FenChess960ImportTest` — Shredder import including the
     `rkbbnrnq/.../RKBBNRNQ w FAfa` position seen from Cute Chess.
   - `BoardTest` — `isChess960Position_*`, `isStandardStartPosition_*`,
     plus the `Board.copy()` is-960 carry-over regression.
   - `Chess960StartPositionsTest` — exhaustive walk through all 960
     Scharnagl positions.
   - Standard FEN imports produce bit-identical `Board` state to before.

### Phase 2 — castling move generation (in progress)

**Done:**

1. `MoveGenerator.calculateCastlingMoves(int kingField)` now takes the
   king's actual source field and dispatches to standard / 960 helpers
   via `theBoard.isStandardChess()`. The four `canDoXxxCastlingYyy960`
   helpers use `castlingRookFiles[slot]` to find the partner rook and
   verify path clearance against the *actual* back-rank squares, not
   the hardcoded standard-chess paths. (commit 155ee56)
2. The `MoveGenerator.game` field was renamed to `gameStatus` along the
   way — cosmetic, matches its type and removes ambiguity with the
   `Game` class. (commit 155ee56)
3. `isCastlingPathEmpty` now takes the **field** of the castle partner
   instead of the piece type — signature is
   `(int startField, int targetField, int counterpartField)` and the
   skip predicate is `f != counterpartField`. The previous
   piece-type check accepted *any* own rook on the path, which let a
   non-partner rook (e.g. the queenside rook that had moved onto the
   kingside-castle path) pass as harmless. Pinned by the four
   `Chess960CastlingTest.{king,queen}sideCastle_isIllegalWhenOwn…RookSitsOnKingsPath_{white,black}`
   regression tests added in commit e1a28a1.

**Pending** (next implementation steps):

1. **`Board.makeMove` / `revertMove` still hardcode `e1`/`e8`/`h1`/`a1`
   and the corresponding rook target squares.** See
   `_makeCastlingKingSideMove` (and three siblings) at
   `Board.java:715-779`: the dispatch `if (fromField == e1) { … } else { … }`
   assumes any non-`e1` source is a black king on `e8`, which silently
   corrupts the board when a white 960 king castles from, say, `b1`.
   The four `BoardTest.makeCastling{KingSide,QueenSide}Move_{white,black}_chess960_*`
   tests pin the bug: the two white cases fail with a board mismatch
   (rook left on its source square, phantom rook conjured on the
   wrong back rank); the two black cases happen to leave a
   board-correct result because the hard-coded `h8/f8` / `a8/d8`
   squares coincide with the test setup's rook files, so the
   incremental Zobrist update is the only visible failure
   (`getPositionHash()` diverges from a fresh
   `calculatePositionHash()`). Fix: route on `Move.getMoveType`
   (already in the packed move) plus the slot's
   `castlingRookFiles[]` entry to compute the rook source and
   target from the king's actual `fromField`, and update the
   Zobrist hash off those same dynamic fields.

**Key edge cases to keep covered** (all exercised by
`Chess960CastlingTest`'s 120-case parameterised matrix plus the
four non-partner-rook spot tests):

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

### Phase 4 — FEN export (done — landed during Phase 1, commit b8bac8c)

Landed earlier than the plan called for, as a natural companion to
the Shredder import work:

1. `Fen.exportShredderFEN` always emits Shredder letters.
2. `Fen.exportFEN` detects whether all four `castlingRookFiles` entries
   match the standard defaults; if so it keeps emitting `KQkq` for
   backward compatibility, otherwise it falls back to the Shredder
   form.

`Board.exportShredderFEN()` is wired up; the REPL's `pgn` / status-line
display picks the right form via `is960()`.

### Phase 5 — Tests and integration (partial)

**Done:**

1. `FenChess960ImportTest` — round-trip and edge-case coverage for
   Shredder/X-FEN castling parsing.
2. `Chess960StartPositionsTest` — exhaustive table walk plus the
   helpers that drive auto-detection tests.
3. `Chess960CastlingTest` — 120 parameterised castling cases plus
   spot tests for king-in-check, attacked king-path, attacked rook,
   attacked rook-only path squares, and the four failing tests for
   the non-partner-rook-in-path bug (waiting on the Phase 2 fix).

**Pending:**

1. Mirror-eval test on a small set of 960 starting positions, equivalent
   to the existing `MirrorEvalTest` for standard chess. `MirrorEvalTest`
   itself has no 960 cases today.
2. Verify that the opening-DB lookup path tolerates 960 positions (it
   should, since lookups are pure Zobrist-keyed) — the existing DB
   will simply miss on 960 starts, which is the desired behavior.
3. End-to-end FEN round-trip: import 960 FEN, play a move, export FEN,
   import again — Zobrist hash matches the first import after one undo.
4. End-to-end engine self-play on a non-standard Scharnagl position via
   the UCI handler — gated on Phase 2 (`Board.makeMove` fix) and Phase 3
   (UCI parser/formatter for the king-to-rook-source form).

### Phase 6 — Evaluation adjustments for 960

Today's `WeightingFunction` has two heuristics that assume the standard
starting position. Neither breaks correctness in 960, but both nudge play
toward standard-chess opening patterns that may not apply.

**Issue 1 — `calculateOpeningState`.** The method awards a development
bonus by checking whether minor pieces still sit on classical starting
squares (`b1`/`c1`/`f1`/`g1` for White) and whether the central pawns
have moved (`b2`/`c2`/`d2`/`e2`/`g2`). In a 960 setup with, say, the
king on `b1`, a knight may start on `g1` *correctly* — the eval would
then never reward developing it, and would also wrongly penalize a
configuration where a knight happens to *land* on `b1` after a move.

Two redesign options to consider (decision deferred to start of phase):

- **(a)** Snapshot the starting back rank on `Board` (next to
  `castlingRookFiles`). The eval then asks per-piece "is this still on
  its starting square?" against the snapshot. Most faithful translation
  of the existing intent.
- **(b)** Replace the square-specific heuristic with a position-agnostic
  development metric: count of minor pieces still on the back rank, or
  centralization scores. Smaller code, but loses some of the targeted
  "central pawns moved → opening progressing" signal that the current
  metric provides.

**Issue 2 — `calculateCastlingState`.** Today the function applies a
penalty (-1, -2, or -4) for sides that have not yet castled, plus zero
for sides that have. The implicit assumption "castling is always good"
does not hold universally in 960. For some 960 setups castling worsens
king safety (e.g. starting positions with the rook adjacent to the king
and corner pawns weak after the rook moves away).

Possible directions:

- **(a)** Keep the penalty but tone it down significantly in 960 (smaller
  magnitudes, or zero unless king safety is otherwise compromised).
- **(b)** Remove the function entirely and let the king-safety component
  of the eval — which already considers attacked king-zone squares —
  encode this implicitly.
- **(c)** Detect whether we are in 960 (any `castlingRookFiles` entry
  deviates from the standard default) and gate the legacy penalty on
  that.

**Issue 3 — King piece-square table.** The current king PST rewards
the back-rank corners aggressively to encode "castle the king to
safety": `b1=+30`, `g1=+30`, `c1=+10`, `a1=+20`, `h1=+20`. In standard
chess the king starts on `e1` (PST 0), so any castling direction is a
clear positive delta and the table works as designed. In 960 the king
already starts on one of these high-bonus squares in roughly half the
Scharnagl positions:

| Scharnagl | King start | PST start | Kingside (`g1`) | Queenside (`c1`) |
|-----------|------------|-----------|-----------------|------------------|
| 518 (standard) | `e1` | 0 | +30 (Δ +30) | +10 (Δ +10) |
| 0 (`BBQNNRKR`) | `g1` | +30 | +30 (Δ 0) | +10 (Δ −20) |
| 959 (`RKRNNQBB`) | `b1` | +30 | +30 (Δ 0) | +10 (Δ −20) |
| `RKBBNRNQ` (cutechess sample) | `b1` | +30 | +30 (Δ 0) | +10 (Δ −20) |

In those positions the PST gives zero pull toward kingside castling
and an active −20 push *against* queenside castling. That magnitude is
unlikely to be what we want even if "skip castling" is a legitimate
strategy here.

Two redesign options:

- **(a)** Replace the king PST with a flatter back-rank profile in 960
  (e.g. uniform `+10` across rank 1, retaining the strong central
  penalties on ranks 4–8). Loses the targeted castling-destination
  signal but stops mis-evaluating non-`e1` starting squares.
- **(b)** Compute the king PST dynamically per game: zero out the
  rank-1 bonus on the king's actual starting square, retain the
  castling-target bonuses on `g1`/`c1`. Requires the starting back-rank
  snapshot from Issue 1 option (a). More precise but more state.

For the other PSTs (pawn / knight / bishop / rook / queen) the bonuses
encode position-agnostic principles (centralization, open lines,
diagonals, 7th-rank pressure). No clear 960-specific defect, but a
deliberate read-through is on the phase-start checklist.

**The one nuance worth recording on the pawn PST:** rank 2 carries a
targeted `-20` on `d2` / `e2` to push the central pawns. In a 960
position where the king starts on `d1` or `e1` *and* castling is
unattractive, those central pawns are useful king cover and the
penalty is misaligned. Filter chain: king on `d`/`e` file (~15–20 % of
Scharnagl positions) ∩ no good castling alternative (a minority of
those) ∩ the `−20` is decision-relevant against material / king-safety
terms (a minority again). Net 1–2 % of 960 games where the pawn PST
nudges the wrong way — and a 960-specific override would lose the
central-push signal in the larger majority of 960 games where the
king is *not* on `d`/`e`. The expected-ELO calculus favors leaving the
pawn PST alone; flagging here so the question doesn't keep
re-surfacing.

**Decision deferred until phase 6 starts.** Option (a) for Issue 1 is
the most conservative path that preserves today's behavior in standard
chess exactly; option (c) for Issue 2 has the same property; for Issue 3
either option must be gated on "is this a 960 game?" to leave standard
chess untouched. Either way, ELO measurement before/after the change is
the litmus test — this is the first phase that can actually change
playing strength in standard chess if we are not careful.

## Open items / pending decisions

- **Branching.** Phases continue to land on `master` with green tests
  between each (modulo the four `BoardTest.makeCastling*Side_*_chess960_*`
  Phase-2 regression tests, which are red on purpose and pin the
  remaining `Board.makeMove` gap). The earlier worry about
  ELO-measurement overlap did not materialize: the no-resign
  calibration runs against Stockfish use standard openings only,
  so Phase-2 churn on the 960 castling path does not affect
  those numbers.
- **`no-opening-weight` experiment branch.** Still unmerged. The +163
  ELO claim it once produced was measured before the UCI sign-bug was
  fixed and is therefore not trustworthy. Whether to keep, re-measure,
  or drop the branch belongs in the Phase-6 conversation.

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
