# 3. Core Data Types

## 3.1 Board representation (12×12 mailbox)

The chessboard is stored as a flat `byte[144]` — a **12×12 mailbox** with a 2-square illegal border on every side, wrapping the actual 8×8 playing area. Each cell holds a piece code (see [§ 3.2](#32-piece-encoding)) or one of the sentinel values `Board.empty = 0` / `Board.illegal = 64`.

Index layout:

```
   col→  0   1   2   3   4   5   6   7   8   9  10  11
row↓    ┌──────────────────────────────────────────────┐
  11  →│ 132 133 134 135 136 137 138 139 140 141 142 143 │  illegal border
  10  →│ 120 121 122 123 124 125 126 127 128 129 130 131 │  illegal border
   9  →│ 108 109 110 111 112 113 114 115 116 117 118 119 │  •••••• 8th rank
   8  →│  96  97  98  99 100 101 102 103 104 105 106 107 │  •••••• 7th rank
   7  →│  84  85  86  87  88  89  90  91  92  93  94  95 │  •••••• 6th rank
   6  →│  72  73  74  75  76  77  78  79  80  81  82  83 │  •••••• 5th rank
   5  →│  60  61  62  63  64  65  66  67  68  69  70  71 │  •••••• 4th rank
   4  →│  48  49  50  51  52  53  54  55  56  57  58  59 │  •••••• 3rd rank
   3  →│  36  37  38  39  40  41  42  43  44  45  46  47 │  •••••• 2nd rank
   2  →│  24  25  26  27  28  29  30  31  32  33  34  35 │  •••••• 1st rank (a1..h8 below)
   1  →│  12  13  14  15  16  17  18  19  20  21  22  23 │  illegal border
   0  →│   0   1   2   3   4   5   6   7   8   9  10  11 │  illegal border
        └──────────────────────────────────────────────┘
              └─ cols 0,1 illegal      cols 10,11 illegal ─┘
```

The playing area runs from index `a1 = 26` (row 2, col 2) to `h8 = 117` (row 9, col 9). All 64 named-square constants `a1, b1, … h8` are precomputed in `Board.java`:

```java
public final static int LENGTH = 12;
public final static int a1 = 2 * LENGTH + 2 + 0;   // = 26
public final static int b1 = 2 * LENGTH + 2 + 1;   // = 27
…
public final static int h8 = 9 * LENGTH + 2 + 7;   // = 117
```

**Why a 12×12 mailbox rather than 8×8 or bitboards?**

The border is the whole point. Move generation reaches off-board squares constantly — a knight on b1 generates eight target indices, six of which fall outside the 8×8 grid. With a bare 8×8 array the generator would need explicit bounds checks (`if (col >= 0 && col < 8 && row >= 0 && row < 8)`) at every step. With a 2-square illegal border, the off-board check collapses to a single read:

```java
if (board[to] != Board.illegal) { … }
```

The 2-square border (rather than 1-square) is needed because a knight can jump 2 squares away. A bishop or rook may slide off in any direction; the sliding loops just terminate as soon as they read `illegal`. This is the classic *mailbox board* technique, and it eliminates conditional branches from every inner generator loop.

**Indexing helpers** ([`ChessUtil`](src/main/java/org/michaelfl/mychess/ChessUtil.java)):

```java
getFieldFromColAndRow(col, row)  →  (row + 2) * 12 + col + 2
colAndRowToField(col, row)       →  same
getRowOfField(field)             →  field / 12 - 2
getColOfField(field)             →  field % 12 - 2
getFieldNumber64(field)          →  row*8 + col   (for Zobrist & external indexing)
```

The dual indexing — 144-cell mailbox internally, 64-cell linear externally — is also visible in the Zobrist table layout (see [§ 3.8](#38-zobrist-hashing-and-positionencoding)).

**`Board` instance state:**

```java
private final byte[] board;             // length 144
private final GameStatus[] statusStack; // length 2000 (fixed, push/pop with stackSize)
private int stackSize;
```

The board mutates in place. There is exactly one `Board` per `Game`, and the search clones it once at the root via `Board.copy()`. The 2000-deep status stack is generous: even a 50-move-rule-limited game can produce at most ~6000 plies in theory, but real games stay well below 2000.

## 3.2 Piece encoding

Pieces are encoded as single bytes designed so that **color is one bit and kind is the low 3 bits**:

| Piece | Value | Binary |
|---|---|---|
| empty | 0 | `0000 0000` |
| whitePawn | 8 | `0000 1000` |
| whiteKnight | 9 | `0000 1001` |
| whiteBishop | 10 | `0000 1010` |
| whiteRook | 11 | `0000 1011` |
| whiteQueen | 12 | `0000 1100` |
| whiteKing | 13 | `0000 1101` |
| blackPawn | 16 | `0001 0000` |
| blackKnight | 17 | `0001 0001` |
| blackBishop | 18 | `0001 0010` |
| blackRook | 19 | `0001 0011` |
| blackQueen | 20 | `0001 0100` |
| blackKing | 21 | `0001 0101` |
| illegal | 64 | `0100 0000` |

Two consequences fall out of this layout, and the move generator exploits both:

1. **`piece & 7`** gives a 0–5 piece kind (pawn/knight/bishop/rook/queen/king) ignoring color — though myChess generally does not use this, preferring to dispatch on the full piece value via lookup tables (`MOVE_FUNCTIONS[piece]`, `calculationFunctions[piece]`).

2. **`piece & turn`** is the own/opposite-color test, because the turn constants in `GameStatus` align with the color bits in the piece encoding:

   ```java
   public final static int TURN_WHITE = 8;    // = whitePawn bit
   public final static int TURN_BLACK = 16;   // = blackPawn bit
   ```

   So inside `MoveGenerator`:

   ```java
   for (int field = …; field < stopField; field++) {
       final byte piece = board[field];
       if ((piece & turn) == turn)              // "my piece"
           calculationFunctions[piece].calculateMoves(this, field);
   }
   ```

   And inside the move helpers:

   ```java
   if ((capturedPiece & oppositeColor) == oppositeColor) { … }   // capture
   ```

   No color-table lookups, no branches per square — just a bitmask.

The `illegal = 64` sentinel uses a high bit that's disjoint from all real pieces, so `(illegal & turn)` is 0 for both colors and the bordered squares simply never match "my piece" or "opposite color" tests.

**Printing tables** in `Board`:

- `printSymbols` — 22-entry array of Unicode chess glyphs (`♚`, `♟`, …) for the REPL board display.
- `fenSymbols` — 22-entry array of FEN letters (`K`, `p`, …) used by [`Fen`](src/main/java/org/michaelfl/mychess/Fen.java) for export.

## 3.3 Move encoding (packed int)

A move is encoded as a single `int` (32 bits), packed by [`BitOps`](src/main/java/org/michaelfl/mychess/BitOps.java) into four bytes:

```
  31           24 23           16 15            8 7             0
  ┌──────────────┬───────────────┬───────────────┬───────────────┐
  │  moveType    │ capturedPiece │    toField    │   fromField   │
  └──────────────┴───────────────┴───────────────┴───────────────┘
  (byte 3)       (byte 2)        (byte 1)        (byte 0)
```

| Field | Range | Meaning |
|---|---|---|
| `fromField` | 0–143 | source index in the 12×12 mailbox |
| `toField` | 0–143 | target index in the 12×12 mailbox |
| `capturedPiece` | 0 / 8–21 | piece on `toField` *before* the move (0 = none) |
| `moveType` | 0–7 | see [§ 3.4](#34-move-types) |

**Packing/unpacking** is done by `BitOps.createWord` and the four `getByteN` accessors:

```java
public static int createWord(byte b0, byte b1, byte b2, byte b3) {
    return ((b0 & 0xFF)      ) +
            ((b1 & 0xFF) <<  8) +
            ((b2 & 0xFF) << 16) +
            ((b3       ) << 24);
}

public static byte getByte0(int word) { return (byte) word; }
public static byte getByte1(int word) { return (byte) (word >>> 8); }
public static byte getByte2(int word) { return (byte) (word >>> 16); }
public static byte getByte3(int word) { return (byte) (word >>> 24); }
```

[`Move`](src/main/java/org/michaelfl/mychess/Move.java) exposes both **static** accessors that take the raw `int` (used everywhere on hot paths) and **instance** accessors that wrap an `int` (used at boundaries for printing and equality):

```java
public static byte getFromField(int move)     { return BitOps.getByte0(move); }
public static byte getToField(int move)       { return BitOps.getByte1(move); }
public static byte getCapturedPiece(int move) { return BitOps.getByte2(move); }
public static byte getMoveType(int move)      { return BitOps.getByte3(move); }
```

**Why `int` rather than a `record` or class?**

The search visits hundreds of thousands of nodes per second. A `record Move(byte from, byte to, byte captured, byte type)` would allocate one object per generated move, generating heavy GC pressure. The 32-bit `int` packing means an entire generated move list is just an `int[]`, allocated once per `Moves` and reused across depths.

**Why store `capturedPiece` in the move itself, rather than re-reading it from the board on undo?**

Because `revertMove(int move)` must restore the captured piece *after* the move has already overwritten that square. Storing it in the move means undo is a constant-time, no-lookup operation:

```java
private void _revertNormalMove(int move) {
    final byte fromField = Move.getFromField(move);
    final byte toField = Move.getToField(move);
    board[fromField] = board[toField];
    board[toField] = Move.getCapturedPiece(move);
}
```

This is what makes the make/undo idiom in the search affordable — see [§ 7.4](search.md#74-make--undo-on-a-single-board).

## 3.4 Move types

The eight move types defined by [`Move`](src/main/java/org/michaelfl/mychess/Move.java) cover the cases where a "move" is more than just *move piece from A to B, optionally capturing*:

```java
public final static byte typeNormal              = 0;
public final static byte typeCastlingKingSide    = 1;
public final static byte typeCastlingQueenSide   = 2;
public final static byte typePawnPromotionQueen  = 3;
public final static byte typePawnPromotionRook   = 4;
public final static byte typePawnPromotionKnight = 5;
public final static byte typePawnPromotionBishop = 6;
public final static byte typeEnPassant           = 7;
```

`Board.makeMove(int move)` does **not** branch on the type itself — it dispatches through two parallel function-pointer tables:

```java
private final static IMove[] MOVE_FUNCTIONS = new IMove[Move.typeEnPassant + 1];
static {
    MOVE_FUNCTIONS[Move.typeNormal]              = Board::makeNormalMove;
    MOVE_FUNCTIONS[Move.typeCastlingKingSide]    = Board::makeCastlingKingSideMove;
    MOVE_FUNCTIONS[Move.typeCastlingQueenSide]   = Board::makeCastlingQueenSideMove;
    MOVE_FUNCTIONS[Move.typePawnPromotionQueen]  = Board::makePawnPromotionMoveQueen;
    MOVE_FUNCTIONS[Move.typePawnPromotionKnight] = Board::makePawnPromotionMoveKnight;
    MOVE_FUNCTIONS[Move.typePawnPromotionRook]   = Board::makePawnPromotionMoveRook;
    MOVE_FUNCTIONS[Move.typePawnPromotionBishop] = Board::makePawnPromotionMoveBishop;
    MOVE_FUNCTIONS[Move.typeEnPassant]           = Board::makeEnPassantMove;
}

private final static IRevertMove[] MOVE_REVERT_FUNCTIONS = new IRevertMove[Move.typeEnPassant + 1];
// (analogous; all four promotion types share revertPawnPromotionMove)
```

`makeMove(int move)` then becomes a single indirect call: `MOVE_FUNCTIONS[Move.getMoveType(move)].move(this, move)`. The JIT inlines aggressively when the type is constant in context (which it usually isn't in the search loop, but the table dispatch is still cheaper than a 7-way `switch`).

**Per-type semantics:**

| Type | Special handling |
|---|---|
| **Normal** | Move piece from `from` to `to`; clear `from`; place piece on `to` (overwriting any captured piece). |
| **CastlingKingSide** | Move king e1↔g1 (or e8↔g8) *and* rook h1↔f1 (or h8↔f8). Update Zobrist hash for both pieces. No capture. Legality (squares not under attack) is enforced by `MoveGenerator`, not by `Board`. |
| **CastlingQueenSide** | Symmetric: king e→c, rook a→d. |
| **PawnPromotion{Queen,Rook,Knight,Bishop}** | Move pawn to last rank, *replace* it with the chosen piece type. `capturedPiece` is still valid (promotion-with-capture). |
| **EnPassant** | Pawn captures diagonally to an empty square; the actually-captured pawn sits one rank behind. The captured-piece slot in the move still holds the opponent pawn for symmetric undo. |

Note that the move generator emits *two* promotion candidates per promotion ply — queen and knight ([`MoveGenerator.addWhitePawnMove`](src/main/java/org/michaelfl/mychess/MoveGenerator.java)). Rook and bishop promotions are not generated because they are strictly dominated by queen promotion. They remain valid move types because user input via `MoveDescription` (e.g. PGN with `=R` for "promote to rook") may still construct them.

## 3.5 `GameStatus` and the status stack

[`GameStatus`](src/main/java/org/michaelfl/mychess/GameStatus.java) is an immutable snapshot of all rules-level state *outside* the piece positions:

```java
private final int  plyCount;         // 0 at start, +1 per move
private final int  turn;             // TURN_WHITE = 8 or TURN_BLACK = 16
private final int  lastMove;         // packed int, 0 only at start
private final int  halfMoveClock;    // for 50-move rule, reset on pawn move or capture
private final int  castlingState;    // 6-bit bitfield (see below)
private final long positionHash;     // incremental Zobrist hash
private final byte enPassantField;   // 0 = none, else mailbox index of the skipped square
```

**Why immutable?** Because `Board` keeps a stack of `GameStatus` snapshots, one per ply. `Board.makeMove` pushes a new instance; `Board.revertMove` pops the top of the stack. No mutation, no copy-on-write, no allocation tracking — just `statusStack[stackSize++] = newStatus` and `statusStack[--stackSize] = null`. The stack is preallocated `new GameStatus[2000]`.

**Castling state bitfield:**

```
bit 0 (1)  BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE
bit 1 (2)  BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE
bit 2 (4)  BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE
bit 3 (8)  BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE
bit 4 (16) BIT_WHITE_HAS_CASTLED
bit 5 (32) BIT_BLACK_HAS_CASTLED
```

Initial value is `INITIAL_CASTLING_STATE = 15` — all four rights granted, neither side has castled yet. The "has-castled" bits are used only by the evaluation function (see [§ 5.5](evaluation.md#55-castling-state)); the four "still-possible" bits drive the move generator. The "still-possible" bits are *cleared* by `Board.calculateNewCastlingState` whenever the king or relevant rook moves or is captured, and once cleared they cannot come back.

**Turn constants share bits with the piece encoding** (see [§ 3.2](#32-piece-encoding)), so `(piece & turn) == turn` tests "is this my piece" without a color-table lookup.

**`switchTurn()`** returns a *new* `GameStatus` with the colors flipped and `enPassantField` cleared. It is used by `Board.isKingChecked` to ask "could the opponent capture my king *now*?" — see [§ 4.5](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection).

**Initial position hash** is a precomputed long literal:

```java
private final static long INITIAL_POSITION_HASH = -8376097377325274526L;
```

This avoids recomputing the start-position Zobrist sum at every `new Game()`. The exact value depends on the contents of `RandomNumbers.RANDOM_NUMBERS`.

## 3.6 `Moves`, `MovesArray`, `IntArray`

Three closely related types implement *"a manually managed `int[]` with append, pop, and shuffle"*:

**[`IntArray`](src/main/java/org/michaelfl/mychess/IntArray.java)** — the primitive:

```java
public class IntArray {
    public final static int INITIAL_CAPACITY  = 30;
    private final static int CAPACITY_INCREMENT = 10;

    int[] array;   // package-private — direct access from Moves
    int   size;

    public final void add(int element) {
        if (size == array.length)
            array = Arrays.copyOf(array, size + CAPACITY_INCREMENT);
        array[size++] = element;
    }

    public final int pop()              { return array[--size]; }
    public final int[] getArray()       { return array; }
    public final int size()             { return size; }
    public final void clear()           { size = 0; }
    public final boolean contains(int)  { /* linear scan */ }
    public final void mayShuffle(Random) { /* Fisher–Yates, only if size >= 4 */ }
}
```

This is `ArrayList<Integer>` without autoboxing — the entire reason it exists. Initial capacity 30 is chosen to accommodate a typical chess position (~30–40 legal moves) without reallocation; the linear growth (`+10` per overflow) is fine because move-list overflow is rare in practice.

The `array` and `size` fields are *package-private* so `Moves` and the search can read them directly:

```java
final int getMove(int moveIndex) {
    return moves.array[moveIndex];   // no bounds check, no method call
}
```

**[`MovesArray`](src/main/java/org/michaelfl/mychess/MovesArray.java)** — `IntArray` subclass that overrides `toString()` to render moves as algebraic notation rather than raw integers. Used by `SortableMovesBucket` (see [§ 3.7](#37-sortablemovesbucket)) where the type signal matters for readability of the call sites.

**[`Moves`](src/main/java/org/michaelfl/mychess/Moves.java)** — the result type of move generation. Wraps an `IntArray` and adds:

- A sentinel singleton `Moves.ILLEGAL = new Moves(0)`, returned by the generator when the king-capture trick (see [§ 4.5](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection)) detects that the *opponent's* previous move left their own king in check, i.e. the current position is illegal. Callers test with `moves.isIllegal()`, which is reference equality to the singleton.
- `count()`, `getMoves()`, `contains(int)`, and `addMove(int)`.

## 3.7 `SortableMovesBucket`

[`SortableMovesBucket`](src/main/java/org/michaelfl/mychess/SortableMovesBucket.java) holds **two parallel `IntArray`s of equal length** — one for moves, one for sort-key weights — and sorts them together by descending weight:

```java
public final class SortableMovesBucket {
    private final MovesArray moves;
    private final IntArray   weights;

    public final void add(int move, int weight) {
        moves.add(move);
        weights.add(weight);
    }

    public final void sort() {
        // insertion sort, descending by weight, swaps both arrays in lockstep
    }
}
```

Choices worth noting:

- **Insertion sort**, not `Arrays.sort`. Move lists are short (typically 30–40 entries, often filtered down to a single bucket of < 10), and insertion sort wins for small `n`. Specialized fast paths for `n == 1` (no-op) and `n == 2` (single compare-and-swap) are inlined.
- **Parallel arrays**, not array of pairs. Same reason as `IntArray`: no allocation per move.
- The bucket itself is *cleared and reused* per node by `MoveSorterImpl`; not allocated per call.

`SortableMovesBucket` is the building block of move ordering. `MoveSorterImpl` keeps several buckets (PV move, killers, captures sorted by MVV-LVA-style weight, quiet moves) and concatenates them in order to produce the final `Moves` list passed back to the search. See [§ 7.8](search.md#78-move-sorting-sortablemovesbucket) for the full ordering policy.

## 3.8 Zobrist hashing and `PositionEncoding`

**Two distinct position-fingerprint mechanisms** exist in the codebase, used for different purposes.

**(a) Zobrist hash (`long`)** — fast, incrementally maintainable, used in the search.

The classical Zobrist scheme: a precomputed table of random `long`s, one per (piece, square, …) feature. The hash of a position is the XOR of the table entries for all features present. Because XOR is its own inverse, the hash can be updated **incrementally** when a piece moves: XOR-out the old (piece, from), XOR-in the new (piece, to), XOR-out any captured (piece, to). All inside `Board._makeNormalMove(...)` and friends.

The table is [`RandomNumbers.RANDOM_NUMBERS`](src/main/java/org/michaelfl/mychess/RandomNumbers.java) — 793 hardcoded `long` literals. The index layout is encoded by three constants in `Board`:

```java
private final static int TURN_INDEX           = 12 * 64;       // = 768, length 1
private final static int CASTLING_RIGHTS_INDEX = 12 * 64 + 1;  // = 769, length 16
private final static int EN_PASSANT_INDEX     = 12 * 64 + 17;  // = 785, length 8
```

| Index range | Feature |
|---|---|
| 0 … 767 | (piece_kind 0–11) × 64 squares — `pieceIndex * 64 + squareIndex` |
| 768 | side-to-move = black |
| 769 … 784 | castling-rights bitmask combination (16 possible values, low 4 bits of `castlingState`) |
| 785 … 792 | en-passant file (a–h, derived from `enPassantField % 12 - 2`) |

`ChessUtil.getPieceNumber12(piece)` maps a piece byte (8–21) to a 0–11 index; `ChessUtil.getFieldNumber64(field)` maps a mailbox index (26–117) to a 0–63 square index.

Castling and turn updates happen in `Board.makeMove` after the type-specific function returns:

```java
// Reset old castling bits, set new ones
newPositionHash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (gameStatus.getCastlingState() % 16)];
newPositionHash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (newCastlingState % 16)];

// Switch turn
newPositionHash ^= RANDOM_NUMBERS[TURN_INDEX];
```

The hash is stored on the new `GameStatus` and pushed onto the status stack. `Board.isThreefoldRepetition()` walks the stack backward (in 2-ply steps, since repetition requires the same side to move) and counts hash matches.

A static `Board.calculatePositionHash(rawBoard, gameStatus)` exists as a from-scratch fallback for when there is no previous status (`PositionEncoding.decode`, FEN import, tests). The asserted invariant is that incremental updates and from-scratch computation always agree.

**(b) `PositionEncoding`** — compact, lossy-but-sufficient, used for serialization.

[`PositionEncoding`](src/main/java/org/michaelfl/mychess/PositionEncoding.java) encodes a position into **up to 192 bits (3 longs)** by packing into a `BitSet`:

```
bits 0..63     "is this square occupied?" — one bit per a1..h8 square
bits 64..191   4 bits per occupied piece, in a1..h8 order, listing only non-empty squares
```

Empty squares contribute one bit (occupancy) and zero piece bits, so the total length scales with the number of pieces on the board. The 4-bit piece codes overload some values to carry side-info into the same bit budget:

| Code | Meaning |
|---|---|
| 0–5 | White pawn / rook / knight / bishop / queen / king |
| 6 | White king, but it's *black's* turn (side-to-move indicator) |
| 7 | White pawn that just made an en-passant-eligible double step OR white rook with castling rights still intact (disambiguated on decode by the rank) |
| 8–13 | Black pawn / rook / knight / bishop / queen / king |
| 14 | Symmetric to 7 for black |

The algorithm is borrowed from [a code-golf solution](https://codegolf.stackexchange.com/questions/19397/smallest-chess-board-compression). It is used for compact storage in the opening DB and for round-trip tests, not on the search hot path.

Note: **the `OpeningDB` key is not the `PositionEncoding` blob.** The key is the first three space-separated tokens of the FEN string — board, turn, castling — produced by `Board.calculatePositionKey()`. The en-passant target, half-move clock, and full-move number are dropped. `PositionEncoding` is a separate compression scheme; see [§ 9.1](opening-database.md#91-storage-format-mapdb) for the opening DB's actual value layout.

## 3.9 `MoveDescription` (symbolic moves)

[`MoveDescription`](src/main/java/org/michaelfl/mychess/MoveDescription.java) is the **symbolic** representation of a move — the form a move takes in user input or in a PGN file, before the move generator has resolved which actual board move it refers to. It is the counterpart to the packed-int `Move` representation: human-readable, possibly under-specified, never used on the search hot path.

```java
public final class MoveDescription {
    public final int     turn;
    public final byte    piece;                 // 0 = unspecified
    public final int     fromCol, fromRow;      // -1 = unspecified
    public final int     toCol,   toRow;        // mandatory
    public final byte    pawnPromotionPiece;
    public final Boolean isCapture;             // Boolean (3-valued: true/false/null)
    public final Boolean isCheck;
    public final Boolean isCheckmate;
    public final Boolean isEnPassant;
    public final Boolean isCastlingKingSide;
    public final Boolean isCastlingQueenSide;
}
```

Each `Boolean` flag is **three-valued** (`true`/`false`/`null`): `null` means the user did not assert anything about that property (so it's neither asserted-true nor asserted-false). When a value is `true`, the move resolver in `Board` will *verify* the assertion against the actually computed move and reject the input if it doesn't match — that's how PGN's `+` and `#` annotations and `e.p.` markers are validated rather than silently ignored.

**Parsing** is done by `MoveDescription.fromString(moveString, turn)`, which dispatches on whether the input starts with `O`/`0` (castling) and otherwise applies a single regex with named groups for piece, source-col, source-row, separator (`-` or `x`), target-col, target-row, promotion (`=Q`), check (`+`/`++`/`#`), and en-passant (`e.p.`):

```java
private final static Pattern MOVE_PATTERN = Pattern.compile(
    "^([PNBRQK])?([a-h])?([1-8])?([-x])?([a-h])([1-8])(=?[NBRQ])?(\\+|#|\\+\\+)?( ?e\\.p\\.)?(!|!!|!\\?|\\?!|\\?|\\?\\?)?$");
```

**Resolution** to a concrete `Move` is the job of [`Board.resolveMoveDescription`](src/main/java/org/michaelfl/mychess/Board.java#L938) and `Board.moveDescriptionToMove`. The flow:

1. If the source field is not fully specified (`fromCol < 0` or `fromRow < 0`), call the `MoveGenerator` to enumerate all currently legal moves that land on the target square with the right piece.
2. Filter by any partial source disambiguation the user provided (column, row, or promotion piece).
3. If multiple candidates remain, *try each on a board copy* and discard those that leave the side-to-move in check, narrowing to the single legal candidate.
4. If still ambiguous → `IllegalMoveException("Move is not unique")`. If none remains → `IllegalMoveException("Impossible move")`.
5. With a fully resolved source field, construct the concrete `Move`. The move type (normal / castling / promotion / en-passant) is inferred from the geometry by `Board.moveDescriptionToMove`.

The reverse direction also exists: `Board.moveToShortNotation(Move)` converts a packed-int move back into the *shortest* unambiguous `MoveDescription` for that position — useful for PGN export and for printing the principal variation in human-readable form.

## 3.10 `NextMoveTask` (async result handle)

[`NextMoveTask`](src/main/java/org/michaelfl/mychess/engines/NextMoveTask.java) is the small handle returned by `ChessEngine.nextMoveAsync(env)`. As a **data type** it carries three things:

```java
public final class NextMoveTask {
    private final MyChessEnv             env;          // per-process state (opening DB ref)
    private       Future<MoveAndWeight>  resultFuture; // set by ChessEngine after submission
    private volatile boolean             isCanceled;   // cooperatively polled by PositionSearch
}
```

`env` is set at construction (either a real `MyChessEnv` or, for unit tests and the status engine, a blank instance with no opening DB). `resultFuture` is set *after* the engine submits the search to its executor — but before `nextMoveAsync` returns, so callers never observe a `null` future. `isCanceled` is `volatile` because it is written from any thread (whoever calls `cancel()`) and read from the search worker.

The class is intentionally minimal — it is **not** a `Future` itself, it just delegates to one:

```java
public MoveAndWeight getResult(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    return resultFuture.get(timeout, unit);
}

public void cancel() {
    isCanceled = true;
    resultFuture.cancel(false);
}

public boolean isCanceled() { return isCanceled; }
```

The companion type [`ChessEngine.MoveAndWeight`](src/main/java/org/michaelfl/mychess/engines/ChessEngine.java) is the actual *result* the search returns:

```java
public static final class MoveAndWeight {
    public final int        move;     // packed-int move, or 0 if no legal move (mate/stalemate)
    public final float      weight;   // evaluation in pawns (centipawns / 100)
    public final GameResult result;   // ONGOING / CHECKMATE / STALEMATE / DRAW
    public final int[]      path;     // principal variation, packed-int moves, 0-terminated
}
```

`path` is the *principal variation* extracted from the search's PV table — the best line of play the search found, of length up to `maxDepth + 1`. The first entry is the move the engine is about to play, the next is the expected opponent reply, and so on. The REPL's `tip`, `weight`, and `dw` commands print this in human-readable form via `ChessUtil.pathToString(path)`.

The full concurrency story — single-thread executor, cooperative cancellation, timeout-vs-cancellation split, and the iterative-deepening fallback — is in [§ 2.4](../README.md#24-concurrency-and-async-move-calculation) and [§ 6.5](search.md#65-time-management-and-cancellation).
