# 4. Move Generation

## 4.1 Generator structure

[`MoveGenerator`](src/main/java/org/michaelfl/mychess/MoveGenerator.java) produces all moves the side-to-move can play in a given position. It is constructed once per engine, paired with a [`MoveSorter`](src/main/java/org/michaelfl/mychess/MoveSorter.java) instance, and reused across every node in the search.

```java
public MoveGenerator(MoveSorter moveSorter);

public Moves calculateMoves(Board theBoard);
public Moves calculateMoves(Board theBoard, int depth);
public Moves calculateMoves(Board theBoard, int depth, int knownBestMove);
public Moves calculateMoves(GameStatus game, Board theBoard, int depth, int knownBestMove);
```

All four entry points funnel into the fourth one, which is the only one that contains real work. `depth` is forwarded to the sorter (killer-move tables are per-depth), and `knownBestMove` is the principal-variation move from the previous iteration of iterative deepening — it gets placed first in the output ordering. Both default to `0` ("none").

**Dispatch:** generation is driven by a function-pointer table indexed by piece value:

```java
private final static CalculateMoves[] calculationFunctions = new CalculateMoves[22];
static {
    calculationFunctions[Board.whitePawn]   = MoveGenerator::_calculateWhitePawnMoves;
    calculationFunctions[Board.whiteKnight] = MoveGenerator::_calculateKnightMoves;
    calculationFunctions[Board.whiteBishop] = MoveGenerator::_calculateBishopMoves;
    calculationFunctions[Board.whiteRook]   = MoveGenerator::_calculateRookMoves;
    calculationFunctions[Board.whiteQueen]  = MoveGenerator::_calculateQueenMoves;
    calculationFunctions[Board.whiteKing]   = MoveGenerator::_calculateKingMoves;
    // …same six for black
}
```

**Main loop:** scan the playing area, dispatch on each own piece:

```java
final int stopField = 9 * Board.LENGTH + 10;   // = 118, one past h8

for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
    final byte piece = board[field];
    if ((piece & turn) == turn)                                // "my piece"
        calculationFunctions[piece].calculateMoves(this, field);
}
```

Two performance points:

