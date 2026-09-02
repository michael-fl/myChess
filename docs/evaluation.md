# 5. Evaluation Function

[`WeightingFunction.calculate(Board)`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) is the static evaluation — it scores a position without looking ahead. Its result is the leaf value of the search tree, and it is by far the hottest piece of code in the engine.

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
    weightOfPiece[Board.whiteQueen]  = 1000;  // 900 until v4.3.2; see note below
    weightOfPiece[Board.whiteKing]   = 0;   // ← kings count zero
    weightOfPiece[Board.blackPawn]   = 100;
    …
    weightOfPiece[Board.blackKing]   = 0;
}
```

**Why the queen is 1000 and not the textbook 900.** It was 900 until v4.3.2. The change
came out of a tapered-evaluation experiment that measured *neutral*: a joint endgame-PST
tune produced what looked like an endgame-material signal, but re-reading it showed a
uniform per-piece offset — which is a statement about **material**, not about squares. The
queen was simply undervalued relative to the rook in the midgame (1.8× rather than 2.0×).
Raising it outright captured the whole effect at **+12.6 Elo**, with no phase dependence,
and the tapered-material idea was shelved as redundant ([roadmap § 12.7.3](roadmap.md)).
The other four values are unchanged from the classical scale.

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

[`PieceSquareTables`](../src/main/java/org/michaelfl/mychess/PieceSquareTables.java)
holds a positional bonus per piece kind and square. It is the largest positional term
by a wide margin, and since v4.4.0 the single most valuable one: adopting the PeSTO
values was worth **+32.6 ± 12.4 Elo** on its own.

**The numbers are not reproduced here, on purpose.** There are 24 tables of 64 values —
six piece kinds × midgame/endgame × the pre-inverted black copy. Copying 1 536 numbers
into Markdown is a maintenance promise nobody keeps; this chapter carried the *previous*
generation of tables long after they had been replaced, which is precisely the failure
mode. Read the values from the class; read the shape from here.

### What the tables are

Twelve logical tables, one midgame and one endgame per piece kind, each stored as
`short[]` and each with a pre-inverted counterpart for black
(`pawnTableWhite` / `pawnTableBlack`, and so on) so the lookup is an array index and
never a coordinate flip in the hot path.

The values derive from **PeSTO** (Ronald Friederich, RofChade), credited in the class
header and in the [README](../README.md#credits-and-third-party-material). Each entry is
`PeSTO(square) + PeSTO(mirrored square)` — mirror-averaging and doubling in one step,
which makes the tables left/right symmetric (a↔h, b↔g, c↔f, d↔e) and puts them on
myChess's centipawn scale. The symmetry matters: PeSTO's raw knight-endgame table has
+10 on one square and −9 on its mirror, a 19 cp difference between positions that are
equivalent by reflection, and `MirrorEvalTest` would flag that.

**Storage is `short`, not `byte`, and that is load-bearing.** The doubled values exceed
±127. An earlier tapered attempt stored them in `byte[]`, silently overflowed, and
measured **−15.6 Elo**; the tuning that looked like a failure was a container bug.

### How a table reaches the score

Two lookups per piece, blended by game phase:

```java
positionWeight[color] = blend(pstMidGameWeight[color], pstEndGameWeight[color], phase);
// blend(mg, eg, phase) = (mg·phase + eg·(MAX_PHASE − phase)) / MAX_PHASE
```

`phase` is accumulated in the same piece loop that sums material, from
`phaseWeightOfPiece`: knight and bishop 1, rook 2, queen 4, pawn and king 0. A full
board sums to `MAX_PHASE = 24`; as pieces come off it falls toward 0, sliding the score
from the midgame tables to the endgame ones. The weights are deliberately *fixed* rather
than derived from the tunable material values, so the phase stays constant for a given
position and the evaluation remains linear in its tunable parameters — which is what
makes Texel tuning possible at all (see
[tapered-evaluation.md](tapered-evaluation.md)).

Rounding uses `roundSymmetric` — round half away from zero — so that
`round(−x) == −round(x)`. An asymmetric rounding would introduce a side bias the moment
the midgame and endgame tables differ.

### The shape, in one example

The midgame pawn table, white's perspective with the 8th rank at the top, as an
**excerpt for orientation only**:

```
  0,   0,   0,   0,   0,   0,   0,   0,
 87, 168, 187, 163, 163, 187, 168,  87,   ← 7th rank: about to promote
-26,  32,  82,  96,  96,  82,  32, -26,
-37,  30,  18,  44,  44,  18,  30, -37,
-52,   8,   1,  29,  29,   1,   8, -52,
-38,  29,  -1,  -7,  -7,  -1,  29, -38,
-57,  37,   4, -38, -38,   4,  37, -57,
  0,   0,   0,   0,   0,   0,   0,   0
