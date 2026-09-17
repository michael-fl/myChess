# 5. Evaluation Function

[`WeightingFunction.calculate(Board)`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) is the static evaluation — it scores a position without looking ahead. Its result is the leaf value of the search tree, and it is by far the hottest piece of code in the engine.

The evaluation is a **weighted sum of nine components**, all expressed as a delta between white and black, all measured in centipawns at the end:

```
material            (piecesWeight[w]         - piecesWeight[b])
+ position          (positionWeight[w]       - positionWeight[b])       * 0.5
+ mobility          (mobilityWeight[w]       - mobilityWeight[b])       * 0.1
+ threats           (threadWeight[w]         - threadWeight[b])         * 0.02
+ castling          (castlingState[w]        - castlingState[b])        * 0.25
+ checks            (chessCount[w]           - chessCount[b])           * 0.25
+ doublePawns       (doublePawnCount[w]      - doublePawnCount[b])      * (-0.15)
+ undefendedPieces  (undefendedPiecesCount[w] - undefendedPiecesCount[b]) * (-0.1)
+ bishopPair        (hasPair[w]              - hasPair[b])              * 0.4
```

The factors are exactly `WeightingFunction.tunableFactorValues()`; `TUNABLE_FACTOR_NAMES` is the
canonical list and this block must match it. Note that only `position` and `mobility` are divided
by 100 inside the sum — the other components are already in pawn units — and the whole sum is
multiplied by 100 at the end, which is why a factor of `0.25` on a `castlingState` delta of `4`
reads as one pawn.

> **Corrected 2026-09-09, and the mistake is instructive.** This block listed **eight** components
> and included an `opening` term with a `decay(ply)` factor. The listing of the final formula
> further down in this document had already been corrected on 2026-09-02, with a note saying it
> "still showed an `openingState` term and a `plyCount`-based decay" — but that correction was
> applied only to the code listing, not to this overview, and not to the descriptive section that
> explained the term. So the document contradicted itself for a week: a whole section documented
> `openingState`, and a note a hundred lines below recorded its removal. `openingState` and
> `calculateOpeningState()` exist neither in the production code nor in the tests. The two
> components that were missing here, `undefendedPieces` and `bishopPair`, and the wrong
> `doublePawns` factor (−0.1 for the actual −0.15) came from the same partial fix.

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

**A halving and an endgame fade were measured and did not ship**; § 5.5.2 records the campaign and why.

### 5.5.1 Measured 2026-09-09 — load-bearing, and too strong

These were the last hand-written numbers in the evaluation. `castlingFactor = 0.25f` dates from
`dabec30`, the original implementation, and the `0 / −1 / −2 / −4` progression from `2cd229a`;
neither had ever been tuned or swept, although `castlingFactor` has been wired into
`TUNABLE_FACTOR_NAMES` the whole time.

**The term is load-bearing.** A 400-game self-play match of a `castlingFactor = 0` build against
4.6.1 at `tc=40/20`, read for the castling *rate* rather than for Elo:

| arm | sides | castled | rights lost without castling |
|---|---:|---:|---:|
| `castlingFactor = 0` | 400 | **67.0 %** | 32.8 % |
| 4.6.1 | 400 | **97.3 %** | 2.5 % |

**A 30.3 pp drop, twelve standard errors**, against a threshold of 5 pp fixed before the run, and
stable across it (31.7 → 33.7 → 32.5 → 30.3 as the sample grew). The hypothesis this measurement
was built to test — that the term is redundant because rank 1 of the king midgame table already
pays 53 cp for `e1 → g1` — is **refuted**. The reason the arithmetic misled: the piece-square table
prices the *square* the king stands on and pays the same for a king that walks to g1, while this
term prices the *state* "rights lost, never castled". Only the latter is an incentive to complete
the castling rather than to rearrange the king on foot.

**But the magnitude is wrong, and three independent measurements say so.**

*The match itself.* The arm without the term scored `+20.9 ± 28.9` Elo, LOS 92.1 %. The null is
inside the interval so this is not a finding — but the build without the term did not play worse.

*Two Texel fits of the four levels* (`CastlingStateTexelData` / `TexelCastlingStateTuner`, with
"castled" pinned at 0 since only the difference reaches the score):

