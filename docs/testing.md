# 11. Testing

The test suite is the executable specification for myChess: it pins down move generation, encoding, search behavior, evaluation ranges, notation parsing, and the rules for draws. It is also the way the engine's playing strength is regression-tested — most of `EngineTest` and `DeepWeightTest` are *position regressions*, where a particular game state must produce a particular move or score range.

| Metric | Value |
|---|---|
| Test classes | 79 `*Test.java` (+ 25 helpers and measurement drivers under the same source root, counted 2026-08-18) |
| Test methods (`@Test` + `@ParameterizedTest`) | 901 declared; 1 215 executed, parameterized cases counted per invocation — measured 2026-08-16, before the 2026-08-18 additions |
| Currently passing | all (last full run 2026-08-16: 1 215 run, 0 failures, 4 skipped; the four skipped were retired on 2026-08-18) |
| Currently `@Disabled` | 0 — see [Retired disabled tests](#retired-disabled-tests) |
| Test source lines | ~24 550 |
| Framework | JUnit Jupiter 5.11 |
| Execution | `mvn test` (Maven Surefire 3.5.2) |

Run from the project root:

```bash
mvn test                          # all tests
mvn -Dtest=BoardTest test          # one class
mvn -Dtest=BoardTest#testIsCheckmate1 test   # one method
```

JDK 25 must be active for `mvn test` (set `JAVA_HOME` if the system default differs — see [`README.md`](../README.md#13-repository-layout)).

## 11.1 Suite structure

### Conventions

- **One test class per production class.** `BoardTest` ↔ `Board`, `WeightingFunctionTest` ↔ `WeightingFunction`, etc. Tests live in the matching package path: `src/test/java/org/michaelfl/mychess/<NameTest>.java`.
- **Package-private visibility.** Most test classes are package-private (no modifier), giving them access to package-private production methods. Where needed (a helper used by other test classes — `EngineTest.engineConfig()`, `EngineTest.testPosition(...)`), the helper is package-private but the test class itself stays default-visible.
- **JUnit Jupiter 5.** Mostly plain `@Test`, with a handful of `@ParameterizedTest` + `@ValueSource` cases in `MoveTest` and `MoveDescriptionTest`, and `@Nested` grouping in `MoveGeneratorTest` and `StaticExchangeEvaluationTest` (to cluster the many per-method SEE cases). No display names (`@DisplayName`). The style is otherwise direct: one method per asserted behavior, descriptive method names (`testIsDraw2`, `testIsCheckmate1`, `testWhiteEnPassantMove`).
- **Assertions always carry messages.** All `assertEquals` / `assertTrue` / `assertFalse` calls pass a meaningful third (or appropriate) argument, in line with the project's global testing convention. Failures are self-explanatory in CI logs.
- **No mocking.** The codebase has no third-party dependencies that would benefit from mocking, and `MoveGenerator`/`Board`/`Game` are all fast enough to use as-is. The engine is constructed end-to-end in every test that exercises search behavior.

### Categories

The test classes cluster into seven groups. The per-group line counts below were taken when the suite was ~14 475 lines and have not been re-measured since; the group membership is still accurate, the numbers are indicative:

| Group | Files (count) | Lines | What it covers |
|---|---|---|---|
| **Data structures & encoding** (13) | `BoardTest`, `ChessUtilTest`, `GameStatusTest`, `MoveTest`, `MoveDescriptionTest`, `PositionEncodingTest`, `PieceSquareTablesTest`, `SortableMovesBucketTest`, `BitOpsTest`, `IntArrayTest`, `CastlingSlotTest`, `BoardCastlingRookFilesTest`, `Chess960StartPositionsTest` | ~3750 | Bit-packing, board layout, color/turn bit invariants, sortable bucket sort order, piece-square table inversion, mailbox indexing, castling-slot / rook-file resolution, the 960 start-position table, notation parsing. |
| **Move generation & rules** (7) | `MoveGeneratorTest`, `GameTest`, `PerftTest`, `Chess960CastlingTest`, `MoveSorterImplTest`, `KillerMovesTest`, `SimpleNotationImporterTest` (`BoardTest` overlaps) | ~1510 | Pseudo-legal generation, castling legality (standard + 960), en passant, check / checkmate / stalemate detection, move ordering; **Perft node-count verification** against the Chess-Programming-Wiki reference values. |
| **Search & evaluation** (22) | `EngineTest`, `EngineSmokeTest`, `DeepWeightTest`, `WeightingFunctionTest`, `QuiescenceSearchTest`, `StaticExchangeEvaluationTest`, `PositionSearchTest`, `engines/SearchNodeContextTest`, `engines/IterationTimingsTest`, `MirrorEvalTest`, `HangingPiecesEvalTest`, `BlunderTest`, `ChessEngineTest`, `NextMoveTaskTest`, `EvalRegressionTest`, `IllegalPvRegressionTest`, `StalemateAvoidanceRegressionTest`, `MoveSortInvariantRegressionTest`, `ZobristHashingTest`, `PositionHashConsistencyRegressionTest`, `StsTest`, `StsDefectTest` | ~5205 | Position regressions, eval component ranges, mirror-symmetry of eval, quiescence depth, static exchange evaluation (SEE) for quiescence capture ordering and SEE < 0 pruning, PV-legality regressions, iterative-deepening timings, async task lifecycle, and Zobrist-hash correctness (incremental vs from-scratch, en-passant round-trips, perft-style + randomized Chess960 consistency walks). |
| **Transposition table** (3) | `TranspositionTableTest`, `TranspositionTableIntegrationTest`, `ScoreTTAdjustmentTest` | ~525 | Bucket replacement / eviction policy, `TTEntryView` round-trips, end-to-end TT plumbing through the engine, mate-score depth adjustment on store / probe. |
| **Draw rules** (2) | `ThreefoldRepetitionTest`, `FiftyMovesRuleTest` | ~180 | Detection + opt-out toggle for both rules. |
| **Notation & I/O** (8) | `FenTest`, `PgnTest`, `PGNImporterTest`, `FenChess960ImportTest`, `PGNConverterTest`, `UciMoveParserTest`, `UciHandlerTest`, `LogTest` | ~2910 | FEN export/import (standard + 960 / Shredder), PGN parsing (strict + lenient) and end-to-end replay from arbitrary start positions, UCI move parsing and protocol handling, log routing. |
| **Database** (2) | `openingdb/DBValueTest`, `openingdb/OpeningDBImporterTest` | ~230 | Byte-layout encoding of `DBValue`, win/loss attribution, opening-DB import. |

### Position-as-string idiom

Almost every test sets up a position by typing it as a `SimpleNotationImporter` `[[...]]` string or as a PGN text block:

```java
@Test
void testBlackCheckmate() {
    SimpleNotationImporter importer = new SimpleNotationImporter(
        "[[b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 ... c8-a8]]");
    var game = importer.importGame();
    assertEquals(GameResult.ONGOING, game.getResult(), "game should not be finished yet");
    game.makeMove(MoveDescription.fromString("c8-a8", GameStatus.TURN_WHITE));
    assertEquals(GameResult.CHECKMATE, game.getResult(), "game status should be black checkmate");
}
```

Two flavors coexist:

- **Long algebraic in `[[...]]` brackets** — for tests that need exact, unambiguous move sequences (move generation, hash invariants, the bulk of `EngineTest`).
- **PGN text blocks via Java text blocks (`"""..."""`)** — for tests where the readability of standard PGN matters (`BoardTest`, some `EngineTest` cases like `testPosition32`).

`GameImporter.importerFor(text)` auto-detects which: `[[`-prefix → `SimpleNotationImporter`, otherwise PGN. The test code routinely uses both depending on the author's preference for that particular case.

### Cross-test sharing

A small amount of helper code is shared across files via static imports from `EngineTest`:

```java
import static org.michaelfl.mychess.EngineTest.engineConfig;
import static org.michaelfl.mychess.EngineTest.testPosition;
```

`EngineTest.engineConfig()` returns a standard depth-8 `EngineConfig`; `EngineTest.testPosition(...)` is the four-overload position-regression harness used by both `EngineTest` and `DeepWeightTest`. There is no top-level test base class; helpers are loose static functions.

`BlunderTest` shares its **depth-bound** harness the same way, with `StsDefectTest`:

```java
import static org.michaelfl.mychess.BlunderTest.DEPTH_BOUND_TIMEOUT_S;
import static org.michaelfl.mychess.BlunderTest.assertEngineStillPlays;
import static org.michaelfl.mychess.BlunderTest.gameFromFenAtDepth;
import static org.michaelfl.mychess.BlunderTest.searchCurrentPositionDeep;
```

`gameFromFenAtDepth(fen, depth, tt)` builds a game whose engine is bounded by depth rather than by the clock (a 120 s budget so the requested iteration always completes); `assertEngineStillPlays(...)` is the inverse of `assertEngineAvoids(...)` and is what a characterization test asserts — its failure message tells the reader that a flip probably means the defect is fixed and the case should be inverted. These four are package-private rather than private for exactly this reason; everything else in `BlunderTest` stays private.

### Retired disabled tests

The suite has **no `@Disabled` tests**. It had four until 2026-08-18, and how they were retired is the policy for any future one: a test nobody re-enables is a test nobody reads, so each was either rewritten to be green and meaningful or deleted outright.

| File | Method | Why it was disabled | Outcome |
|---|---|---|---|
| `EngineTest` | `dontCaptureWithKingPawn` | Open bug — the search recaptures with the g-pawn and opens its own king's cover. Note said "re-enable once positional evaluation is tightened". | Re-measured: **still red for exactly the stated reason**, and Stockfish confirms the premise (`18...Bxf6` = −0.29 vs `18...gxf6` = −3.90, refuted by `19.Bg4 f5 20.Bh3 Ne4 21.Qh5`). Rewritten as the characterization `captureOnF6WithTheGPawn_characterizesShreddingItsOwnKingCover`, pinning `g7-f6` with a TODO to restore the original expectation. Counted in the `king-safety` family below. |
| `PgnTest` | `testReadLargePGNFile` | Needed a 194 MB external PGN not checked into the repo. | Rewritten as `readingManyConcatenatedGames_yieldsThemAllIntact`, generating 20 000 games into a `@TempDir` — 0.19 s. |
| `PGNImporterTest` | `testImportLargePGNFile` | Same. | Re-measured on a machine where the file exists: **it passed, in 7 816 s.** Never red — just 2 h 10 min of wall-clock, which is why nobody ran it. Replaced by `importingManyConcatenatedGames_replaysEveryOne`: 500 synthetic games whose bodies each carry a move kind that is hard for the *importer* (castling both ways, check with king recapture, en passant, promotion to queen and to knight, file disambiguation), asserting parsed move count against replayed ply count per game. |
| `PGNImporterTest` | `testImportMultipleLargePGNFiles` | Same, over a hard-coded `KingBase2019-pgn/` directory. | **Deleted.** It covered nothing the replacement above does not. |

The lesson worth keeping: three of the four were disabled for **cost, not for failure**, and the cost came entirely from depending on external data. A generated fixture reaches the same coverage in under a second, so that dependency was never necessary. The fourth was a real open defect hidden behind an annotation — which is the worse failure mode, because a disabled test reports neither red nor green.

### What is not tested

Honest gaps in coverage:

- **Insufficient-material draws** are not detected at all (see [§ 8.5](game-lifecycle.md#85-insufficient-material)). No test covers the case because the rule is not implemented.
- **`OpeningDB`** has no test. Only `DBValue` (the byte-layout encoder) is tested. The MapDB integration is exercised only by the live importer.
- **`PositionSearch`'s `alphaBetaSearch`** is not unit-tested directly. Coverage is end-to-end via `EngineTest` and `DeepWeightTest` (which run the full engine), plus `QuiescenceSearchTest` for the quiescence layer in isolation.
- **REPL command dispatch (`CommandHandler`)** has no tests. The commands are exercised manually via interactive runs.
- **`NextMoveTask` cancellation paths** are not tested.

## 11.2 Notable test cases

A tour of the tests that earn their keep — either because they catch high-value invariants or because they document the engine's intended behavior in an exemplary way.

### `EngineTest.testPositionN` (×33) — position regressions

The bulk of the playing-strength regression suite. Each test loads a real-game position and asserts three things:

```java
testPosition(gameNotation,           // long-algebraic [[...]] or PGN text block
             expectedMove,           // single move or Set<String> of acceptable moves
             expectedPathOpt,        // optional: exact expected PV (move-by-move)
             expectedMinWeight,      // weight range (in pawns) the search must produce
             expectedMaxWeight,
             new GameConfig(ENGINE, engineConfig()));   // depth 8, MyChessEngine
```

The harness (`EngineTest.testPosition(...)`) drives the search via `nextMoveAsync().getResult(5, TimeUnit.MINUTES)` — a generous timeout for slow CI machines without being unbounded. It then verifies:

1. The chosen move is in the expected set (either short or long algebraic notation matches).
2. The reported weight lies in `[expectedMinWeight, expectedMaxWeight]`.
3. If `expectedPathOpt` is given, the full principal variation matches move-by-move.
4. The PV length equals `maxDepth - 1` (or fewer for forced-mate lines, where it equals the ply-to-mate).
5. Replaying the PV on the board produces a final `Game.result` that matches the search's reported result.

The `testPosition` overloads cover everything from the first ply of an opening (`DeepWeightTest.testPosition01`) to mate-in-3 endings (`DeepWeightTest.testPosition11`, which uses `checkmateIn(3)` as both the min and max expected weight). The "TODO" annotations in the source (e.g. `0.5f, // TODO 1.7`) document where the current engine produces a measurably weaker evaluation than the test author believes correct — but rather than failing the test, the expected range is loosened to match reality, with the TODO as a marker that revisiting the evaluation could tighten it.

### `ZobristHashingTest` (×8) — incremental-hash correctness

Arguably the most load-bearing test in the suite: incorrect Zobrist updates would silently break threefold-repetition detection ([§ 8.3](game-lifecycle.md#83-threefold-repetition)) and opening-book key generation ([§ 9.2](opening-database.md#92-lookup-policy)).

Three properties are verified:

- **`testHashOfStartPosition`** — the starting-position hash, computed via incremental XOR-ing the 32 pieces' hash contributions plus the castling-rights entry, equals both `Board.calculatePositionHash()` (from-scratch) and `GameStatus.getPositionHash()` (incremental at engine start).
- **`testIncrementalUpdate`** — replays a 100+-ply real game, asserting after *every* move that `calculatePositionHash()` (from scratch) equals `getPositionHash()` (maintained incrementally by `Board.makeMove`).
- **`testIncrementalUpdateWithRevert`** — additionally calls `revertMove` after each `makeMove` and verifies the hash returns to the prior value, then `makeMove` again and verifies the hash is restored. This catches any asymmetry between forward and undo updates.

Plus five tests for the en-passant hash contribution (`testWhiteEnPassantField`, `testBlackEnPassantField`, `testDifferentHashForDifferentPositions`, `testSameHashForSamePosition`, `testEnPassantMakesDifferenceForSamePosition`). The last is subtle: two move orders that arrive at the same piece arrangement must hash *differently* when one of them grants en-passant rights but the other doesn't.

### `PerftTest` — move-generator node-count verification

The gold-standard move-generator correctness test. Enumerates every strictly legal move sequence to a fixed depth from six canonical positions (standard start, Kiwipete, and four Chess-Programming-Wiki positions chosen to exercise rare interactions — en-passant chains, promotion-with-capture, pinned pieces revealing check, castling through attacked squares) and compares the leaf count against the mathematically known values. A discrepancy of even one node means a missed legal move or a spuriously generated illegal one.

Split into a default set (each position at two depths, ~2 s total) and a `@Tag("slow")` set one depth deeper per position (~40 s, ~600 M nodes). The `MoveGenerator` runs in `allPromotions = true` mode so all four promotion piece types are generated, matching the reference counts (production skips bishop under-promotion — see [§ 4.0.6](version-history.md)).

This suite surfaced the latent en-passant Zobrist-drift bug fixed in v4.0.7: the count mismatch appeared only in the two positions whose sub-trees contain en-passant captures, which pointed straight at the ep move handling. Complemented by [`PositionHashConsistencyRegressionTest`](../src/test/java/org/michaelfl/mychess/PositionHashConsistencyRegressionTest.java), which walks the same kind of exhaustive / randomized move sequences asserting incremental-hash consistency at every ply.

### `Sts` / `StsTest` — the Strategic Test Suite, and the difference between a measurement and a test

`Sts` is a **measurement tool, not a graded test** — the same role `tools/run-anchor-bracket.sh` has for Elo. It runs the [Strategic Test Suite](https://www.chessprogramming.org/Strategic_Test_Suite) (1188 positions, 15 themes) and reports a score per theme, so the weakest evaluation component can be *named* rather than guessed at. Launch it with `tools/run-sts.sh`; results and the measurement policy live in [`sts-history.md`](sts-history.md). Credits: [README](../README.md#credits-and-third-party-material).

Three properties are easy to get wrong and worth stating:

- **Partial credit, not best-move-only.** Every position lists up to ten candidate moves (`c9`) with a point value each (`c8`), the best worth 100. myChess earns the value of whichever it plays. An engine that plays the 46-point second choice is measurably different from one that plays the 1-point tenth choice, and binary scoring throws that away. Always read the printed `best` and `miss` columns next to the percentage: the same score can mean "half-good everywhere" or "a third perfect, the rest off the list", which are different diagnoses.
- **Fixed depth, not fixed time.** Each position is searched to a fixed depth with a 24 h per-move budget, so the score is reproducible and machine-independent. Consequence: **the number is not comparable to published STS ratings**, which are measured at fixed time — only to another myChess run at the same depth. And what is measured is "move quality at depth N", evaluation *and* search: a search change that surfaces a different move at the same depth moves the score too.
- **`bm` is SAN while `c9` is from-to notation.** `bm f5` versus `c9 "f4f5 …"`, so `bm` cannot be string-compared against the engine's move; comparison runs against `c9` alone. `bm` does equal `c7`'s first token on every line, which `StsTest` uses as a cheap integrity check.

`StsTest` covers three different things, and conflating them would oversell it:

1. **Unit tests of the parser and the scoring arithmetic**, on hand-written fixtures — independent of the suite file.
2. **The notation contract**, and this is the only part that guards myChess code. For all 1188 positions it asserts every `c9` candidate lies in `{ toUci(m, board) : m legal in board }`. A candidate outside that image is *unreachable* — its points can never be awarded at any depth, and the symptom is indistinguishable from the engine simply playing worse. The assertion goes red when `UciMoveParser.toUci`, the Chess960 castling branch, or move generation drifts. Move generation alone suffices: the search only *chooses* among generated moves and can never produce a string outside that image.
3. **Asset-swap detection** — the position counts, per-theme sizes, and candidate-list shape. Over a tracked, unchanging file these cannot fail on their own; their sole purpose is to go red if the file is *replaced* (a newer STS release, or the bare-FEN variant in the same upstream download). Honest bookkeeping, not a test of the engine.

None of the three starts the engine, so a fourth test does: a wiring proof over three positions at depth 2, asserting structure only — never which move or how many points. Without it, a wrong board handed to `toUci` or an inverted theme filter would surface only in the measurement run.

**Deliberately no score threshold and no `@Tag("slow")` suite run.** A floor can only be derived from a baseline, making it a snapshot of today's engine: every improvement loosens it, and if nobody raises it the test is green while guarding nothing — the exact failure mode that cost four `@Disabled` tests (see above). Reinforcing this, `pom.xml` sets no Surefire `<excludedGroups>`, so a `@Tag("slow")` test is paid on every `mvn test`. Regression protection stays with `bench` (node signature, seconds) and SPRT.

Aggregation keys on the **theme number**, not the theme name: theme 3 appears in the suite under two orderings of the same name (`Knight Outposts/Repositioning/Centralization` 85 times, `.../Centralization/Repositioning` once), and keying by name would split it into two rows.

### `ThreefoldRepetitionTest` (×4) — engine plays into the draw

```java
@Test
void testIsDraw() {
    String moves = "[[g2-g3 e7-e6 a2-a3 d8-h4 g3-h4 a7-a6 g1-f3 g8-f6 f3-g1 f6-g8 g1-f3 g8-f6 f3-g1]]";
    var game = new SimpleNotationImporter(moves).importGame();
    assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
    game.makeMove(MoveDescription.fromString("f6-g8", game.getTurn()));
    assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
}

@Test
void testFindDrawMove() throws Exception {
    /* same setup */
    var game = importer.importGame(new GameConfig(MyChessEngine.class, engineConfig()));
    MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
    assertEquals("f6-g8", ChessUtil.moveToString(move.move), "Unexpected move");
    assertEquals(0f, move.weight, "Weight must be 0 (draw)");
    assertEquals(GameResult.DRAW, move.result, "game must be draw due to threefold repetition rule");
}
```

Two sides of the same property: detection (the rules layer correctly transitions to `DRAW`) and *engine behavior* (the search actively chooses the repeating move when it's the best option). `testDisableThreefoldRepetition` then verifies the opt-out: with `enableThreefoldRepetition(false)`, the same sequence stays `ONGOING`.

`FiftyMovesRuleTest` follows the identical structure — detection + disable — for the 50-move rule, including verification that `halfMoveClock` reaches exactly 99 before and 100 after the triggering move.

### `BoardTest.testIsCheckmateN` (×3) — classic mate patterns

Three of the most famous quick-mate sequences in chess, each as a one-method test:

| Test | Pattern | Final move |
|---|---|---|
| `testIsCheckmate1` | Scholar's mate (queen + bishop on f7) | `Qxf7` |
| `testIsCheckmate2` | Fool's mate (white blunders, black mates on h4) | `Qh4` |
| `testIsCheckmate3` | Smothered mate (knight delivers mate on d6) | `Nd6#` |

Each asserts that mate is *not* detected before the final move and *is* detected after. They exercise the same `Board.isCheckmate(MoveGenerator)` path that PGN annotation (`#` suffix verification) uses.

### `QuiescenceSearchTest` (×3) — capture-chain extension

Tests the [quiescence search](search.md#64-quiescence-search) directly, bypassing the main alpha-beta. The harness:

1. Loads a position whose last move was a capture.
2. Asserts the material balance after the capture matches `expectedMaterialWeight`.
3. Runs `QuiescenceSearch.quiescenceSearch(...)` standalone.
4. Asserts the returned weight is in `[expectedWeightMin, expectedWeightMax]`.
5. Asserts the maximum reached search depth is at least `expectedMaximumReachedDepthMin` — proof the extension actually fired.

The two `testPositionWithUnguardedNight` tests are interesting: each sets up a scenario where the quiescence search *should* see an opportunity to recapture an unguarded knight. Both carry TODOs in the expected weight ranges (`// TODO: 0 < expectedWeight < 0.5 !`) — the engine currently does not exploit them perfectly, and the test documents the gap rather than masking it.

### `PositionEncodingTest.testMultiplePositions` — round-trip over 100+ positions

Replays the same 100-ply real game used in `ZobristHashingTest.testIncrementalUpdate`, but asserts a different invariant: after every move, `PositionEncoding.encode(board)` → `PositionEncoding.decode(...)` → `exportFEN()` produces the same FEN as the original `board.exportFEN()`. This locks down the binary serialization format used by the opening DB (see [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding)).

### `MoveGeneratorTest` — castling matrix

Four explicit cases:

- `testCastlingPossible1` — castling is legal; the engine must include `e1-g1` in the move list and accept the actual move.
- `testCastlingNotPossible1`/`2`/`3` — three subtly different reasons castling is illegal (king in check on start, transit, or destination; opposing bishop covering a transit square). All three assert that `e1-g1` is *not* in the generated moves and that attempting it throws `IllegalStateException`.

Plus two en-passant tests covering both colors, each verifying both the `enPassantField` state after the trigger move *and* the resulting capture move's presence in the generator's output.

### `WeightingFunctionTest` (×39) — evaluation component coverage

The single largest test file by method count. Each test pins down one component of the evaluation function for one specific position: a particular pawn structure produces a particular `doublePawnCount`, a particular development state produces a particular `openingState`, a king under attack produces a particular `chessCount`. Together they form the documented expected behavior for [Chapter 5](evaluation.md).

Test names are very specific (`testWhiteWithDoublePawn`, `testStartPositionOpeningWeight`, `testBlackCastlingState`, …), so failures point straight at the responsible component without needing to read the test body.

### `GameTest.testToShortNotation` — round-trip notation

A single test exercising the full chain `long algebraic input → MoveDescription → resolve → make move → render back to short algebraic → assertEquals to expected PGN form`. Walks a 60+ move game, asserting at every ply that the produced short notation matches the expected PGN string (including `+`, `#`, `0-0-0`, promotion suffixes, capture `x`, disambiguation columns/rows). Pinning the entire forward+inverse notation pipeline in one test.


---

## 11.3 Turning a lost game into a test

`BlunderTest` grew to 27 cases by pinning real defeats, first from cutechess matches and
now mostly from lichess. The route below is worth following exactly, because every step of
it exists because a shortcut went wrong once.

### Finding the case

[`tools/lichess-blunder-scan.py`](../tools/lichess-blunder-scan.py) downloads new games and
ranks the damage, writing [`test-results/lichess/blunders.md`](../test-results/lichess/blunders.md).
Two rankings, and the second is the one that matters:

- **Single-move blunders** — one move losing at least 300 cp.
- **Losing phases** — three consecutive own moves losing 250 cp together. This exists
  because the single-move filter misses the characteristic failure: a won game given away
  in three moves, no one of which crosses the threshold. In game NMc7sp8h neither `33.f3`
  (175 cp) nor `35.Rd3` (292 cp) would have been flagged, yet together they turn +1.89
  into a loss. Five of the six worst phases start from a *winning* position.

Pick by the **At start** column, not by the loss: a phase starting at +2.39 threw a win
away, one starting at −3.16 was already lost and says nothing about the mistake that lost
it. The **Test** column says whether a game is already covered — the scanner greps the test
sources for `lichess.org/<id>`, which is why every test names its game id in the JavaDoc.

### Working the case up

[`tools/probe-blunder.py`](../tools/probe-blunder.py) prints everything a test needs:

```sh
../lichess-bot/venv/bin/python tools/probe-blunder.py --game NMc7sp8h --move 33
```

FEN, material balance, Stockfish's best move and evaluations, myChess's choice at each
depth, and a suggested pin depth. Three of its outputs decide the shape of the test:

- **Material balance.** myChess reporting a healthy advantage while a pawn *behind* rules
  out the material-only eval shortcut as the explanation. That happened in NMc7sp8h
  (−100 cp while claiming +1.55) and changed the diagnosis from "material greed" to a
  genuine positional misjudgement.
- **Depth behavior.** A move abandoned at depth 10 is knowledge two plies out of reach; a
  move kept at every depth is a hole in the evaluation. Record which, in the JavaDoc — it
  is the difference between "more search will fix this" and "more search will not".
- **The UCI move.** Never derive it by hand. Deriving `Kxh2` as `h1h2` when the king stood
  on **g1** made a probe report "not reproduced" for a move myChess had in fact played, and
  the case was nearly dismissed.

### Writing the test

**Pin a depth, never a time budget.** Fixed depth is deterministic; a time budget makes the
outcome depend on machine speed and load, and the same test then passes on one machine and
fails on another. Use the depth the game actually reached — `probe-blunder.py` suggests the
lowest one that reproduces.

**Write the correct assertion first and confirm it goes red.** Then relax it to a
characterization that passes, and record the target assertion in a `TODO`. A test written
green from the start proves nothing: it may be green because it asserts the wrong thing.
Both halves are needed — the red run proves the defect is real and reproduced, the green
version keeps the suite usable while the defect is open.

**Assert the defect, not its incidental details.** A repetition test pinned the exact
sidestep `c1-b1`; the v4.4.0 tables changed it to `c1-d1` and the test failed although the
defect — not taking the free rook — was unchanged. `assertNotEquals` on the move that
*should* be played is the honest form there.

**Do not call a change an improvement without measuring how large.** When the PeSTO tables
made myChess play `Qc3` instead of the losing `12.h3`, that was reported as fixed. `Qc3` is
Stockfish's *second* choice at −0.8 against `d3` at +0.3 — the blunder was gone, optimal
play was not. Compare against the best move, not against the old move.

### What the JavaDoc should carry

Everything a reader needs to judge the case without re-running anything: the lichess link,
what was at stake, what was played, Stockfish's best move and both evaluations, **myChess's
own evaluation** (the gap is the finding — the extremes so far are +8.00 in a position mated
in four, and +1.79 where Stockfish reads −8.53), the depth behavior, and which family the
case belongs to.

Naming the family matters more than it sounds: a fourth case in a known family is stronger
evidence than a first case in a new one. Every test that characterizes engine behavior on a
theme therefore closes its JavaDoc with a line naming its family, which makes the
classification explicit and countable:

```java
 * <p><b>Test family:</b> king-safety (defect)
```

The family is the **topic**; the word in parentheses is what the test currently *does*:

| Status | Meaning |
|---|---|
| `defect` | Characterizes an open defect — it passes because the flaw is present, and must be inverted when the flaw goes (see [below](#when-the-defect-is-fixed)). |
| `fixed` | Was a `defect`, now asserts the correct behavior and guards the repair. |
| `guard` | Asserts correct behavior that was never broken, usually to mark the *limit* of a nearby defect. |

```sh
grep -hoE "Test family:</b> [a-z-]+ \([a-z]+\)" src/test/java/org/michaelfl/mychess/*.java \
  | sed 's/.*<\/b> //' | sort | uniq -c | sort -rn        # topic + status
grep -c "Test family:</b> king-safety (defect)" src/test/java/org/michaelfl/mychess/*.java
```

**What must NOT carry the marker: aggregate measurements.** `StsTest` and `EvalBenchmarkTest`
deliberately have no `Test family:` line, and neither should any future suite-level metric.
The marker classifies *per-position characterizations*, whose status can be `defect`, `fixed`,
or `guard`; a score over 70 or 1188 positions is none of the three. Adding the marker to
"complete" the taxonomy would corrupt the grep-based counts above, which are the whole reason
the marker exists. The `king-safety` family therefore now has two kinds of evidence that are
counted separately: 19 individual cases, and — outside the tally — the STS *King Activity*
theme score in [`sts-history.md`](sts-history.md). The depth-8 misses list printed by
`StsRunner` is the intended feed for new individual cases; it arrives in exactly the shape
[§ 11.3](#113-turning-a-lost-game-into-a-test) asks for (position, played move, best move).

The marker says **`Test family`, not `Blunder family`** — it was renamed once the first
family arrived whose tests are not blunders. The narrower word had already started to
mislead: it forced tests that assert myChess doing the *right* thing to stay unclassified
even when they belonged squarely to a theme, and two `BlunderTest` cases (`16...gxh4`,
`12.h3`) had flipped into real avoidance assertions while still carrying a label that called
them blunders. A family is a *topic*, and a topic outlives the defect that introduced it.

The status word exists because the topic alone cannot carry the evidence. Adding it
immediately corrected a claim this document had made in prose: not all 17 king-safety cases
are open defects — **four are already fixed**, so the number arguing for § 12.21 is 13.
Three families turned out to have no open case at all. A family count without the status is
a count of *interest* in a topic, which is not the same as a count of *evidence* against
the engine, and only one of the two belongs in a prioritization argument.

Deliberately **not** a custom JavaDoc tag: `@testFamily` would work for grep and for
the compiler, but IntelliJ flags every unknown block tag as "Wrong tag", and 25 warnings
are worse than a slightly less formal marker. A bold line in the body renders in the docs,
needs no IDE or `maven-javadoc-plugin` configuration, and parses just as well.

Nothing in the build reads it yet. The point is that the classification lives next to the
evidence rather than in a document that drifts, so the tally below can be *derived* rather
than maintained once there are enough cases to justify the tooling.

| Family | Open | Fixed | Guard | What it means |
|---|---:|---:|---:|---|
| `king-safety` | 19 | 4 | 1 | Danger to its own king is not charged for. Pawn pushes in front of it (`33.f3`, `20.h3`, `38...g6`), captures that drag it out (`Kxh3`, `Kxh2`), an attack on its file simply not scored (`23...Qd2`), a defender retreated to save it (`15...Ne8`), a pawn recaptured instead of trading off the attacking queen (`21.hxg4`), three cases from the **anchor bracket** (§ below) — a rook battery on the g-file read as +2.09 *for myChess* while it is −5.94 (`32...Ba4`), a pawn grabbed while its own king sits airy on b2 (`20.Bxf5`), and a king on f7 about to be hunted by the queen, scored +2.98 where the truth is −1.03 (`55...Bxd4`, the one case whose move does not reproduce, so it pins the score) — and a **pawn storm against the castled king priced at nothing** (`21...Qa3`: after it White is winning by ~3.6 and myChess reads +0.02 to +0.49 at every depth from 1 to 11 — the only case in the class where the losing move was the *opponent's*, kept because myChess shares the blind spot from both sides). The four fixed ones (`25.Rg7`, `16...gxh4`, `19...Nxe2`, `12.h3`) came with the v4.3.1 and v4.4.0 tables. The eighteenth open case is the family's extreme: from [keBKOXd1](https://lichess.org/keBKOXd1) myChess reads **+5.00 for itself while mated in six** — three black pieces around its king on an opened g-file, and `19.Nb6+` is Stockfish's own best move, so nothing about move choice is in question. **The family's first and only guard belongs to the same game** and is what makes the rest interpretable: after `23.gxf3` myChess finds `Rxh3+ 24.Kg2 Rh2#` from the black side, the mate its 2172-rated opponent missed. Search finds mates in this position class; the evaluation is what does not see the danger. Before that pairing the seventeen defects left the obvious counter-argument — "it just needs more depth" — unanswered. The nineteenth arrived by a different route than the rest: `EngineTest.dontCaptureWithKingPawn` had been **`@Disabled`** since long before this tally existed, with the note "re-enable once positional evaluation is tightened" — which nobody was going to notice. Re-measured on 2026-08-18 it was still red for exactly the stated reason, and Stockfish confirms the premise: `18...Bxf6` is −0.29, `18...gxf6` (what myChess plays) is −3.90, refuted by `19.Bg4 f5 20.Bh3 Ne4 21.Qh5` against the g-file it just opened. It is now a characterization pinning `g7-f6`, with a TODO to restore the original expectation. Tracked as [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo). |
| `corner-grab` | 3 | — | 2 | Material taken with a piece that then sits out of play: `9.Qe5`/`Qxh8`, `12.Qxb7`, `15...Nxa1`. Two guards: `21...Qxa1` (Philidor's Legacy) pins that the search *does* refute the grab by depth 13 — the defect is that a real clock never reaches it; `17.Kxb7` (Hamppe–Meitner 1872) pins the mate that punishes taking a bishop **with the king**, found at depth 8 in Stockfish's own line. |
| `material-only-shortcut` | 4 | — | 1 | The evaluation degenerates to a piece count once `materialDelta` leaves the ±200 cp band. All of `MaterialOnlyShortcutEvalTest`: a positional advantage erased (1), a material tie resolved by move ordering into the worst recapture (3), material preferred to a winning exchange sacrifice — `36.Qxb5` instead of `36.Rxf6` (4), and four consecutive positions of the Immortal Draw all graded at exactly +8.00 while Stockfish reads a forced 0.00 (5). The guard is case 2, marking where the blindness stops: material is the one dimension never discarded, so the right move is still found. Cases 4 and 5 come from real games; they live here rather than in `BlunderTest` — see below. Case 5 asserts the *property* (the score is an exact number of pawns) rather than a fixed value, which survives table changes that move the principal variation, and it asserts it four times because one whole number is coincidence-prone; case 4 still pins a number and should be moved to that form. |
| `repetition` | — | 3 | 3 | **Fixed 2026-08-14** ([§ 12.23](roadmap.md#1223-repetition-draws-are-invisible-to-the-search--done-2026-08-15--15-elo)): the search asked for three occurrences, declined at the second, and fell through to a table entry written before the repetition existed. `PositionSearch` now asks `Board.isTwofoldRepetition()`, deciding it from the search path. The two `fixed` cases are the warm-table block and the winning-position shuffle; the guards are the cold-table control, a toggle test that brings the shuffle back with detection off — without that one the no-repetition assertion could pass for an unrelated reason — and the Immortal Draw. The third `fixed` case replays a whole lichess game ([ImKwjaJy55DV](https://lichess.org/ImKwjaJy55DV)) in which 4.4.0 shuffled a +0.9 advantage into a draw: with a warm table it reproduces the game move for move, while 4.4.1 leaves the cycle at the first opportunity. Measured 2026-08-15 at **≈ +15** — SPRT H1 after 321 games, and an event count of 0 to 18 repetition draws from a won position (p = 7.6 × 10⁻⁶). |
| `endgame-technique` | 3 | 1 | — | Endgame-specific knowledge missing: not occupying a promotion square (`75.Ba1`), and a won knight endgame priced at +1 where Stockfish has +3.92 (`49.Kd4`, [82EFspXF](https://lichess.org/82EFspXF)). The second case is the sharpest evidence in the suite that the evaluation carries almost no weight where material is level — both sides hold knight and three pawns, so the material-only shortcut is not involved and the positional terms genuinely do run. Third open case from the anchor bracket: a nine-piece ending where `67...Rf4` lets the b-pawn run, −0.32 → −8.40, and the family's second passed-pawn failure — together they point at the value of a pawn about to promote, which no piece-square table expresses. Fixed: trading into a lost pawn endgame (`66.Nxe5`), which now scores below −0.9 where it once read −0.04. |
| `drawn-endgame-overvaluation` | 1 | — | — | The mirror image of `endgame-technique`: material the evaluation **cannot convert** is priced at face value. All of `DrawnEndgameEvalTest` — four positions the Syzygy tablebase proves drawn (three of rook+knight against rook, one of rook+bishop against rook) score **+3.53 to +3.71 pawns** on v4.4.2, and depth 12 moves that by at most 0.15, so no amount of search fixes it. The first fixture comes from [OcR3sqSx](https://lichess.org/OcR3sqSx), where myChess spent the entire fifty-move budget — 100 plies — trying to win a proven draw. **The reference is a proof, not an estimate**, which is unique in this suite: five pieces are exhaustively solved, so there is no reference uncertainty to argue about. Tracked as [roadmap § 12.24](roadmap.md#1224-endgame-scaling--material-advantages-that-cannot-be-converted--s--520-elo). |
| `tactical-oversight` | 1 | 2 | — | Walks into a concrete tactic: a pawn grab losing to a fork (`39.Rxd5`), a knight move abandoning the pawn it defended (`21.Nf3`). One **open** case from the anchor bracket: `25...Qc3` leaves the rook on d8 to `26.Qxd8`, turning 0.00 into −8.23 — and unlike the king-safety cases myChess is *not* optimistic about the position beforehand (−0.49 against Stockfish's 0.00), so here the defect looks like move selection rather than evaluation. |
| `unsound-attack` | — | 1 | — | Its own attack over-valued: the knight sacrifice `16.Ng6` rated +1.53. **No open case** — repaired, now guarding. |
| `search-horizon` | 5 | — | — | The one family whose cause is **reach, not knowledge**: myChess plays the move at depth 8 and abandons it two to four plies deeper, so the evidence points at roadmap §§ 12.1–12.6 (LMR, PVS, history ordering) rather than at the evaluation. All of `StsDefectTest`, and the first family sourced from a curated suite instead of from lost games — 1188 positions nobody chose with myChess in mind. Cases: a rook removed from the only square guarding c7, **mate in five**, 13.66 pawns, corrected at depth 11 (`Square Vacancy.099`); a queen stranded after grabbing a3, corrected at depth 12 — the deepest (`Square Vacancy.079`); `1.h3` allowing the `1...f3` break (`Center Control.071`); the `1.h4` undermining thrust declined (`Undermine.098`); and `1.c6` simply losing the pawn to `bxc6` (`King Activity.035`). Every case names its correction depth, which is what makes the label a measurement — and what will explain a flip. **These are more fragile than an evaluation characterization on purpose:** an evaluation case flips when its defect is fixed, a horizon case flips as soon as *any* search improvement sees more at depth 8. The family exists because ranking STS misses by centipawn loss selects horizon effects systematically — 18 of 18 classified so far, clustering at depth 9 — which is a finding about the measurement, not about the engine. See [`sts-history.md`](sts-history.md). |
| `pointless-exchange` | 2 | — | — | Exchanges valued by material alone, not by what they leave behind. Both from `StsDefectTest`, both kept at every depth from 8 to 11, so neither is a reach problem: `AT.098` trades the queens off from +0.84 into **−0.98** where the suite's `Qd7` holds +0.82, and `Offer of Simplification.090` hands the bishop over for the f6 knight at once (+2.12 → +0.22) when Stockfish's own line takes on f6 two moves later — the capture is right, the timing is not. myChess's own score *rises* from +47 to +66 cp across those depths in the second case, so it grows more confident the longer it looks. Backed by the aggregate: theme 15, *Avoid Pointless Exchange*, produced only **19 best moves of 73** in the v4.4.2 run, the lowest best-move count of any theme. A quiescence search driven by captures plus an evaluation with no term for keeping tension is biased toward exactly these moves. See [`sts-history.md`](sts-history.md). |
| `king-activity` | 1 | — | — | The king as a piece that wants to be *active*, not merely safe — distinct from `king-safety` above, and untouched by the shelved king-safety terms. `StsDefectTest.kingActivity085`: from +2.13 myChess plays the flank pawn `1.g4` instead of walking the king in with `1.Kf1` (+2.65) and the advantage is gone, **0.00**. Worth **1 of 100 points at every depth from 8 to 11** — one of the flattest trajectories in the scan. The case joins both findings of the STS theme table in one position: theme 11 scores 62.6 %, and the two weakest themes of the run are both flank-pawn advancement. It is the same misweighting the open `king-safety` cases show from the other side (`20.h3`, `33.f3`), which is why it is tracked with [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo) despite the separate family name. |
| `flank-pawn-advance` | 1 | — | — | A pawn advance on the side where the opponent's king sits is worth nothing to the evaluation, because no term expresses it. `StsDefectTest.akpc036`: the suite's `1.Qf6` prepares `g4-g5` and holds +1.05; myChess plays the neutral `1.Rd2` and +1.14 becomes **−0.09**. Worth **5 of 100 points at all four depths**, and the position contains no tactic, no capture, nothing a deeper search would stumble over. Backed by the strongest aggregate signal available: theme 8, *Advancement of f/g/h pawns*, is the **weakest of all fifteen at 59.5 %** with the joint-highest miss count, and theme 9 (a/b/c pawns) is second at 60.4 % — against 76.2 % for pawn play in the *center*, where the piece-square tables do carry a gradient. |
| `passed-pawn` | 1 | — | — | A pawn two squares from promotion priced as an ordinary pawn: the piece-square tables express where a pawn stands, not that it is about to become a queen. `StsDefectTest.squareVacancy062` is the largest verified evaluation loss in the scan at **4.27 pawns** — `1...Qf2+` looks forcing and loses to `2.Qxf2 Rxf2 3.c6 Nf8 4.c7`, read as **−3.41** against +1.49 for the suite's `Qc2`, and worth 14 of 100 points at every depth from 8 to 11. Same root as the two passed-pawn failures already noted under `endgame-technique` above, which is where a fix would be measured. |
| **total** | **41** | **11** | **7** | 59 markers across seven test classes. |

The tally spans **every** test class, not just `BlunderTest` — `repetition` for instance
draws one of its three cases from `ThreefoldRepetitionTest`.

**What the split is for.** The `Open` column is the evidence; `Fixed` and `Guard` are history
and boundary markers. Keeping them in the same family is deliberate — a repaired case is the
best possible regression test for the theme, and a `guard` says where a defect stops, which
is as much a part of understanding it as the defect itself. But only `Open` may be quoted in
a prioritization argument. Three families read very differently once split:
`tactical-oversight`, `unsound-attack` and — since 2026-08-14 — `repetition` have **no open
case at all**, so none of them is an argument for anything; they are repairs holding.
Conversely `king-safety` keeps 13 open cases across nine distinct game situations, which is
what makes § 12.21 the next evaluation theme rather than one more idea.

`repetition` also shows why `fixed` is worth recording separately from deleting the test.
Its four cases now pin a *mechanism* nobody would reconstruct from the production diff: that
detection has to be path-local, that the game rule must stay at three occurrences, and that
switching the check off brings the defect back. That last one is the reason the family kept
two guards rather than shrinking to the repairs alone.

**A case belongs with its mechanism, not with its provenance.** `BlunderTest` is where
real-game cases live, so a game reproduction lands there by default — but the
`material-only-shortcut` case went to `MaterialOnlyShortcutEvalTest` instead, as the fourth
of the four shapes that class already documents. Three reasons, and they generalize: the
class owns the explanation, so the case extends an argument instead of restating it; its
`deepEval` helper made the test three lines rather than a new scaffold; and it is not
`@Tag("slow")`, so the case runs in the fast suite instead of inside a 410-second class.
The deciding question is which file someone opens when they next chase this behavior — and
for a shortcut nobody looks among seventeen king-safety cases. The scanner's coverage
report is unaffected either way: it globs `src/test/java/**/*.java` for lichess ids, so a
test is found wherever it sits.

A second line, `Contributing:`, marks a mechanism that is a co-cause rather than the
primary one, so a case can be counted once and still be findable from both sides:

```java
 * <p><b>Test family:</b> corner-grab
 * <p><b>Contributing:</b> material-only-shortcut — a rook capture is a 500 cp swing…
```

Two cases carry it (`21...Qxa1` and `9.Qe5`), and the reason to keep it separate from the
family is that a co-cause must be *checked*, not assumed. Reviewing the four `BlunderTest`
cases that mentioned the material-only shortcut in prose found **two of the four claims
wrong**, both by the same confusion: `EVALUATE_MATERIAL_ONLY_THRESHOLD` applies to
`materialDelta`, the material swing **since the root** of the search, not to the balance at
it. So:

- A position that merely *stands* two pawns up enters the search at a delta of zero.
- A line that *keeps* a three-pawn lead holds the delta near zero throughout — the
  positional evaluation runs, the opposite of what the comment claimed.
- The comparison is a strict `>`, so a swing of exactly 200 cp does not trip it either.

Both corrections make the affected cases *cleaner* king-safety evidence: the evaluation
did run and still missed the danger. The cheap test for whether the shortcut is actually
engaged is the one the fourth case turns on — **a material-only score has to land on a
whole number of pawns**, material values being multiples of 100 cp. Note the direction:
that makes a whole score a reason to suspect the shortcut, not a proof of it, since the
positional evaluation can land on a whole number as well. Corroborate before concluding.
For case 4 the corroboration was `+6.27` one depth deeper, next to `+6.00` at three
consecutive depths; case 5 gets it from four whole numbers in a row.

A test gets no family when there is no theme to name. Most of the suite is in that
position — 854 test methods against 34 markers — because `IntArrayTest`, `LogTest` or
`PGNConverterTest` verify a unit, not a behavior anyone will investigate as a topic. Adding
a family there would be noise, not classification.

Guard rails are the borderline case, and the answer is that it depends on whether the theme
is the same one. `nf7_atMove25` and `qd5_atMove22` assert that myChess *finds* a mating
combination; `testIsDraw`, `testFindDrawMove`, `secondOccurrenceIsNotYetADraw` and
`testDisableThreefoldRepetition` assert that the repetition *rule* works as the rules of
chess require. Those stay unmarked, because the rule holding is not evidence about the
search that cannot see a repetition coming — different subject, same word.
`secondOccurrenceIsNotYetADraw` in particular exists to stop a fix from loosening the game
rule instead of tightening the search, which is the opposite concern from § 12.23. Contrast
`material-only-shortcut` case 2: it also asserts correct behavior, but about the very
mechanism the other three cases indict, and it is what establishes that material is the one
dimension the shortcut never discards. That belongs in the family; the row states its role.

The 13 open king-safety cases are the argument for the roadmap's ordering, and two of them —
`15...Ne8` and `21.hxg4` — additionally bound how *large* the term has to be: in both, correct
positional signal loses to a piece or a pawn of material, so a penalty worth a few dozen
centipawns would not change either decision. All three shelved attempts in § 12.21 were scaled
in exactly that range. The four `fixed` cases carry a second lesson worth keeping in view:
all four fell to *tables* (the v4.3.1 king endgame table, the v4.4.0 PeSTO tables) rather than
to a dedicated king-safety term. That is the cheaper mechanism, and it is the one to rule out
before building a new term. Note also which
families a king-safety term would *not* touch: `75.Ba1` is the one case where the
evaluation is roughly right (+0.52 — it knows the win is gone) while the move is never
generated at any depth, which points at search or move ordering instead.

### When the defect is fixed

The `TODO` says what to do: replace the characterization with the assertion it was written
from, do not merely relax it. Two of these have already flipped — `16...gxh4` and `12.h3`
both became real avoidance assertions when the v4.4.0 tables landed — and both kept a
`TODO` for the accuracy still missing.