```

Three things generalise from it. Rank bonuses dominate for pawns and grow sharply toward
promotion. Central files beat flank files at equal rank, and the a- and h-files carry
outright penalties. And every row reads the same left to right as right to left — the
mirror symmetry described above, visible directly.

The odd values are not typos. Each entry is a *sum of two* PeSTO values, so mixed parity
is expected; the class comment works through `1 = 10 + (−9)` as an example.

The endgame counterpart of the same table is flatter across files and steeper across
ranks — in an endgame a pawn's file matters less and its distance from promotion matters
more. That difference *is* the tapered evaluation; before v4.3.0 there was one table and
a crude `plyCount > 60` switch that simply dropped the king table.

### A local deviation worth remembering

The Simplified tables myChess used before v4.3.0 rewarded b2/c2/g2 with +10 and
penalised b3/g3 with −5 — bonuses that discouraged queenside development and the
fianchetto. Those squares were zeroed at the time, and the reasoning survived the move
to PeSTO: a table that rewards a piece for *staying home* fights the rest of the
evaluation.

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
mobilityWeightOfPiece[Board.whitePawn]   =  5;
mobilityWeightOfPiece[Board.whiteKnight] = 40;
mobilityWeightOfPiece[Board.whiteBishop] = 30;
mobilityWeightOfPiece[Board.whiteRook]   = 20;
mobilityWeightOfPiece[Board.whiteQueen]  =  3;
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

## 5.9 King-line danger

The three files at and beside the king, each classified on an ordered scale, summed, and looked up
in a fitted table. Introduced in `4.6.0-king-line`.

| The file, walking away from the king | Level |
|---|---:|
| an own pawn shelters it | 0 |
| half-open, the enemy pawn still on its own half | 1 |
| half-open, the enemy pawn past the middle | 2 |
| open | 3 |
| open, with an enemy rook or queen on it | 4 |

Summed over three files that is 0–12, and the sum — not the individual level — indexes the penalty:

```
danger:  0    1    2    3    4    5    6    7    8    9   10   11   12
cp:      0   21   42   42   77   91   95  134  138  223  223  223  223
```

**Three properties of that table are measurements rather than choices.** It comes from isotonic
least squares against Stockfish's *static* NNUE evaluation minus this engine's, over the 39,619
positions of the Chess960 self-play corpus, phase-scaled, with monotonicity as a constraint of the
fit; intervals from a block bootstrap. Equal neighbors (42/42) are the fit saying it cannot
separate those two levels. And indices 10–12 carry index 9's value deliberately: their own fitted
values rest on 0.56 % of samples between them, which is the occupancy at which a coefficient means
nothing.

**Why the sum and not the parts.** Three half-open files are worse than three times one half-open
file, and putting the non-linearity in the *index* rather than in per-file values is what lets the
fit find that shape instead of assuming it.

**Direction.** The walk starts one square in front of the king and runs to the far rank, so
everything behind the king is ignored. Pawn shelter is directional — a pawn behind the king covers
nothing — and the walk is anchored on the king rather than the back rank for the same reason: an
own pawn the enemy has already walked past is not cover. The cost is a real blind spot, an enemy
rook *behind* an advanced king, accepted because that rook is a concrete threat one ply deep which
the search reads better than a static level could.

**Phase.** `blend(table[danger], 0, phase)` — full strength in the midgame, nothing in the endgame.
That is not a refinement but the term's central assumption: an exposed king in an endgame is often
an *active* king, so the sign of king exposure reverses there.

**Scale factor.** `kingLinePenaltyFactor = -0.01f`. Negative, like `doublePawnFactor` and
`undefendedPiecesFactor` — the penalty is a positive "how bad is it" quantity and the sign lives in
the factor. At `-0.01` the fitted table applies at exactly 1:1 in centipawns, because everything
inside the composition sum is in pawns and the `* 100` is outside; so it is not a cautious starting
value but the one at which the table means what it was fitted to mean. It is the ninth entry of
`TUNABLE_FACTOR_NAMES`.

**Cost.** Three files of at most eight squares per king, computed inside the piece walk the
evaluation performs anyway rather than as a separate pass. Measured at **−5.55 % NPS** on a
bit-identical tree; for contrast the shelved attack-unit term of § 12.21 cost −21.9 %. See
[bench-history](bench-history.md).

**What the fit does and does not license.** It says this quantity accounts for 2.238 % of what
separates this evaluation from Stockfish's, against 1.270 % for the attack-unit term that was
shelved at −42.9 Elo. That is a screen result, not an Elo prediction. See
[king-safety.md § 4.11](king-safety.md).

## 5.10 Composition formula

All ten components (material plus the nine positional terms) combine in
`calculatePositionWeight()`:

```java
private int calculatePositionWeight() {
    if (containsIllegalMove)
        return turn == 0 ? ILLEGAL_WEIGHT_POS : ILLEGAL_WEIGHT_NEG;

    return roundSymmetric((
              (piecesWeight[0] - piecesWeight[1]) / 100f
            + (positionWeight[0] - positionWeight[1]) / 100f * positionFactor
            + (mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor
            + (threadWeight[0] - threadWeight[1]) / 100f * threadWeightFactor
            + (castlingState[0] - castlingState[1]) * castlingFactor
            + (chessCount[0] - chessCount[1]) * chessFactor
            + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor
            + (undefendedPiecesCount[0] - undefendedPiecesCount[1]) * undefendedPiecesFactor
            + ((bishopCount[0] >= 2 ? 1 : 0) - (bishopCount[1] >= 2 ? 1 : 0)) * bishopPairFactor
            + (calculateKingLinePenalty(0) - calculateKingLinePenalty(1)) * kingLinePenaltyFactor)
            * 100);
}
```

> **This listing was three terms out of date until 2026-09-02** — it omitted the undefended-pieces
> penalty and the bishop-pair bonus and still showed an `openingState` term and a `plyCount`-based
> opening correction that the class no longer has. A stale copy of production code in a document is
> worse than no copy, because it reads as authoritative. Re-paste it from
> `WeightingFunction.calculatePositionWeight()` whenever a term is added.

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

**Where are pawn structure (passed pawns, isolated pawns, pawn chains), king safety beyond castling, and outposts?** Not implemented. (The **bishop pair** no longer belongs on this list — it landed in v4.3.3 as a fixed +0.4-pawn bonus wired as the 8th tunable Texel factor, worth +31.3 ± 24.1 Elo, the largest single evaluation gain of the tapered series.) The evaluation is deliberately compact — about 560 lines including all per-piece pseudo-move generation — and trades depth in the evaluator for breadth in the search. The opening-state component captures the most expensive missing piece (development) for the first 20 moves; everything else is left to the search.