| level | shipped | `hybrid` (1.49 M) | `human-masters` (855 k) |
|---|---:|---:|---:|
| castled | 0 | 0 *(pinned)* | 0 *(pinned)* |
| both rights | −25 | **+9.5** | **+26.0** |
| one right | −50 | −7.0 | +10.5 |
| no rights | −100 | −4.0 | −20.0 |
| **span** | **100 cp** | **16.5 cp** | **46.0 cp** |

Both corpora shrink the span drastically, and both put "both rights" *above* "castled" — the
opposite of the shipped shape's central claim.

**The fits are not usable as a template, and that matters more than their values.** They disagree
on the basic question: on `hybrid` the shipped values make the prediction **worse than no term at
all** (0.070870 against 0.069934), on `human-masters` better (0.157077 against 0.157318). That is
§ 4.3 of `king-safety.md` reproduced — the corpora genuinely differ and the Zurichess-derived one
is the outlier. Both fits are non-monotone, and in both the violation sits on the "one right"
level, which carries 2.4–2.6 % of king states and is flagged as thin by the tuner itself. And the
improvement is at the edge of what has ever meant anything: `hybrid` gains 0.000050 over no term,
`human-masters` 0.000473 — against the king-line re-fit, which gained **0.00053** and then
measured **0 Elo**.

**What the three measurements do agree on is that the term is too strong.** The cheapest test of
that is one parameter, monotone, shape unchanged: `castlingFactor = 0.125f`, i.e.
`0 / −12.5 / −25 / −50`. It sits between the shipped 100 cp span and the fitted 16.5–46 cp, needs
no choice between two disagreeing corpora, and an SPRT against 4.6.1 settles it.

**One open question the match raised and did not answer.** The zero arm — the largest possible
reduction — was the one that scored `+20.9`. If halving measures positive, the next question is
not "reduce further" but why a term that lifts the castling rate by 30 points buys no Elo doing
it, i.e. whether castling is worth as much for myChess as everyone assumes. That is its own
investigation.

### 5.5.2 Measured 2026-09-09 to 2026-09-17 — the opponent effect, and what the factor cannot do

§ 5.5.1 ended with one hypothesis (the term is too strong) and one open question (why a term
that lifts the castling rate by 30 points buys no Elo). Seven runs later the picture is
different from the one that question assumed, and the most valuable thing the campaign
produced is not the new factor.

#### The finding: the castling rate is a property of the pairing, not of the build

**The term scores a *difference* of castling states.** `castlingState[0] - castlingState[1]`
rewards the opponent losing rights exactly as much as it rewards castling. So a build carrying
the full term does not simply castle more — it also plays for the opponent's rights, and that
only pays against an opponent which does not answer it. The castling rate a build shows is
therefore not a number the build has. It is a number the *pairing* has.

Four pairings, same code base, castling rate of the first-named side:

| pairing | rate of the first side | rate of the second |
|---|---:|---:|
| 0.25 vs 0 | 98.4 % | 72.1 % |
| 0.25 vs 0.125 | 98.4 % | 83.5 % |
| 0.25 vs 0.25 | 92.5 % (level) | 92.5 % |
| 0 vs 0 | 88.2 % (level) | 88.2 % |

The same build reads 98.4 % against a defenseless opponent and 92.5 % against itself. The
26-point span § 5.5.1 reported as the term's behavioral range was almost entirely this artifact.

**How it surfaced: the controls did.** The factor sweep (below) was run with both ends of the
known scale included as control points — factor 0 and factor 0.25, whose rates were supposedly
72.1 % and 98.4 %. Neither reproduced. Factor 0 came back at 88.2 % and factor 0.25 at 92.5 %,
and it was the *failure of the controls*, not any of the new points, that exposed the effect.
Without them a factor would have been picked against a scale that does not exist. This is the
methodological result to carry forward: **a sweep whose endpoints are known must include them.**

#### The factor is a weak lever, and 85 % is out of its reach

Six self-play points, 200 games each at `tc=40/20`, both arms carrying the same factor, so the
pairing is neutral by construction:

| factor | castling rate | 95 % CI |
|---:|---:|---:|
| 0 | 88.2 % | ± 3.2 |
| 0.02 | 88.0 % | ± 3.2 |
| 0.05 | 91.0 % | ± 2.8 |
| 0.08 | 91.0 % | ± 2.8 |
| 0.125 | 92.0 % | ± 2.7 |
| 0.25 | 92.5 % | ± 2.6 |

