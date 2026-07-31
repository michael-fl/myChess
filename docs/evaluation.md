# 5. Evaluation Function

[`WeightingFunction.calculate(Board)`](src/main/java/org/michaelfl/mychess/WeightingFunction.java) is the static evaluation — it scores a position without looking ahead. Its result is the leaf value of the search tree, and it is by far the hottest piece of code in the engine.

The evaluation is a **weighted sum of eight components**, all expressed as a delta between white and black, all measured in centipawns at the end:

```
material        (piecesWeight[w] - piecesWeight[b])
+ position      (positionWeight[w] - positionWeight[b])  * 0.5
+ mobility      (mobilityWeight[w] - mobilityWeight[b])  * 0.1
+ threats       (threadWeight[w]   - threadWeight[b])    * 0.02
+ castling      (castlingState[w]  - castlingState[b])   * 0.25
+ opening       (openingState[w]   - openingState[b])    * 0.1 * decay(ply)
+ checks        (chessCount[w]     - chessCount[b])      * 0.25
+ doublePawns   (doublePawnCount[w] - doublePawnCount[b]) * (-0.1)
```

Positive = white is better, negative = black is better. The result is rounded to a centipawn integer.

Crucially, computing the mobility, threat, check, and castling-state components requires **a full pseudo-move-generation pass for every own piece on the board**, very similar to what `MoveGenerator` does but specialized for scoring rather than producing a move list. That second pass is why static evaluation is expensive and why the engine has a *material-only shortcut* for positions that have already swung wildly in material terms (see [§ 7.3](search.md#73-material-only-evaluation-shortcut)).

`WeightingFunction` is **stateful and reused**. Each `PositionSearch` constructs one `WeightingFunction` and calls `.calculate(board)` repeatedly. The fields are 2-element arrays indexed by color (`0 = white`, `1 = black`), reset to zero on every `calculate(...)` entry.

## 5.1 Material weight

The classical 1/3/3/5/9 valuation, in centipawns:

```java
public final static int[] weightOfPiece = new int[Board.blackKing + 1];
static {
    weightOfPiece[Board.whitePawn]   = 100;
    weightOfPiece[Board.whiteKnight] = 300;
    weightOfPiece[Board.whiteBishop] = 300;
    weightOfPiece[Board.whiteRook]   = 500;
    weightOfPiece[Board.whiteQueen]  = 900;
    weightOfPiece[Board.whiteKing]   = 0;   // ← kings count zero
    weightOfPiece[Board.blackPawn]   = 100;
    …
    weightOfPiece[Board.blackKing]   = 0;
}
```

Kings count zero because they cannot be captured in a legal game — capturing the king is the king-capture-trick sentinel (see [§ 4.5](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection)) and is replaced in the search by checkmate scoring (see [§ 6.6](search.md#66-checkmate-and-stalemate-scoring)). The material weight assigned to a king-capture would otherwise dwarf everything else and break alpha-beta windows.

**Accumulated per color** in the main scan loop of `WeightingFunction.calculate`:

```java
for (int field = Board.a1; field < stopField; field++) {
    final byte piece = board[field];
    if (piece != Board.empty && piece != Board.illegal) {
        final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;
        piecesWeight[color] += weightOfPiece[piece];
        …
    }
}
```

The same scan also dispatches to per-piece evaluation via `calculationFunctions[piece]` — a table-of-lambdas parallel to the one in `MoveGenerator` — which adds the mobility, threat, and pawn-structure components.

**Stand-alone material delta** is exposed as a static helper:

```java
public static int calculateMaterialWeight(Board theBoard);
```

It runs the same scan but skips piece-square tables and pseudo-move generation. `PositionSearch` calls this once at the root of every iteration to seed the running `materialWeight` field, which is then incrementally updated as the search descends — see [§ 7.3](search.md#73-material-only-evaluation-shortcut).

**Move-delta helper** is also static:

```java
public static int getMaterialWeightOfMove(int move, int depth);
```

It returns the material change a move causes (captured-piece value, adjusted for promotions: queen − pawn, etc.). Used by the search to maintain `materialWeight` and `materialDelta` incrementally without re-scanning the board after each move.

## 5.2 Piece-square tables

[`PieceSquareTables`](src/main/java/org/michaelfl/mychess/PieceSquareTables.java) holds the per-piece per-square positional bonuses adapted from the [chessprogramming.org *Simplified Evaluation Function*](https://www.chessprogramming.org/Simplified_Evaluation_Function), with one local modification (see footnote below). For every piece type, a 64-value table assigns a bonus or penalty to each square. Examples:

**Pawn (white perspective, 8th rank at top):**

```
 0,  0,  0,  0,  0,  0,  0,  0,
50, 50, 50, 50, 50, 50, 50, 50,   ← 7th rank: huge bonus for advanced pawns
10, 10, 20, 30, 30, 20, 10, 10,
 5,  5, 10, 25, 25, 10,  5,  5,
 0,  0,  0, 20, 20,  0,  0,  0,
 5,  0,-10,  0,  0,-10,  0,  5,
 5,  0,  0,-20,-20, 10,  0,  5,   ← 2nd rank: −20 on d2/e2 forces central pawns forward; b2/c2/g2 zeroed (see below)
 0,  0,  0,  0,  0,  0,  0,  0
```

Local deviation from Simplified: the original table rewards b2/c2/g2 with +10 (and b3/g3 with −5) — bonuses that discouraged queenside and fianchetto development. They were removed when the old hand-rolled `calculateOpeningState` heuristic in [`WeightingFunction`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) was retired (it had its own +10 cp pawn-move bonus for those same files, which conflicted with the PST). A future PeSTO migration ([roadmap § 12.7](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined)) would replace the entire table set.

**Knight (white):** −50 in corners (worst squares for a knight), +15 to +20 in the central 4×4 (best squares — d4/e4/d5/e5 score +20, the surrounding ring +15).

**King (white, midgame table):** strong penalties everywhere except the back rank, with bonuses on b1/g1 (+30 each) and c1/f1 (+10 each) to encourage castled positions. (Note: there is no separate endgame king table — see the special case below.)

**Storage layout.** The tables are stored as `byte[144]` arrays in the **same mailbox layout** as `Board` (so that `table[field]` is a direct lookup with the mailbox index — no coordinate conversion needed). They are built once at class load by parsing the string constants:

```java
private static byte[] createBoard(final String tableString) {
    final byte[] table = Board.createEmptyRawBoard();
    int col = 0, row = 7;
    for (String s : tableString.split(",")) {
        byte weight = (byte) Integer.parseInt(s.trim());
        table[ChessUtil.getFieldFromColAndRow(col, row)] = weight;
        col = (col + 1) % 8;
        if (col == 0) row--;
    }
    return table;
}
```

**Black tables are inverted from white.** `invert(table)` flips the table top-to-bottom so that the 7th rank for white becomes the 2nd rank for black, and so on. A black pawn on a7 gets the same bonus a white pawn on a2 would get.

A 22-entry lookup `piece2table[piece]` maps from piece byte (8–21) to the right per-piece table:

```java
public static int getPieceSquareWeight(final byte piece, final int field) {
    return piece2table[piece][field];
}
```

**Accumulation** in the main scan:

```java
positionWeight[color] += PieceSquareTables.getPieceSquareWeight(piece, field);
```

**Endgame special case for the king.** The king PST encodes a *midgame* king-safety preference (stay back, castle). In an endgame this is wrong — the king should march to the center. myChess handles this with a simple cutoff:

```java
if (!(isEndGame && Board.isKing(piece))) {
    positionWeight[color] += PieceSquareTables.getPieceSquareWeight(piece, field);
}
```

`isEndGame` is `gameStatus.isEndGame()`, which is a placeholder one-liner:

```java
public boolean isEndGame() {
    return plyCount > 60;     // TODO: optimize end-game detection
}
```

This is admittedly crude — true endgame detection would look at remaining material (no queens, few pieces, …) rather than ply count. A `TODO` marker in the code acknowledges the limitation.

**Scale factor.** The position component contributes at `positionFactor = 0.5` to the final sum — half-weighted relative to material. A central knight (+20) is worth 10 centipawns relative to a corner knight (−50): a 35-centipawn swing for the knight position alone.

## 5.3 Mobility

Mobility is the number of squares an own piece can move to (including captures), weighted per piece type. Computed by the per-piece evaluator methods (`calculateForKnight`, `calculateForBishop`, …) which walk the same patterns as `MoveGenerator` but call a scoring helper instead of `addMove`:

```java
private boolean move(final byte movingPiece, final int from, final int to, int color) {
    final byte piece = board[to];
    final int oppositeColor = WeightingFunction.oppositeColor[color];

    if (piece == Board.illegal) return false;

    if (piece == Board.empty) {
        mobilityWeight[color] += mobilityWeightOfPiece[movingPiece];
        return true;
    } else if ((piece & oppositeColor) == oppositeColor) {
        capture(movingPiece, from, to, color, piece);
        return false;
    } else {                              // own piece — blocked
        return false;
    }
}
```

**Per-piece weights** (note: *inverse* to material value):

```java
mobilityWeightOfPiece[Board.whitePawn]   = 20;
mobilityWeightOfPiece[Board.whiteKnight] = 50;
mobilityWeightOfPiece[Board.whiteBishop] = 30;
mobilityWeightOfPiece[Board.whiteRook]   = 10;
mobilityWeightOfPiece[Board.whiteQueen]  =  5;
mobilityWeightOfPiece[Board.whiteKing]   =  0;
```

The intuition: small pieces benefit most from having options because they have fewer squares to begin with. A knight stuck in a corner is much worse than a queen stuck in a corner — the queen will be untangled in a move or two, the knight may need three. The king gets zero because king mobility in the middlegame is *negative*: it would mean the king is exposed.

**Pawn mobility** counts single-step and double-step forward moves directly inside `calculateForWhitePawn` (and the black mirror), since pawns don't go through `move(...)`:

```java
int to = field + Board.LENGTH;
if (board[to] == Board.empty) {
    mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
}
…
if (fieldToRow(field) == 1) {
    to = field + 2 * Board.LENGTH;
    if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty) {
        mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
    }
}
```

**Scale factor.** `mobilityFactor = 0.1` — a single extra knight move is worth 5 centipawns, a single extra queen move is worth 0.5 centipawns.

### Tuning observations

The six per-piece weights are hand-tuned heuristics, never ELO-validated. They are listed as a candidate in [roadmap § 12.7](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined). The inverse-scaling intent is clear once you look at the maximum contribution a single piece can produce, which is remarkably uniform across the four "real" mobility types:

| Piece  | Per-move | Typical max moves    | Max contribution (raw) |
|--------|---------:|---------------------:|-----------------------:|
| Pawn   | 20       | 2  (single + double) |  40                    |
| Knight | 50       | 8  (centralized)     | 400                    |
| Bishop | 30       | 13 (long diagonal)   | 390                    |
| Rook   | 10       | 14 (open file/rank)  | 140                    |
| Queen  | 5        | 27 (full board)      | 135                    |
| King   | 0        | —                    | 0                      |

Two values look weakly justified compared to standard chess-engine literature:

- **Pawn = 20** is high. The per-move bonus treats "pawn can advance" as a structural good in 20-cp units, which conflates *"pawn isn't blocked"* with *"pawn is well-placed"* — not the same thing. Structural pawn metrics (passed / isolated / doubled, see § 5.7 and the roadmap's passed-pawn bullet) would discriminate better.
- **Rook = 10** is flat across all rook placements. A rook on an open file deserves more bonus than one shuffling behind its own pawns; the linear per-move weight makes no such distinction. Modern engines use per-square mobility tables or explicit open-file bonuses to capture this.

Knight, bishop, queen, and king values, on the other hand, match standard engine intuition: knights and bishops are the most mobility-sensitive (short-range / blockable), the queen is already so mobile by nature that extra moves add little marginal value, and the king should not be encouraged to wander during the middlegame.

## 5.4 Threat weight

The "threat" component scores how much enemy material this side is *attacking*. Computed in the same `move(...)` / `capture(...)` helpers as mobility, but on the capture branch:

```java
private void capture(final byte movingPiece, final int from, final int to, final int color, final byte piece) {
    if (piece == oppositeKing[color]) {
        if (turn == color) {
            containsIllegalMove = true;       // we'd capture the king — see § 4.5
        } else {
            chessCount[color]++;              // we're giving check
            threadWeight[color] += 4;         // small bonus for attacking the king
        }
    }
    mobilityWeight[color] += mobilityWeightOfPiece[movingPiece];
    threadWeight[color]   += weightOfPiece[piece];
}
```

(The field name `threadWeight` is a long-standing typo in the codebase for "threat" — kept here verbatim because it appears in `WeightingFunction.toString()` debug output and changing it now would be a churn-only edit.)

The score added per threatened piece is the full **material value** of that piece (`weightOfPiece[piece]`). A knight that attacks the opponent's queen contributes 900 to `threadWeight`. The opponent king contributes a token 4 because its material weight is zero (see [§ 5.1](#51-material-weight)), but a "check" is also separately counted by `chessCount` and gets its own larger factor (see [§ 5.8](#58-check-count)).

A capture also still counts toward **mobility** — moving onto an enemy piece is one of the squares the piece can reach.

**Scale factor.** `threadWeightFactor = 0.02` — deliberately tiny. Attacking the opponent's queen contributes only 18 centipawns to the score, not 900. The threat component is a *positional nudge* to prefer aggressive piece placement, not a substitute for actually winning material. Actually winning material happens in the search. Note: before v4.2.0 myChess's quiescence search resolved only the *same-square exchange chain* at the leaf, so a threatened queen on a square different from the previous capture relied on the main search reaching depth, with `threadWeight` filling the gap. **As of v4.2.0 the QSearch follows *all* captures at every leaf** (see [search § 6.4](search.md#64-quiescence-search)), so that multi-square gap is closed — whether `threadWeight` is still needed is now a live re-test (see the [§ 12.16 closure](roadmap-done.md#1216-remove-threadweight-term-from-the-evaluation-function--investigated-not-productive)).

## 5.5 Castling state

`castlingState[color]` is a small **non-positive** score per side, reflecting how close that side is to having safely castled:

```java
if (game.hasWhiteCastled())                             castlingState[0] =  0;   // done
else if (whiteKingSidePossible && whiteQueenSidePossible) castlingState[0] = -1;   // both rights
else if (whiteKingSidePossible || whiteQueenSidePossible) castlingState[0] = -2;   // one right
else                                                       castlingState[0] = -4;   // can no longer castle
```

The score is always non-positive: castled = 0 (best), one or two rights remaining = small penalty (still recoverable), no rights = larger penalty (irreversible loss). The progression is `0, -1, -2, -4` so that losing rights without castling roughly doubles the penalty each step.

**Inputs come straight from `GameStatus`** — the four "castling-still-possible" bits and the two "has-castled" bits set by `Board.calculateNewCastlingState` after every move. No board scanning needed.

**Scale factor.** `castlingFactor = 0.25` — losing both castling rights without having castled is worth `(0 − (−4)) × 0.25 = 1.0` pawn against the side that lost the rights. That's a meaningful but not overwhelming penalty: still recoverable through other positional advantages, but enough to push the engine toward castling early.

## 5.6 Opening state

`openingState[color]` is another non-positive score that captures *how far this side has progressed past the opening setup*. Computed for each color by `calculateOpeningState()`:

```java
int state = 0;
if (!game.hasWhiteCastled())          state--;
if (board[Board.b1] == whiteKnight)   state--;     // knight still on starting square
if (board[Board.c1] == whiteBishop)   state--;
if (board[Board.f1] == whiteBishop)   state--;
if (board[Board.g1] == whiteKnight)   state--;

int movedPawnCount = 0;
if (board[Board.b2] != whitePawn) movedPawnCount++;
if (board[Board.c2] != whitePawn) movedPawnCount++;
if (board[Board.d2] != whitePawn) movedPawnCount++;
if (board[Board.e2] != whitePawn) movedPawnCount++;
if (board[Board.g2] != whitePawn) movedPawnCount++;
if      (movedPawnCount == 0) state -= 2;
else if (movedPawnCount == 1) state--;

openingState[0] = state;
```

Five contributors, each penalizing "still in opening setup":

| Trigger | Penalty |
|---|---|
| King not castled | −1 |
| Each minor piece still on its starting square (b1, c1, f1, g1) | −1 each (up to −4) |
| Zero of the five tracked pawns (b, c, d, e, g) has moved | −2 |
| Exactly one of those five pawns has moved | −1 |
| Two or more tracked pawns have moved | 0 |

The five tracked pawns are b, c, d, e, g — the **non-rook pawns minus the f-pawn**. The f-pawn is omitted because moving it weakens the diagonal toward the king and is rarely a good opening move; not moving it is not a development *failure*. The a- and h-pawns are omitted because they aren't expected to move early.

Maximum penalty for an undeveloped opening position is `−1 (king) − 4 (minors) − 2 (no pawns moved) = −7`.

**Scale factor with ply decay.** `openingFactor = 0.1`, but with a multiplicative decay based on `plyCount`:

```java
final float openingFactorCorrection =
    plyCount > 20 ? (plyCount > 40 ? 0f : 0.5f) : 1.0f;
```

| Ply count | Multiplier |
|---|---|
| ≤ 20 (first 10 moves per side) | 1.0 — full weight |
| 21 – 40 (move 11–20 per side) | 0.5 — half weight |
| > 40 (after move 20) | 0.0 — disabled |

So a fully undeveloped position at ply 0 is worth `−7 × 0.1 × 1.0 = −0.7` pawns. The same position at ply 30 is worth `−0.35` pawns. By move 21 (ply 40) the opening factor stops contributing entirely — the position is judged on its own merits.

## 5.7 Double-pawn penalty

A "double pawn" here means two same-color pawns stacked on the same file with no enemy piece between them. Counted in the per-pawn evaluator as part of the same forward-square inspection that drives mobility:

```java
// (inside calculateForWhitePawn)
int to = field + Board.LENGTH;
if (board[to] == Board.empty) {
    mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
} else if (board[to] == Board.whitePawn) {
    doublePawnCount[color]++;                  // ← own pawn directly in front
}
```

Note this is the *narrow* form: only pawns **directly** in front of each other count. Two same-color pawns on the same file but separated by something else (e.g. an enemy piece, or a same-color but non-adjacent stack) are not counted. The check is intentionally cheap — one array read on the square immediately above.

**Scale factor.** `doublePawnFactor = -0.1` (note the explicit negative sign). Each doubled pawn costs 10 centipawns to the side that owns it. A doubled c-file ⇒ 10 centipawns penalty for that side; tripled pawns ⇒ 20 centipawns.

## 5.8 Check count

`chessCount[color]` is the number of times `color`'s pieces are currently attacking the *opposite* color's king. (The field name uses "chess" in the German sense of *Schach* = "check".)

Counted inside `capture(...)` whenever the captured piece is the opposite king:

```java
if (piece == oppositeKing[color]) {
    if (turn == color) {
        containsIllegalMove = true;       // would capture our own king — illegal position
    } else {
        chessCount[color]++;              // we attack the opponent's king
        threadWeight[color] += 4;
    }
}
```

Two different code paths converge here:

1. **`turn == color`** — it's our turn, but the evaluation is checking own-piece moves and finds we could capture the opposite king. That can only happen if the *previous* move (the opponent's last) left their king attacked — i.e. they made an illegal move. Set `containsIllegalMove` and abort the evaluation.
2. **`turn != color`** — it's the opponent's turn, we're considering hypothetical moves by our pieces, and one of them could capture the opponent king. That's a check delivered by us.

Multiple checks (double check) are counted multiply, so a discovered check that delivers two attacking pieces contributes 2 to the count.

**Scale factor.** `chessFactor = 0.25`. A single check is worth 25 centipawns to the checking side. A double check is worth 50. This is *positional*, not a replacement for actual mate-finding — actual mate is found by the search bottoming out at a position where the opponent has no legal moves and is in check (see [§ 6.6](search.md#66-checkmate-and-stalemate-scoring)).

## 5.9 Composition formula

All eight components (material plus the seven positional terms) combine in `calculatePositionWeight()`:

```java
private int calculatePositionWeight() {
    if (containsIllegalMove)
        return turn == 0 ? ILLEGAL_WEIGHT_POS : ILLEGAL_WEIGHT_NEG;

    final int plyCount = game.getPlyCount();
    final float openingFactorCorrection =
        plyCount > 20 ? (plyCount > 40 ? 0f : 0.5f) : 1.0f;

    return Math.round((
          (piecesWeight[0]    - piecesWeight[1])    / 100f
        + (positionWeight[0]  - positionWeight[1])  / 100f * positionFactor
        + (mobilityWeight[0]  - mobilityWeight[1])  / 100f * mobilityFactor
        + (threadWeight[0]    - threadWeight[1])    / 100f * threadWeightFactor
        + (castlingState[0]   - castlingState[1])          * castlingFactor
        + (openingState[0]    - openingState[1])           * openingFactor * openingFactorCorrection
        + (chessCount[0]      - chessCount[1])             * chessFactor
        + (doublePawnCount[0] - doublePawnCount[1])        * doublePawnFactor) * 100);
}
```

A few features of this formula worth noting:

- **All components are white−black deltas.** No absolute scoring of a single side. The result is "by how much is white better than black".
- **The result is centipawns** (`* 100` at the end after a `/ 100f` inside each summand). The intermediate float arithmetic exists to make the per-component factors more readable; the final value is rounded to int.
- **The summary is symmetric across colors.** The search caller multiplies by a `weightFactor` (`+1` for white-to-move, `-1` for black-to-move) at the boundary so the negamax-style search always sees "this is good for the side to move".
- **Illegal positions short-circuit** the entire formula with a sentinel value (`±ILLEGAL_WEIGHT = ±1_000_000`), which the search recognizes and uses to discard a candidate move that left its own king in check (see [§ 4.5](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection)).

**Magic-number table for quick orientation:**

| Constant | Centipawns | Meaning |
|---|---|---|
| `MIN_ALPHA` | `−2_147_483_647` | initial alpha bound for the root |
| `MAX_BETA` | `+2_147_483_647` | initial beta bound |
| `ILLEGAL_WEIGHT_POS` | `+1_000_000` | sentinel — own-king-capturable position |
| `ILLEGAL_WEIGHT_NEG` | `−1_000_000` | sentinel — negated form of above |
| `CHECKMATE_WEIGHT_HIGH` | `+200_000` | mate score; mate-in-N is `HIGH − N×100` |
| `CHECKMATE_WEIGHT_LOW` | `+100_000` | lower bound of the mate-score band |

`isCheckmateWeight(w)` returns true iff `|w|` is between `LOW` and `HIGH` — i.e. the value encodes a mate in some number of plies, not a static evaluation. `checkmateWeightToPlies(w)` recovers that ply count: `(HIGH − |w|) / 100`. This range encoding lets the search compare mate scores: mate-in-3 (`200_000 − 300 = 199_700`) is preferred over mate-in-5 (`200_000 − 500 = 199_500`), and a regular evaluation of `+5.00` (= 500 centipawns) is correctly recognized as not-a-mate. See [§ 6.6](search.md#66-checkmate-and-stalemate-scoring) for how the search produces these values.

**Where are pawn structure (passed pawns, isolated pawns, pawn chains), king safety beyond castling, bishop pair, and outposts?** Not implemented. The evaluation is deliberately compact — about 560 lines including all per-piece pseudo-move generation — and trades depth in the evaluator for breadth in the search. The opening-state component captures the most expensive missing piece (development) for the first 20 moves; everything else is left to the search.
