# 10. Notation and I/O

myChess speaks three notations: **FEN** for position snapshots, **PGN** for game records (input only), and **algebraic notation** for individual moves (both input and output, in two flavors). All notation handling sits in the root package; nothing in `engines/` or `openingdb/` parses or emits notation directly. The notation layer is also the **boundary between user input and internal representations**: every line typed at the REPL flows through one of the parsers in this chapter before reaching `Game.makeMove(...)` or any of the engine APIs.

## 10.1 FEN

[`Fen`](../src/main/java/org/michaelfl/mychess/Fen.java) implements **Forsyth–Edwards Notation export** as a static utility. The class is package-private and stateless; the public entry point is [`Board.exportFEN()`](../src/main/java/org/michaelfl/mychess/Board.java).

**Status: export only.** There is no FEN *import* in the codebase — no `Fen.importFEN`, no `Fen.parseFEN`, no `Board(String fen)` constructor. Positions can be loaded into the engine only via:
- the default starting position (`new Game()`, `new Board()`),
- replay of a `List<MoveDescription>` (`new Game(config, moves)`), or
- the binary round-trip via [`PositionEncoding.decode`](data-types.md#38-zobrist-hashing-and-positionencoding).

This means a FEN string printed by the `fen` REPL command cannot be pasted back in to restore the position — `fen` is a one-way diagnostic. (Adding a `fromFEN` parser is straightforward and would close this gap.)

### Output format

`Fen.exportFEN(Board)` produces the **full six-field FEN** specified by the standard:

```
<piece placement> <active color> <castling> <en passant> <half-move clock> <full-move number>
```

Example (starting position):

```
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
```

Field by field:

| Field | Format | Source |
|---|---|---|
| Piece placement | rank 8 → rank 1, ranks separated by `/`, empty runs encoded as digits | `Board.fenSymbols[]` lookup per square (see [§ 3.2](data-types.md#32-piece-encoding)) |
| Active color | `w` / `b` | `GameStatus.turn` |
| Castling availability | concatenation of `K`/`Q`/`k`/`q` for the four rights, or `-` if none | `GameStatus.castlingState` bits |
| En passant target | algebraic name of the skipped square, or `-` | `GameStatus.enPassantField` via `ChessUtil.fieldToString` |
| Half-move clock | integer plies since last pawn move or capture | `GameStatus.halfMoveClock` (see [§ 8.4](game-lifecycle.md#84-fifty-move-rule)) |
| Full-move number | `(plyCount / 2) + 1` | `GameStatus.plyCount` |

The piece-placement encoder is a single loop over ranks 7 → 0:

```java
private static void writePosition(Board board, StringBuilder buf) {
    for (int row = 7; row >= 0; row--) {
        int emptyCount = 0;
        for (int col = 0; col <= 7; col++) {
            byte piece = board.getPieceAt(col, row);
            if (piece == Board.empty) {
                emptyCount++;
            } else {
                if (emptyCount > 0) {
                    buf.append(emptyCount);
                    emptyCount = 0;
                }
                buf.append(Board.fenSymbols[piece]);
            }
        }
        if (emptyCount > 0) {
            buf.append(emptyCount);
        }
        if (row > 0) {
            buf.append('/');
        }
    }
}
```

The empty-run digits are written only at non-empty squares or end-of-rank, so the standard's "no leading zeros, no consecutive digits" rule falls out for free.

### FEN-prefix key

`Board.calculatePositionKey()` strips the last three FEN fields (en-passant target, half-move clock, full-move number) and returns the three-field prefix `<placement> <color> <castling>`. This is the key used by the [opening database](opening-database.md#91-storage-format-mapdb) — see that chapter for details on why a FEN prefix rather than the Zobrist hash. Dropping the en-passant target collapses positions that differ only in an unused en-passant flag into the same book entry.

The implementation is a `lastIndexOf(' ', ...)` chain:

```java
public String calculatePositionKey() {
    var fen = exportFEN();
    int i1 = fen.lastIndexOf(' ', fen.lastIndexOf(' ', fen.lastIndexOf(' ') - 1) - 1);
    return fen.substring(0, i1);
}
```

Three nested calls walk back from the end, each finding the previous space. The cut runs *up to* the third-to-last space (the one before the en-passant field), so the result is the three FEN tokens `<placement> <color> <castling>` — en-passant, half-move clock, and full-move number are all dropped.

## 10.2 PGN

[`Pgn`](../src/main/java/org/michaelfl/mychess/Pgn.java) is a **PGN parser** — input only. There is no PGN export.

The class is `public final` and exposes four static `parse(...)` overloads, all reducing to one core parse that takes a `BufferedReader`:

```java
public static Stream<Pgn> parse(String pgn);
public static Stream<Pgn> parse(String pgn, boolean ignoreErrors);
public static Stream<Pgn> parse(File pgnFile, boolean ignoreErrors);    // opens with UTF-8
public static Stream<Pgn> parse(BufferedReader pgnReader, boolean ignoreErrors);
```

The encoding choice is the caller's responsibility — the `File` overload uses **UTF-8**, but the [opening-DB importer](opening-database.md#93-import-pipeline) reads PGN with **ISO-8859-1**, matching the de-facto encoding of large historical PGN databases like KingBase. Both work for ASCII move text.

**Return type is a `Stream<Pgn>`** — a single PGN source can contain many games concatenated, and the iterator (`PGNIterator`) reads one game at a time lazily. This matters for the importer where the input may be hundreds of megabytes.

### The `Pgn` record

A successful parse produces a `Pgn` instance carrying three things:

```java
public final class Pgn {
    private final String notation;                  // raw source text, retained for error messages
    public  final Result result;                    // game outcome
    public  final List<MoveDescription> moves;      // unmodifiable, ordered, both colors
}

public enum Result {
    WHITE_WINS, BLACK_WINS, DRAW, ONGOING, UNKNOWN
}
```

`moves` is the **ordered list of `MoveDescription`s** as parsed (see [§ 3.9](data-types.md#39-movedescription-symbolic-moves)), alternating white and black starting with white. There is no separation between white and black move lists — consumers iterate sequentially and rely on position to identify the side.

### Two-pass parsing

PGN parsing is a two-pass structure:

1. **Line classification** (`PGNIterator.readOnePgn`) — read lines, distinguish *tag pairs* (lines beginning with `[`) from *move text* (everything else). Tag pairs are accumulated until move text starts; a tag after move text or a missing closing `]` throws `IllegalPGNException`.
2. **Move text tokenization** (`Builder.build()` + `Tokenizer`) — once a *game termination marker* (`1-0`, `0-1`, `1/2-1/2`, or `*`) or EOF is seen, the accumulated move text is tokenized and parsed.

**Game termination markers** map to `Result`:

| Marker | `Result` |
|---|---|
| `1-0` | `WHITE_WINS` |
| `0-1` | `BLACK_WINS` |
| `1/2-1/2` | `DRAW` |
| `*` | `UNKNOWN` |
| missing | `ONGOING` |

Move tokens are required to come in **strict triples**: move-number-with-dot, white move, black move. The builder tracks position via `i % 3`:

```java
} else if (i % 3 == 0) {        // move no — must equal expectedMoveNo
    int moveNo = parseMoveNo(token);
    if (moveNo != expectedMoveNo) {
        throw new IllegalPGNException(…);
    }
    expectedMoveNo++;
} else if (i % 3 == 1) {        // white move
    if (!"..".equals(token)) {
        moves.add(MoveDescription.fromString(token, GameStatus.TURN_WHITE));
    }
} else if (i % 3 == 2) {        // black move
    moves.add(MoveDescription.fromString(token, GameStatus.TURN_BLACK));
}
```

The `".."` token is recognized as a place-holder for "white has no move recorded here" (used in positions that start mid-game). Black's slot does *not* accept `".."` — encountering it throws.

**Strictness.** Move numbers must be consecutive starting at 1. There is no support for:
- annotations (`!`, `!!`, `?`, `??`, `!?`, `?!`) — actually these are accepted *inside* a move token by `MoveDescription.fromString`'s regex but not separately.
- comments (`{...}` or `;...`)
- numeric annotation glyphs (`$1`, `$2`, …)
- variations (`(...)`)
- recursive annotations

A PGN with any of these inside the move text will throw `IllegalPGNException` from the tokenizer or `IllegalArgumentException` from `MoveDescription.fromString`. The `ignoreErrors` flag is the practical workaround: when `true`, `PGNIterator` swallows the bad game and continues to the next.

### Lenient mode (`ignoreErrors = true`)

Used by [`OpeningDBImporter`](opening-database.md#93-import-pipeline). On any `IllegalPGNException` or `IllegalArgumentException` (the parser's two principal failure modes), `PGNIterator.readNextPgn` logs the failure to `System.err`, throws away the offending game's accumulated state, and tries to read the next game. This is essential for large historical corpora where individual games may have unusual annotations or formatting.

Strict mode (`ignoreErrors = false`, the default for `parse(String)`) aborts the whole stream on the first malformed game. Used by the REPL's `import` command — failing fast is better than silently dropping a paste mistake.

### Importing into a `Game`

[`PGNImporter`](../src/main/java/org/michaelfl/mychess/PGNImporter.java) wraps a single `Pgn` and replays it via `new Game(config, pgn.moves)`. By default it uses a configuration with [threefold repetition](game-lifecycle.md#83-threefold-repetition) and [fifty-move rule](game-lifecycle.md#84-fifty-move-rule) **disabled**:

```java
public Game importGame() {
    var config = new GameConfig(
            MyChessEngine.class,
            new EngineConfig.Builder()
                    .enableThreefoldRepetition(false)
                    .enableFiftyMovesRule(false)
                    .build());
    return importGame(config);
}
```

This is so historical games that legitimately reach a 3-fold or 50-move position without claiming a draw can still be fully replayed without the status engine prematurely flagging the game as `DRAW` mid-import.

## 10.3 Algebraic notation

Three move-notation systems coexist in the codebase, each used at a different boundary:

| System | Parser | Emitter | Used by |
|---|---|---|---|
| **Short algebraic** (PGN-style: `Nf3`, `O-O`, `exd5`, `e8=Q+`) | `MoveDescription.fromString` | `Board.moveToShortNotation` | PGN parsing, REPL move input, PGN-style display |
| **Long algebraic** (`e2-e4`, `b1-c3`, `e7-e8Q`) | `SimpleNotationImporter.parseMove` | `ChessUtil.moveToString` | REPL `import [[…]]`, engine self-play move log, default print |
| **Field tuple** (raw `(col, row)` → mailbox index) | `ChessUtil.getColAndRowFromString` | `ChessUtil.fieldToString` | building block for both above |

### Short algebraic (`MoveDescription`)

Covered in detail in [§ 3.9 `MoveDescription` (symbolic moves)](data-types.md#39-movedescription-symbolic-moves). To summarize what is relevant here:

- **Regex-based parser** with one pattern for normal moves and one for castling (`O-O` / `0-0` / `O-O-O` / `0-0-0`, both spellings accepted).
- **Lossy → fully resolved** via [`Board.resolveMoveDescription`](data-types.md#39-movedescription-symbolic-moves), which uses the [move generator](move-generation.md) to fill in missing source-square information and reject ambiguous or impossible moves.
- **Annotation suffixes** (`!`, `!!`, `?`, `??`, `!?`, `?!`) are accepted and silently dropped; `+`, `#`, `++` are parsed into the `isCheck` / `isCheckmate` boolean fields and *verified* against the actual move outcome (a wrong `+` raises `IllegalMoveException`).
- **`e.p.` suffix** is accepted as an en-passant marker; the resolver does not require it (en-passant is detected from geometry) but a present marker is verified.

The REPL's `MoveCommand` also applies a **case-fixup heuristic**: if the user types `nf3` (lowercase piece letter), it retries with the first character uppercased before giving up. This is convenience-only — PGN proper requires uppercase piece letters.

### Long algebraic (`SimpleNotationImporter`)

[`SimpleNotationImporter`](../src/main/java/org/michaelfl/mychess/SimpleNotationImporter.java) parses the long-algebraic form used by the REPL's `import [[...]]` syntax and by the engine's own self-play log. Format:

```
[[ <move1> <move2> <move3> ... ]]
```

…where each `<move>` is `<from>-<to>[<promotion>]`, e.g.:

```
[[e2-e4 e7-e5 g1-f3 b8-c6 e8-e8Q]]
```

The implementation is intentionally tiny — a 4-step substring split:

```java
if (moveNotation.length() < 5 || moveNotation.length() > 6 || moveNotation.charAt(2) != '-')
    throw new IllegalArgumentException("Illegal move notation: " + moveNotation);
String fromFieldNotation     = moveNotation.substring(0, 2);
String toFieldNotation       = moveNotation.substring(3, 5);
char   pawnPromotionSymbol   = moveNotation.length() >= 6 ? moveNotation.charAt(5) : 0;
```

Six characters maximum: `e7-e8Q`. There is no support for castling notation (`O-O`) in this format — castling is encoded as the king's source-to-target move (e.g. `e1-g1`), which the resolver later recognizes from geometry. There is no support for capture indicators (`x`), check indicators (`+`), or any disambiguation — the full source square is always given, so disambiguation is unnecessary.

**Use cases:**

1. **REPL `import [[...]]`** when the user pastes a move list. Recognized by `GameImporter.importerFor` via the `[[` prefix.
2. **The engine's `auto`-game move log**: `Game.exportMoves()` builds exactly this format from the board's status stack, so a self-played game can be round-tripped: `auto` produces output, paste-into-`import` reproduces the position.

### Output formatting

The engine has three main move-to-string helpers in [`ChessUtil`](../src/main/java/org/michaelfl/mychess/ChessUtil.java):

- **`ChessUtil.moveToString(int move)`** — long algebraic without context. Returns `e2-e4`, with promotion suffix `Q`/`R`/`B`/`N` if the move is a pawn promotion. Used in error messages, the move log line of `go`, and the PV display.
- **`ChessUtil.moveToString(int move, Board board)`** — PGN-like short algebraic given the board context (uses board to look up the moving piece). Returns `Nf3`, `exd5`, etc. Used by `Board.moveToShortNotation` as a fallback when the symbolic form can't be uniquely reconstructed.
- **`ChessUtil.pathToString(int[] path)`** — formats a principal-variation array (0-terminated `int[]`) by joining `moveToString` outputs with spaces. Used by the `tip` and `weight` commands.

`Board.moveToShortNotation(Move)` is more ambitious: it builds a `MoveDescription` with only the minimum disambiguating fields (no source column unless needed; no source row unless needed; with `x`/`+`/`#` annotations when applicable), then verifies the result resolves back to the same move via the move generator. The retry logic walks four candidate forms (no disambiguation → column-only → row-only → both) and picks the first that round-trips cleanly. Used for PGN-quality output in the `go` and `o` commands.

### Weight formatting

Aside from moves, [`ChessUtil.weightToString`](../src/main/java/org/michaelfl/mychess/ChessUtil.java) formats evaluation scores for display:

| Input weight | Output |
|---|---|
| `ILLEGAL_WEIGHT_NEG` / `POS` | `"illegal"` |
| In the mate-score range `[100_000, 200_000]` (positive or negative) | `"M3"` (mate in 3) or `"-M5"` (mated in 5) |
| Everything else | decimal pawns: `"1.25"`, `"-0.50"` |

The mate-to-ply translation goes through `WeightingFunction.checkmateWeightToPlies(w)` — see [§ 6.6 Checkmate and stalemate scoring](search.md#66-checkmate-and-stalemate-scoring).

## 10.4 REPL commands

The full command list as registered in `CommandHandler`'s constructor:

```java
private final List<Command> commands = List.of(
        new QuitCommand(),
        new AutoGameCommand(),
        new NewGameCommand(),
        new MoveCommand(),
        new ImportCommand(),
        new PrintCommand(),
        new BoardCommand(),
        new ExportCommand(),
        new RevertCommand(),
        new TipCommand(),
        new LastCommand(),
        new GoCommand(),
        new WeightCommand(),
        new DeepWeightCommand(),
        new LoadCommand(),
        new SetVariantsCommand(),
        new SetDepthCommand(),
        new SetIterationDepthCommand(),
        new PossibleMovesCommand(),
        new FenCommand(),
        new HashCommand(),
        new OpeningCommand(),
        new OpeningMoveCommand()
);
```

Each input line is offered to the commands in order; the first whose `canHandle(line)` returns `true` wins. There is **no syntax help inside the REPL** — typing `help` or `?` triggers `MoveCommand.parseMove` (which fails the algebraic-notation regex) and the line is reported as `"Unknown command"`. The reference below is the only authoritative list.

### Game flow

| Command | Aliases | Effect |
|---|---|---|
| `<algebraic move>` | e.g. `e2-e4`, `Nf3`, `O-O`, `exd5+`, `e8=Q#` | Apply the move for the side to move. If a computer color was set by `go`, the engine responds. |
| `go` | `g` | Engine plays one move for the side to move. Sets *computer color* = side just played, so subsequent user moves are auto-replied to. |
| `auto` | — | Engine plays both colors until the game ends (or 1000 plies hit, in which case the result is forced to `DRAW`). Clears computer color. |
| `revert` | `r` | Undo the last move via `Game.revertMove()`. Clears computer color. The result transitions back to `ONGOING`. |
| `new` | — | Throw away the current game, create a fresh one from the starting position. Clears computer color. |

### Inspection (read-only)

| Command | Aliases | Output |
|---|---|---|
| `print` | `p` | Board + side to move + move count + half-move clock + castling string + result line (if game over). |
| `board` | — | Just the board (no status footer). |
| `moves` | — | All pseudo-legal moves the engine would consider for the side to move, in the sorter's output order. |
| `last` | — | Long-algebraic form of the last move played. |
| `fen` | — | Full six-field FEN of the current position. |
| `hash` | — | The Zobrist position hash as a decimal `long`. |
| `export` | `exp` | The game's move list in `[[ … ]]` long-algebraic form. Round-trips with `import`. |

### Engine queries

| Command | Aliases | Effect |
|---|---|---|
| `tip` | — | Run a search and print just the move the engine would play, without applying it. |
| `weight` | `w` | Run the static evaluation on the current board (no search) and print the component breakdown via `WeightingFunction.print()`. |
| `dw` | — | "Deep weight": same as `go` but without the opening-DB short-circuit (the env passed to the engine is `null`). Used to force a search even in book positions. |

### Import / load

| Command | Aliases | Effect |
|---|---|---|
| `import <text>` | `imp <text>` | Auto-detects format: starts with `[[` → `SimpleNotationImporter`; otherwise treated as PGN. Replaces the current game. |
| `l` | — | **Debug shortcut**: load a hardcoded PGN-like move list baked into [`LoadCommand.handle`](../src/main/java/org/michaelfl/mychess/CommandHandler.java#L209). The hardcoded string is changed during development; it is not stable. |

### Opening database

| Command | Aliases | Effect |
|---|---|---|
| `o` | — | Look up the current position in the opening DB. Prints a numbered table of all known moves with `#games / win% / draw% / loss%`. |
| `o<N>` | e.g. `o1`, `o2`, `o12` | Play the `N`-th most popular book move from the current position (1-indexed; sorted by `getTotalCount()` descending). |

### Configuration (not implemented)

| Command | Status |
|---|---|
| `config variants <N>` | Prints `"not implemented"`. |
| `config depth <N>` | Prints `"not implemented"`. |
| `config iteration-depth <N>` | Prints `"not implemented"`. |

The handlers are wired but the implementations are commented out — see [`SetDepthCommand`](../src/main/java/org/michaelfl/mychess/CommandHandler.java#L417) and friends. Re-enabling them would require `EngineConfig` to be mutable or `Game` to support engine reconfiguration on the fly, neither of which currently exists.

### Quit

| Command | Aliases | Effect |
|---|---|---|
| `quit` | `exit`, `q` | Calls `game.shutdown()` (stops engine executors) and returns `false` from `nextCommand`, exiting the main loop. The try-with-resources block in `MyChessMain` then closes the opening DB. |

### Adding a new command

The shape is fixed: define a nested `final` subclass of `Command` inside [`CommandHandler`](../src/main/java/org/michaelfl/mychess/CommandHandler.java), implement `canHandle(String)` (returns `true` iff this command should claim the line) and `handle(String) throws Exception`, then append a new instance to the `commands` list in `CommandHandler`'s constructor. The list order matters only when two commands could accept the same line — the existing list is hand-ordered so the most specific predicate wins (e.g. `AutoGameCommand` for the bare word `auto` before `MoveCommand` would even try to parse it). See [§ 2.5 REPL and main loop](../README.md#25-repl-and-main-loop) for the dispatch mechanism.