**4.3 points across the whole range**, most of it spent between 0.02 and 0.05, and the curve is
flat from 0.05 upward. Switching the term off entirely still leaves 88.2 %.

The reference the target was set against: over **276,670 master games** of median Elo 2312
(`src/test/resources/large.pgn`, tokenized by `myChess-lab/scripts/master-castling-rate.py`), a
side castles in **87.0 %** of them — 91.9 % as White, 82.0 % as Black. Both sides castle in
77.4 % of games, neither in 3.5 %. myChess at 88.2 % with the term *removed* is already at
master practice; the owner's 85 % target sits *below* what the engine does with no castling term
at all.

**So the preference is not in this term.** It comes from the king midgame piece-square table,
which pays 53 cp for `e1 -> g1` against the 25 cp the term added at 0.25. Moving the rate to 85 %
would mean touching a Texel-tuned table — a different undertaking, with its own Elo risk, and not
one this campaign justified.

#### The phase fade

The flat term charged its full value into positions where the rights it prices cannot be used.
Over the 2886-game anchor gauntlet, 16.3 % of all plies sat at phase 8 or below with the term
speaking, at 99.8 cp on average. The fix is a ramp rather than a straight taper, because scaling
with the raw phase reaches 0.67 at phase 16 — a third off while both queens may still be on the
board, which is backwards; squaring the phase makes it worse (0.44 there), since it falls faster
everywhere rather than later.

```
phase >= 16   1.00     midgame, queens possible
phase 12      0.58
phase  8      0.17
phase <= 6    0.00     a rook and a minor each
```

`CASTLING_RAMP` is a precomputed `int[25]` because the arithmetic contains an integer division
and this runs once per evaluation; it is routed through `blend` so the class keeps a single
interpolation function. `CastlingTaperTest` pins every entry, both thresholds, and the factor.

#### The two Elo measurements

Both fixed-N with the stopping point declared before the run, so the point estimates are
unbiased. Both against `versions/4.6.1`, which carries the flat 0.25 term.

| build | games | score | Elo | LOS |
|---|---:|---:|---:|---:|
| `castlingFactor = 0`, no ramp | 7540 | 2899–2751–1890 | **+6.8 ± 6.8** | 97.6 % |
| 0.125 **and** ramp | 3000 | 1090–994–916 | **+11.1 ± 10.4** | 98.2 % |
| 0.25, **ramp only** | 3000 | 1091–1076–833 | **+1.7 ± 10.6** | 62.6 % |

The middle row was the 4.7.0 candidate. Its interval is the only one that clears zero rather
than touching it, and a shifted SPRT computed afterwards accepts "not worse than −10" at
α = β = 0.05 (llr +5.77 of ±2.94).

**The decomposition the third row was supposed to give does not exist at this sample size.**
The difference between rows two and three is about 9.4 Elo with an interval far wider than that;
separating a 5.4 Elo effect at this error level needs on the order of **11,800 games**. Worse,
the ramp-only arm measured under the thinner conditions of the two: replaying its own PGN
(`myChess-lab/scripts/ramp-window.py`) shows the ramp can change a score — non-zero delta *and*
phase below 16 — in only **5.4 % of plies and 12.5 % of games**, against 11.4 % and 18.7 % for
the halved arm, because the halved factor leaves more games with a non-zero delta. Seven eighths
of that match was played by two builds computing identical numbers.

So the candidate carried both changes as one, and which of them earns the +11.1 was never
established.

#### Why none of it shipped

The candidate's full suite came back with **five failures out of 1391**, all of them absent on
master, and none explained by the 12.5 cp shift the halving causes. Controls at each of the
four factor/ramp combinations separate them:

| test | master | factor 0.125 only | ramp only | both |
|---|---|---|---|---|
| `FactorTexelDataTest` breakdown | pass | pass | **fail** | **fail** |
| `BlunderTest` h3 | pass | **fail** | **fail** | **fail** |
| `EngineTest.testPosition12` | pass | fail | fail | fail |
| `EngineTest.testPosition26` | pass | pass | **fail** | **fail** |
| Chess960 knight retreat | pass | **fail** | pass | **fail** |

