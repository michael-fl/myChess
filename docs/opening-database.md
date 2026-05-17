# 9. Opening Database

myChess ships with infrastructure for a persistent opening book, but **no book data**. The book is built offline by replaying a corpus of PGN games, aggregating per-position move statistics, and writing them to a MapDB file. The engine then queries that file at every search to skip computation for known positions.

The three classes involved are all in the [`openingdb`](../src/main/java/org/michaelfl/mychess/openingdb) sub-package:

| Class | Visibility | Role |
|---|---|---|
| `OpeningDB` | `public` | MapDB wrapper. Open / close, store, look up, transactional commit. |
| `DBValue` | package-private | Encodes / decodes the per-position byte-array blob. |
| `OpeningDBImporter` | package-private | PGN ingestion pipeline that populates the DB. |

The book file lives at **`db/openings.db`** (relative to the JVM's working directory). The `db/` directory is git-ignored and auto-created on first run. If the file is missing or empty, lookups return `null`, the engine falls through to a full search, and play is unaffected — there is no failure mode for "no book".

## 9.1 Storage format (MapDB)

[`OpeningDB`](../src/main/java/org/michaelfl/mychess/openingdb/OpeningDB.java) wraps a [MapDB](https://mapdb.org/) `BTreeMap<String, byte[]>`:

```java
private OpeningDB() {
    db = DBMaker
            .fileDB("db/openings.db")
            .transactionEnable()
            .closeOnJvmShutdown()
            .make();

    dbMap = db.treeMap("openingsMap")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.BYTE_ARRAY)
            .valuesOutsideNodesEnable()
            .createOrOpen();
}
```

Three MapDB-specific choices worth flagging:

- **`transactionEnable()`** — writes are batched and made durable by `commit()`. The importer commits every 1,000 inserts and on completion; the engine never writes, only reads.
- **`closeOnJvmShutdown()`** — registers a shutdown hook so MapDB unlocks the file on `kill -SIGTERM`. The primary close path is still the try-with-resources block in [`MyChessMain`](../src/main/java/org/michaelfl/mychess/MyChessMain.java); the shutdown hook is a safety net.
- **`valuesOutsideNodesEnable()`** — value blobs are stored separately from B-tree nodes. Per-position blobs grow with the number of distinct moves seen at that position (see DBValue layout below), so keeping them off the B-tree nodes prevents the tree from going wide.

### Key

The key is the **FEN prefix** of the current board — the first three space-separated tokens of `Fen.exportFEN(board)`, namely piece placement + active color + castling availability. En-passant target, half-move clock, and full-move number are all dropped, so positions reached by different move paths but otherwise identical (and positions that differ only in an unused en-passant flag) hash to the same key.

```java
public String calculatePositionKey() {
    var fen = exportFEN();
    int i1 = fen.lastIndexOf(' ', fen.lastIndexOf(' ', fen.lastIndexOf(' ') - 1) - 1);
    return fen.substring(0, i1);
}
```

The triple `lastIndexOf(' ', ...)` walks back from the end three times, finding the space *before* the en-passant token; `substring(0, i1)` then cuts at that space, leaving exactly the first three tokens. The result is a string like `"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"`.

**Why a FEN prefix and not the Zobrist hash?** The Zobrist hash is a 64-bit `long` and would key the BTreeMap with collisions roughly one per 4 billion entries — fine in practice, but the FEN prefix is human-readable, debuggable, and trivially round-trippable. Storage cost is higher (~50–80 bytes per key vs 8 for a hash), but the book has on the order of 10–100k positions so the overhead is negligible. Also: the Zobrist hash is *engine-internal* — its values would invalidate the entire DB if `RandomNumbers.RANDOM_NUMBERS` ever changed, while the FEN prefix is a stable standard.

### Value: `DBValue` byte layout

[`DBValue`](../src/main/java/org/michaelfl/mychess/openingdb/DBValue.java) interprets the stored `byte[]` as a length-prefixed records-array:

```
 byte offset:   0          4         8          12         16        20         24    …
                ┌──────────┬─────────┬──────────┬──────────┬─────────┬──────────┬───
                │ position │ move 1  │ total 1  │ win 1    │ loss 1  │ move 2   │ total 2 │ …
                │ count    │ (int)   │          │          │         │ (int)    │         │
                └──────────┴─────────┴──────────┴──────────┴─────────┴──────────┴───
                4 bytes    └──── 16 bytes per move entry ─────────────┘
```

| Field | Size | Meaning |
|---|---|---|
| `position count` | 4 bytes | total games in which this position was reached (used by the importer's cleanup pass) |
| per move entry: `move` | 4 bytes | the packed-int move played from this position (see [§ 3.3](data-types.md#33-move-encoding-packed-int)) |
| per move entry: `total` | 4 bytes | games in which this move was played from this position |
| per move entry: `win` | 4 bytes | games where the **player who made this move** won |
| per move entry: `loss` | 4 bytes | games where that player lost |

The **draw count is not stored**; it is derived: `draw = total − win − loss`.

All integers are little-endian via `BitOps.createWord` / `BitOps.getByteN` (see [§ 3.3](data-types.md#33-move-encoding-packed-int) for the byte order). Total blob size for a position with `N` distinct moves is `4 + 16·N` bytes — typically 50–400 bytes per position.

**Win/loss attribution.** The `win`/`loss` columns are from the perspective of the side that played the move:

```java
boolean isWinMove =  (result == Result.WHITE_WINS && turn == GameStatus.TURN_WHITE)
                  || (result == Result.BLACK_WINS && turn == GameStatus.TURN_BLACK);
boolean isLossMove = !isWinMove
                  && (result == Result.WHITE_WINS || result == Result.BLACK_WINS);
```

So a move played by white in a game that white won bumps `win`; the same game's perspective on black's first move bumps black's `loss` (because black played a move and then lost). Anything that is neither a player-won nor player-lost outcome (a draw, or a result of unknown / abandoned) implicitly bumps the draw count (= total − win − loss).

**Add-move algorithm** ([`DBValue.addMove`](../src/main/java/org/michaelfl/mychess/openingdb/DBValue.java#L36)):

1. Bump `position count` at offset 0.
2. Linear-scan the records for an entry whose `move` matches. The scan is O(N) in moves, but N is typically < 20 at any opening position.
3. **If found:** bump `total` and either `win` or `loss` in place.
4. **If not found:** call `increaseSpaceAndInsertNewMove`, which `Arrays.copyOf`s the buffer to grow it by 16 bytes and writes the new entry at the end. This is O(blob size) per insertion — fine because each position is rarely amended more than a handful of times during a single PGN import.

The `byte[]` is the canonical representation: it lives in MapDB, in the importer's in-memory `positionMap`, and as the `DBValue.buf` field. Conversion to `DBValue` is just wrapping the array reference.

## 9.2 Lookup policy

`OpeningDB`'s read-side API is small:

```java
public static OpeningDB open();              // factory; uses fixed db/openings.db path
public PositionInfo lookupPosition(String key);
public DBValue get(String key);              // raw access, used by the importer
public void close();                         // AutoCloseable
```

…plus write-side methods used only by `OpeningDBImporter`:

```java
public void put(String key, byte[] value);
public void commit();
public void rollback();
```

**`lookupPosition`** does the standard read path — fetch the blob, wrap it in `DBValue`, expand into a `PositionInfo` that lists all known moves with their counts:

```java
public PositionInfo lookupPosition(String key) {
    var dbValue = get(key);
    if (dbValue == null) {
        return null;
    }
    return new PositionInfo(dbValue);
}
```

`PositionInfo` and its element type `MoveInfo` are declared inside `OpeningDB`:

```java
public static final class MoveInfo {
    public final Move move;
    public final int  winCount;
    public final int  drawCount;
    public final int  lossCount;

    public int getTotalCount()      { return winCount + drawCount + lossCount; }
    public int getWinPercentage()   { return Math.round((float) winCount  / getTotalCount() * 100.0f); }
    public int getDrawPercentage()  { return Math.round((float) drawCount / getTotalCount() * 100.0f); }
    public int getLossPercentage()  { return Math.round((float) lossCount / getTotalCount() * 100.0f); }
}

public static final class PositionInfo {
    public final int            count;     // = dbValue.getPositionCount()
    public final List<MoveInfo> moves;     // immutable, in DB-storage order
}
```

The percentages always round (`Math.round`), so a 1-game move shows up as 100% in whichever bucket it landed in. The filter thresholds in the engine (≥ 100 total games minimum, see below) make these percentages statistically meaningful for actually-used positions.

**The engine's lookup-and-pick policy** is in [`ChessEngine.getMoveFromOpeningDB`](../src/main/java/org/michaelfl/mychess/engines/ChessEngine.java) and the rules are documented in [§ 7.7 Opening-book lookup](search.md#77-opening-book-lookup). Briefly: filter by `totalCount >= 100`, `winPercentage >= 20`, `lossPercentage < 45`; then weighted-random sample by `totalCount`. The `getRandom()` source is the engine's own `java.util.Random` instance, persisted across calls so move-variety is preserved within a session.

**REPL access.** The `o` command (handled by `OpeningDBCommand` in `CommandHandler`) prints the current position's lookup result without playing a move — useful for inspecting the book:

```
> o
[for each known move in the current position:]
  <move>  total=<N> win=<W>% draw=<D>% loss=<L>%
```

`o <move>` filters to a single move's stats. The command exists for debugging and curiosity — game flow does not depend on it.

**Lookup is transactional**, but reads do not commit. The transaction overhead per `lookupPosition` is the cost of a single MapDB B-tree descent and a `byte[]` deserialization. For positions not in the book it bottoms out at the BTreeMap miss-path (no value decoding) and is essentially free.

## 9.3 Import pipeline

[`OpeningDBImporter`](../src/main/java/org/michaelfl/mychess/openingdb/OpeningDBImporter.java) builds the DB from a directory of PGN files. It is the only writer to `db/openings.db` in the codebase.

**It is package-private and not exposed via the REPL.** The only way to run it is to invoke its `main(...)` directly:

```bash
mvn compile exec:java -Dexec.mainClass=org.michaelfl.mychess.openingdb.OpeningDBImporter
```

…and it **only works as-is on the author's machine**, because the source directory is hard-coded:

```java
void importPGNs() throws IOException {
    var dir = Path.of("/Users/mf/_PRIVAT_/Schach/KingBase2019-pgn/");
    Files.list(dir).forEach(...);
}
```

Other users must edit that path to point at their own PGN corpus before running. The original target — *KingBase 2019* — is a freely available large database of master-level games.

### Pipeline stages

```
PGN files          import          accumulate           cleanup            persist           verify
─────────  →   ─────────────  →  ─────────────  →   ──────────────  →  ─────────────  →  ─────────────
KingBase   read PGN, replay  positionMap         drop positions      MapDB BTreeMap   re-read DB,
.pgn dir   moves, record     (HashMap<String,    with count < 20     write all        assert blobs
           each position     byte[]>, in-mem)                        entries,         match in-mem
           up to ply 32                                              commit per 1000  values
```

**1. PGN parsing** — `Pgn.parse(reader, true)` returns a list of `Pgn` records (one per game). The `true` flag is "lenient" mode — invalid PGNs are skipped rather than aborting the whole file. Files are read as **ISO-8859-1** (PGN's de-facto encoding for special characters in player names and comments).

**2. Replay** — `importPgn(Pgn)` constructs a fresh `Board`, then replays each move:

```java
for (var moveDescr : pgn.moves) {
    moveDescr = board.resolveMoveDescription(moveDescr, moveGenerator);
    var move = board.moveDescriptionToMove(moveDescr);

    var key = board.calculatePositionKey();          // FEN-prefix key
    var dbValue = new DBValue(positionMap.get(key));
    dbValue.addMove(move.getMove(), moveDescr.turn, pgn.result);
    positionMap.put(key, dbValue.getBuffer());

    board.makeMove(move.getMove());
    totalMovesCounter++;

    if (++depth == maxDepth) break;                   // = 32 plies = 16 full moves
}
```

Key recording happens **before** `makeMove` — the position recorded is the one from which the move was played, not the one resulting from it.

**3. Depth cap** — `maxMoveDepth = 16` *full moves* (32 plies). Beyond that, opening theory is exhausted and positions become game-specific. Stopping at ply 32 keeps the DB compact — for 1M games at 32 plies each, the per-position counts amortize: most positions appear in only a handful of games, only the truly common ones cross the cleanup threshold.

**4. Black-first PGNs are skipped:**

```java
if (pgn.moves.get(0).turn == GameStatus.TURN_BLACK) {
    return;
}
```

Some PGN sources include puzzle positions starting with black to move; these would corrupt the win/loss attribution against the standard starting position. They are dropped wholesale.

**5. Cleanup** — `cleanupPositionMap()` drops any position with `dbValue.getPositionCount() < 20`. A position must have been reached at least 20 times in the input corpus to be retained:

```java
if (dbValue.getPositionCount() < 20) {
    iter.remove();
}
```

This is the **statistical reliability filter** complementing the engine-side `totalCount >= 100` filter (which is per-move, not per-position). Together they ensure the book never plays a move that wasn't observed in a meaningful number of games.

**6. Persistence** — `buildDatabase()` opens the DB, writes every surviving `positionMap` entry, commits every 1,000 inserts:

```java
for (var entry : positionMap.entrySet()) {
    db.put(entry.getKey(), entry.getValue());
    if (++count % 1000 == 0) {
        db.commit();
        System.out.println("Inserted #" + count);
    }
}
db.commit();
```

Periodic commit keeps the transaction log bounded — without it a single all-or-nothing commit at the end would peak memory hard on a million-position book. On any `Error` or `RuntimeException` during the write, the whole pipeline rolls back to the last commit.

**7. Verification** — `verifyDatabase()` re-opens the DB and reads back every key that was just inserted, asserting that the stored blob byte-equals what was in `positionMap`. This catches MapDB persistence bugs, serializer mismatches, or off-by-ones in `DBValue` byte layout. Verification is a slow but cheap insurance pass (no work other than reads), and the importer's runtime is dominated by the PGN parse + replay anyway.

### Resource usage

The in-memory `positionMap` is sized at `new HashMap<>(100000)` (initial capacity 100k). At ~80 bytes per key and 50–400 bytes per value, a fully populated book costs on the order of 50–100 MB resident before persistence. The full KingBase 2019 corpus produces a `db/openings.db` of roughly the same size after MapDB's overhead. Importing the full corpus takes minutes (dominated by PGN parsing + move resolution) — not the kind of thing the engine runs at startup, hence the offline-only design.