- The loop bounds skip the bordered rows (rows 0, 1, 10, 11) but **not** the bordered columns within rows 2–9. The two illegal columns in each scanned row are handled by `(piece & turn) == turn` returning `false` for `illegal = 64`.
- The lambda call through `CalculateMoves[]` is one level of indirection per piece, but each per-piece generator then inlines all of its target-square logic — see [§ 4.2](#42-per-piece-movement). The dispatch cost is amortized over many generated moves per piece.

**Mutable per-call state** (reset on every entry to `calculateMoves`):

```java
private GameStatus game;             // current rules state
private byte[]    board;             // theBoard.getRawBoard() — direct array reference
private int       oppositeColor;     // TURN_WHITE or TURN_BLACK
private int       oppositeKing;      // blackKing or whiteKing — for king-capture detection
private boolean   containsIllegalMove;
```

After the loop:

```java
if (containsIllegalMove)
    return Moves.ILLEGAL;

return moveSorter.getSortedMoves();
```

`Moves.ILLEGAL` is a sentinel (see [§ 3.6](data-types.md#36-moves-movesarray-intarray) and [§ 4.5](#45-pseudo-legal-moves-and-king-capture-detection)) meaning "*the previous move put the side-to-move-now in a position where the opponent's king is capturable — so the previous move was illegal*". This is the engine's mechanism for filtering moves that leave one's own king in check, without doing an explicit per-move legality test.

## 4.2 Per-piece movement

Each piece type has its own generator method on `MoveGenerator`. They all share two helpers, `move(piece, from, to)` for sliders / leapers and a per-pawn variant. The `move(...)` helper encodes one *target* square:

```java
private boolean move(final byte piece, final int from, final int to) {
    final byte capturedPiece = board[to];
    if (capturedPiece == 0 || (capturedPiece & oppositeColor) == oppositeColor) {
        addMove(from, to, piece, capturedPiece, Move.typeNormal);
        if (capturedPiece == oppositeKing)
            containsIllegalMove = true;
    }
    return capturedPiece == 0;   // true → keep sliding, false → blocked
}
```

The return value drives sliding-piece loops: keep going as long as the target square was empty; stop on any piece (own or opponent). Off-board sentinels (`board[to] == Board.illegal = 64`) fail both branches — `capturedPiece != 0` and `(64 & oppositeColor) != oppositeColor` — so the move is silently dropped and `false` is returned, terminating the slide.

**Knights** — eight fixed offsets:

```java
move(piece, field, field + 2 * Board.LENGTH + 1);   // up 2, right 1
move(piece, field, field + 1 * Board.LENGTH + 2);   // up 1, right 2
move(piece, field, field - 1 * Board.LENGTH + 2);   // down 1, right 2
move(piece, field, field - 2 * Board.LENGTH + 1);   // down 2, right 1
move(piece, field, field - 2 * Board.LENGTH - 1);
move(piece, field, field - 1 * Board.LENGTH - 2);
move(piece, field, field + 1 * Board.LENGTH - 2);
move(piece, field, field + 2 * Board.LENGTH - 1);
```

`LENGTH = 12` is the row stride. A knight on b1 (mailbox index 27) attempts `27 + 24 + 1 = 52` (c3, valid), `27 - 24 + 1 = 4` (off-board, hits `illegal`), and six others — each handled by `move(...)` without explicit bounds checks.

**Bishops, rooks, queens** — sliding loops. The bishop:

```java
// move up-right
for (int to = field + Board.LENGTH + 1; move(piece, field, to); to += Board.LENGTH + 1);
// move down-right
for (int to = field - Board.LENGTH + 1; move(piece, field, to); to = to - Board.LENGTH + 1);
// move down-left
for (int to = field - Board.LENGTH - 1; move(piece, field, to); to = to - Board.LENGTH - 1);
// move up-left
for (int to = field + Board.LENGTH - 1; move(piece, field, to); to += Board.LENGTH - 1);
```

The increment expressions hard-code the four diagonal strides (`±LENGTH ±1`). The loop body is empty (`for (…; …; …);`) — all work is in the condition. Rook and queen are structurally identical: four / eight directions, all unrolled into separate `for` lines.

**Kings** — eight one-square offsets (same set as queen but no loop) plus castling delegation:

```java
move(piece, field, field + Board.LENGTH);          // up
move(piece, field, field + Board.LENGTH + 1);      // up-right
…
move(piece, field, field + Board.LENGTH - 1);      // up-left

if (game.isCastlingPossible())
    calculateCastlingMoves();
```

**Pawns** are special-cased because they move and capture differently and have promotion + en-passant variants. The white-pawn case (black is mirror-symmetric):

```java
private void calculateWhitePawnMoves(int field) {
    // single step
    int to = field + Board.LENGTH;
    if (board[to] == 0)
        addWhitePawnMove(field, to);

    // double step — only from rank 2 (row index 1)
    if (fieldToRow(field) == 1) {
        to = field + 2 * Board.LENGTH;
        if (board[to] == 0 && board[field + Board.LENGTH] == 0)
            addWhitePawnMove(field, to);
    }

    // capture right
    to = field + Board.LENGTH + 1;
    if ((board[to] & oppositeColor) == oppositeColor) {
        if (board[to] == oppositeKing)
            containsIllegalMove = true;
        addWhitePawnMove(field, to);
    }

    // capture left   (symmetric: +LENGTH - 1)

    // en passant
    final byte enPassantField = game.getEnPassantField();
    if (enPassantField != 0
        && (enPassantField == field + Board.LENGTH - 1
         || enPassantField == field + Board.LENGTH + 1)) {
        addWhiteEnPassantMove(field, enPassantField);
    }
}
```

`addWhitePawnMove(from, to)` adds **either** one normal pawn move **or** two promotion moves (queen and knight) when the target rank is the back rank:

```java
private void addWhitePawnMove(int from, int to) {
    if (to >= Board.a8) {
        // Pawn promotion — queen and knight only (see § 3.4)
        addMove(from, to, Board.whitePawn, board[to], Move.typePawnPromotionQueen);
        addMove(from, to, Board.whitePawn, board[to], Move.typePawnPromotionKnight);
    } else {
        addMove(from, to, Board.whitePawn, board[to], Move.typeNormal);
    }
}
```

Rook and bishop promotions are deliberately omitted from generation because they are strictly dominated by queen promotion in any normal evaluation. The move-type constants for them still exist so that user-supplied PGN moves like `e8=R` can be validated and replayed (see [§ 3.4](data-types.md#34-move-types)).

## 4.3 Castling legality

Castling is the most rules-heavy part of move generation because it has four separate legality conditions, three of which are not visible just from the king square. The generator handles them in stages:

**Stage 1 — Are the rights still there?** The two "castling possible" bits on `GameStatus` (see [§ 3.5](data-types.md#35-gamestatus-and-the-status-stack)) are cleared by `Board.calculateNewCastlingState` whenever the king moves, the relevant rook moves, the king is captured, or the rook is captured. Once cleared they cannot come back. The generator simply tests:

```java
if (game.isWhiteCastlingKingSidePossible() && canDoWhiteCastlingKingSide()) { … }
```

**Stage 2 — Are the squares between king and rook empty?**

```java
private boolean canDoWhiteCastlingKingSide() {
    if (board[Board.f1] != Board.empty || board[Board.g1] != Board.empty)
        return false;
    …
}
```

For queen-side, three squares must be empty (b1, c1, d1).

**Stage 3 — Are none of the king's start / crossed / target squares under attack?**

```java
return !(isWhiteCastlingFieldUnderAttack(Board.e1)
      || isWhiteCastlingFieldUnderAttack(Board.f1)
      || isWhiteCastlingFieldUnderAttack(Board.g1));
```

This is the only place in the codebase where the engine does an **explicit attack test** rather than relying on the pseudo-legal king-capture trick (see [§ 4.5](#45-pseudo-legal-moves-and-king-capture-detection)). The reason: the king-capture trick only catches "is the king *currently* attacked"; castling needs "would the king be attacked on *any* of three squares" — including the start square the king is leaving. A full pseudo-legal generator pass for the opponent would have to be run three times, which is wasteful.

Instead, `isWhiteCastlingFieldUnderAttack(field)` ray-casts from the square outward and checks for the right attacker type per direction:

```java
// check left (rook / queen attack along the rank)
int f = field - 1;
if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
    for (; board[f] == Board.empty; f--) ;
    byte piece = board[f];
    if (piece == Board.blackQueen || piece == Board.blackRook)
        return true;
}

// check up   — rook / queen on file
// check right — rook / queen on rank
// check up-left, up-right — bishop / queen on diagonal
// check knight squares — four offsets where an opposing knight could be
// check pawn squares    — two diagonals one rank up (black pawns attack from above)
// check king squares    — three adjacent squares (only the three "up" ones for white,
//                         because the king cannot attack from below — that area is
//                         the wrong side of the board)
```

The first `if` on each ray (`(board[f] & TURN_WHITE) != TURN_WHITE`) short-circuits the ray as soon as it hits an own piece — own pieces block enemy line attacks and cannot attack the square themselves.

The black version (`isBlackCastlingFieldUnderAttack`) is the mirror image: directions are flipped (down instead of up for the king/pawn region), and the attacker pieces are the white set. The two methods are kept as duplicated code (with the `@SuppressWarnings("Duplicates")` marker at the class level) rather than parameterized, because every conditional is tight and color-symmetric in a way that a parameterized version would slow down measurably.

## 4.4 En passant

En passant has two halves:

**(a) Establishing the right** happens inside `Board.makeMove(int)` after the pawn has moved. `Board.getEnPassantField(movedPiece, fromField, toField)` returns the *skipped* square iff the moved piece is a pawn and the move was a double step:

```java
static byte getEnPassantField(byte movedPiece, byte fromField, byte toField) {
    if (movedPiece == Board.whitePawn && toField == fromField + 2 * Board.LENGTH) {
        return (byte) (fromField + Board.LENGTH);
    }
    if (movedPiece == Board.blackPawn && toField == fromField - 2 * Board.LENGTH) {
        return (byte) (fromField - Board.LENGTH);
    }
    return 0;
}
```

The returned mailbox index is stored on the new `GameStatus.enPassantField`. The Zobrist hash is updated by XOR-ing in the en-passant file:

```java
newPositionHash ^= RANDOM_NUMBERS[EN_PASSANT_INDEX + enPassantField % Board.LENGTH - 2];
```

The right exists for *exactly one ply*. The next `makeMove` produces a new `GameStatus` where `enPassantField` defaults back to `0` unless that move was itself another double-step.

**(b) Using the right** happens inside `MoveGenerator.calculateWhitePawnMoves` and the black mirror. When the generator is enumerating a pawn's moves, it checks whether `game.getEnPassantField()` is exactly one of the two diagonal capture squares for *this* pawn:

```java
final byte enPassantField = game.getEnPassantField();
if (enPassantField != 0
    && (enPassantField == field + Board.LENGTH - 1
     || enPassantField == field + Board.LENGTH + 1)) {
    addWhiteEnPassantMove(field, enPassantField);
}
```

If so, an `Move.typeEnPassant` move is emitted. The captured-piece slot is set to the opponent pawn so that undo works:

```java
private void addWhiteEnPassantMove(int from, int to) {
    addMove(from, to, Board.whitePawn, Board.blackPawn, Move.typeEnPassant);
}
```

**Make / unmake for `typeEnPassant`** is the only normal-looking move that mutates *three* squares rather than two. `Board._makeEnPassantMove`:

```java
board[toField]   = board[fromField];      // place capturing pawn on the en-passant square
board[fromField] = empty;                 // clear source
if (toField > fromField) {                // white captured upward
    board[toField - Board.LENGTH] = empty;  // remove black pawn one rank below target
} else {                                  // black captured downward
    board[toField + Board.LENGTH] = empty;  // remove white pawn one rank above target
}
```

Undo restores the captured pawn one rank behind the target. The Zobrist incremental update removes both pawn hashes (capturing and captured) and adds back the capturing pawn at its new square.

**Evaluation also considers en passant.** Inside `WeightingFunction.calculateForWhitePawn` / `calculateForBlackPawn`, en-passant captures count toward mobility and threat weight (see [§ 5.3](evaluation.md#53-mobility), [§ 5.4](evaluation.md#54-threat-weight)) by inspecting `game.getLastMove()` rather than `enPassantField` — this is independent bookkeeping inside the evaluation function, redundant with the generator but kept separate so evaluation works on positions reached without a prior `makeMove` (e.g. from FEN import).

## 4.5 Pseudo-legal moves and king-capture detection

myChess generates **pseudo-legal** moves and detects illegal ones via the *king-capture trick*. This is the single biggest design choice in `MoveGenerator`, so it deserves a section of its own.

**The naive alternative:** generate all moves the side-to-move could play, then for each move make it on a board copy, run the *opponent's* move generator, and check whether any opponent reply captures the now-side-to-move's king. Discard any move where that happens. Cost: one full move-generation pass per generated move, just to filter.

**myChess's approach:** generate all moves *without* checking for self-check. Inside the generator, set a flag whenever a move would capture the opponent's king:

```java
private boolean move(final byte piece, final int from, final int to) {
    final byte capturedPiece = board[to];
    if (capturedPiece == 0 || (capturedPiece & oppositeColor) == oppositeColor) {
        addMove(from, to, piece, capturedPiece, Move.typeNormal);
        if (capturedPiece == oppositeKing)
            containsIllegalMove = true;
    }
    return capturedPiece == 0;
}
```

At the end of generation, if `containsIllegalMove` is true, return the singleton `Moves.ILLEGAL`. Otherwise return the normal `Moves`.

**How is this used to detect self-check?** The search makes a candidate move on the working board, then calls `moveGenerator.calculateMoves(...)` to enumerate the opponent's responses. If the result is `Moves.ILLEGAL`, that means the opponent has a king-capture move available — which means the *previous* move (the candidate) left the side that just moved in check. The search treats this as an invalid candidate:

```java
ctx.workingBoard.makeMove(move);
var result = alphaBetaSearch(...).negate();
ctx.workingBoard.revertMove();
…
// -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
if (result.weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
    /* candidate was legal — record its result */
}
```

The `WeightingFunction.ILLEGAL_WEIGHT_POS` constant is returned for `Moves.ILLEGAL`, and after `negate()` it becomes `ILLEGAL_WEIGHT_NEG`. The convention is *"negative ILLEGAL is possible (the move we just tried turned out illegal), positive ILLEGAL is impossible (would mean we ourselves captured the opponent king, which is a search bug)"* — the search asserts that positive-ILLEGAL never bubbles up:

```java
__assert(() -> !(result.weight <= WeightingFunction.ILLEGAL_WEIGHT_NEG
              || result.weight >  WeightingFunction.ILLEGAL_WEIGHT_POS),
         () -> "Unexpected weight " + …);
```

**Trade-offs:**

| | Pseudo-legal + king-capture trick | Strict legal generation |
|---|---|---|
| Cost in generator | Zero — single integer compare per move | One legality test per move |
| Cost in search | Search effectively does one extra ply for every position to detect check | Search has no overhead |
| Effective depth lost | ≈ 1 ply (the "filter ply") | 0 |
| Implementation complexity | Low — one flag, one sentinel `Moves.ILLEGAL` | High — full attack-detection for every move |

The trick wins because pseudo-legal generation is enormously cheaper, and alpha-beta pruning makes the extra ply mostly free at the leaves (illegal candidates get the worst possible score and are pruned immediately). The one place where the trick *doesn't* apply is **castling**, where the king must be safe on three specific squares including its current one — there the explicit attack test from [§ 4.3](#43-castling-legality) is unavoidable.

## 4.6 Move-ordering hook

The generator does not assemble its output into a sorted list itself. Every move it produces is routed through:

```java
private void addMove(final int fromField, final int toField,
                     final byte movingPiece, final byte capturedPiece,
                     final byte moveType) {
    int move = Move.create((byte) fromField, (byte) toField, capturedPiece, moveType);
    moveSorter.addMove(move, fromField, toField, movingPiece, capturedPiece);
}
```

…and at the end of `calculateMoves`, the sorter is asked for the final ordered output:

```java
return moveSorter.getSortedMoves();
```

[`MoveSorter`](src/main/java/org/michaelfl/mychess/MoveSorter.java) is a three-method interface:

```java
public interface MoveSorter {
    void  reset(GameStatus gameStatus, Board board, int depth, int knownBestMove);
    void  addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece);
    Moves getSortedMoves();
}
```

This is a **strategy hook**, not just a sort utility. The sorter decides the ordering policy and may need information the move alone does not carry — the moving piece (for MVV-LVA-style weighting), the source and target squares (for piece-square-table delta), the search depth (for killer-move table lookup), and the principal-variation move from the previous iteration. All of those come in through `addMove` and `reset`.

Two implementations exist:

- **[`MoveSorterImpl`](src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java)** — the engine sorter. Six buckets (PV move → recapture of last-played piece → winning captures → killer moves → other captures → quiet moves → king moves), sorted internally where it matters. Full details in [§ 7.8](search.md#78-move-sorting-sortablemovesbucket).
- **`MoveSorter.defaultImplementation()`** — returns a `new MoveSorterImpl()` constructed with a *new* `KillerMoves` table. Used in stand-alone contexts where the search history is irrelevant: tests, `Board.resolveMoveDescription`, `Game.makeMove`, `Pgn` parsing. The ordering still works; killer-move boosting just never fires because the table is fresh.

The separation gives the search room to vary its ordering strategy (or experiment with one) without ever touching `MoveGenerator`. Conversely, `MoveGenerator` can be reused by any non-search caller (notation resolution, perft counting, opening-book ingestion) with a trivially-constructed sorter that produces the same legal move set.