**The ramp breaks `analyzeFactors`**, which still emits the castling feature flat as
`(castlingState[0] − castlingState[1]) * 100.0` while the evaluation goes through
`castlingWeight(delta, phase)`. The identity `eval = material + sum(feature * factor)` no
longer holds, so the Texel factor tuner would fit `castlingFactor` against a gradient that
does not match the evaluation. The feature has to carry the same ramp.

**`testPosition12` is an improvement and reads as one**: it pins `b2-a3`, recorded in its own
comment as v4.6.0's most expensive regression at 2.2 pawns, and every weakened variant plays
`g5-e6` instead — Stockfish's best move at +4.47.

**The other three are real, and none of them originates at the root.** In the h3 position
(phase 19, above `CASTLING_FULL_PHASE`) the ramp-only build's evaluation is identical to
4.6.1 to the last digit, and in the Chess960 position the castling delta is zero in all
builds. They are search-order effects of scores that changed deep in the tree, where material
has been traded down into the ramp's window — which is precisely what the fade was built to
do.

**How much weakening one of them tolerates.** Sweeping only `castlingFactor` against
`BlunderTest`'s h3 case, where `12.h3` loses by force:

| factor | 0.25 | 0.21875 | 0.1875 | 0.15625 | 0.125 | 0.0625 | 0 |
|---|---|---|---|---|---|---|---|
| verdict | avoids | avoids | **avoids** | **plays it** | plays it | plays it | plays it |
| its score for the losing move | — | — | — | 0.99 | 1.12 | 1.34 | 1.56 |

The threshold sits between 0.1875 and 0.15625, and below it the engine does not merely flip
once — the score it assigns the losing move rises monotonically as the term weakens. A
monotone dose-response over six points is much stronger evidence than a single flip: this is
a causal effect of the term's strength, not search tie-breaking. One position, so the
threshold is that position's and not the engine's; what generalizes is that **the term is
doing measurable king-safety work at 0.25**, which § 5.5.1's "too strong" hypothesis did not
anticipate.

`castlingFactor = 0.1875` with no ramp leaves the whole characterization suite clean apart
from `testPosition12`'s stale expectation. It has no Elo measurement.

#### What this campaign says for the next one

- **A behavioral rate measured against a weaker opponent is not the build's rate.** Any future
  term scoring a difference has the same exposure. Measure level, or state the pairing.
- **Put the known endpoints in the sweep.** They cost two points and they are the only thing
  that can tell you the scale moved under you.
- **Decide what the measurement must separate before splitting a change into arms.** Two arms
  three thousand games each answered less than one arm of six thousand would have, because the
  quantity between them was smaller than either interval.
- § 5.5.1's open question is answered, and its premise was wrong: the term never lifted the rate
  by 30 points. It lifts it by about 4, which is why it buys single-digit Elo.

## 5.6 — removed

`5.6` was *Opening state*, deleted on 2026-09-09 together with the term it described: neither
`openingState` nor `calculateOpeningState()` exists in the production code or the tests, and the
note under § 5.9's listing had recorded the term's removal for a week while this section still
explained it. See the correction note under the overview formula at the top.

**The number is deliberately not reused.** Renumbering §§ 5.7–5.9 would break the anchors that
`data-types.md`, `move-generation.md` and `tapered-evaluation.md` link to, and two internal
references in this file, for no gain.

**Two components of the formula still have no section of their own** — `undefendedPieces` (−0.1
per hanging piece) and `bishopPair` (+0.4, the largest single eval gain of the 4.3.x series at
+31.3 Elo). They are the natural candidates for this slot when someone writes them up.

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

All nine components (material plus the eight positional terms) combine in
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
            + ((bishopCount[0] >= 2 ? 1 : 0) - (bishopCount[1] >= 2 ? 1 : 0)) * bishopPairFactor)
            * 100);
}
```

> **This listing was three terms out of date until 2026-09-02** — it omitted the undefended-pieces
> penalty and the bishop-pair bonus and still showed an `openingState` term and a `plyCount`-based
> opening correction that the class no longer has. A stale copy of production code in a document is
> worse than no copy, because it reads as authoritative. Re-paste it from
> `WeightingFunction.calculatePositionWeight()` whenever a term is added — or removed: a ninth
> line for a king-line danger term stood here while that term was on the mainline, and had to come
> back out when it was shelved. Its documentation lives in
> [king-safety.md § 4.11–4.12](king-safety.md), where an unshipped attempt belongs.

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
