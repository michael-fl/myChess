# 11. Testing

The test suite is the executable specification for myChess: it pins down move generation, encoding, search behavior, evaluation ranges, notation parsing, and the rules for draws. It is also the way the engine's playing strength is regression-tested — most of `EngineTest` and `DeepWeightTest` are *position regressions*, where a particular game state must produce a particular move or score range.

| Metric | Value |
|---|---|
| Test classes | 82 `*Test.java` (+ 32 helpers and measurement drivers under the same source root, class count re-counted 2026-08-27, helper count 2026-08-30 — `ProbeVsEvalBenchmark`, `MatchStyleAnalysis`, `KingAttackUnits`, `KingAttackTexelData`, `TexelKingAttackTuner` added, see [roadmap § 12.26](roadmap.md)) |
| Test methods (`@Test` + `@ParameterizedTest`) | 1 311 — the 1 271 of the full run on 2026-08-27, plus two gate-boundary and five `containsIllegalMove` sentinel tests (2026-08-28), twenty anchor-bracket cases and ten `KingAttackUnits` tests (2026-08-29), two Chess960 king-safety cases and one standard-chess one (2026-08-30). Derived rather than re-measured, since a full run costs 17 minutes; the counts for the changed classes *are* measured — `WeightingFunctionTest` 58, `BlunderTest` 63. Parameterized cases counted per invocation |
| Currently passing | **all** — full run 2026-08-27: 1 271 run, 0 failures, 0 errors, **0 skipped** |
| Currently `@Disabled` | 0 — see [Retired disabled tests](#retired-disabled-tests) |
| Test source lines | ~28 260 |
| Framework | JUnit Jupiter 5.11 |
| Execution | `mvn test` (Maven Surefire 3.5.2), **17:13 min** for the full run on an M1 Pro (measured 2026-08-27). Was 11:07 min: v4.6.0 raised the depth-bound budget in `BlunderTest` from 120 s to 300 s, because `qxa1_atDepth13` no longer reached depth 13 inside the old one and fell back to a shallower, differently-decided iteration. Only cases that actually reach the cap pay for it |

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

The test classes cluster into seven groups. The per-group line counts below were taken when the suite was ~14 475 lines and have not been re-measured since; the group membership is accurate for the classes it names, and the numbers are indicative.

**The lists name 63 of the 81 test classes** (checked 2026-08-27). The 18 that are missing are not a random remainder: 10 of them are the Texel-tuning and dataset infrastructure (`TexelTunerTest`, the six `*TexelDataTest` adapters, `HybridDatasetBuilderTest`, `PgnQuietEpdExtractorTest`, `Chess960OpeningBookGeneratorTest`), which arrived as a cluster after these groups were written and arguably wants an eighth group of its own; the rest are evaluation (`BishopPairTest`, `NonPawnMaterialWeightTest`, `TaperedEvaluationTest`), search (`BoardNullMoveTest`, `DeepIterationRegressionTest`, `DisableSkipHeuristicExtensionTest`) and I/O (`PgnAnnotationImportTest`, `VersionTest`). Reproduce the check with:

```sh
for f in $(find src/test -name "*Test.java" | sed 's|.*/||; s|\.java$||' | sort); do
    grep -q "\`\(engines/\|openingdb/\)\?$f\`" docs/testing.md || echo "unnamed: $f"
done
```

| Group | Files (count) | Lines | What it covers |
|---|---|---|---|
| **Data structures & encoding** (13) | `BoardTest`, `ChessUtilTest`, `GameStatusTest`, `MoveTest`, `MoveDescriptionTest`, `PositionEncodingTest`, `PieceSquareTablesTest`, `SortableMovesBucketTest`, `BitOpsTest`, `IntArrayTest`, `CastlingSlotTest`, `BoardCastlingRookFilesTest`, `Chess960StartPositionsTest` | ~3750 | Bit-packing, board layout, color/turn bit invariants, sortable bucket sort order, piece-square table inversion, mailbox indexing, castling-slot / rook-file resolution, the 960 start-position table, notation parsing. |
| **Move generation & rules** (7) | `MoveGeneratorTest`, `GameTest`, `PerftTest`, `Chess960CastlingTest`, `MoveSorterImplTest`, `KillerMovesTest`, `SimpleNotationImporterTest` (`BoardTest` overlaps) | ~1510 | Pseudo-legal generation, castling legality (standard + 960), en passant, check / checkmate / stalemate detection, move ordering; **Perft node-count verification** against the Chess-Programming-Wiki reference values. |
| **Search & evaluation** (26) | `EngineTest`, `EngineSmokeTest`, `DeepWeightTest`, `WeightingFunctionTest`, `QuiescenceSearchTest`, `StaticExchangeEvaluationTest`, `PositionSearchTest`, `engines/SearchNodeContextTest`, `engines/IterationTimingsTest`, `MirrorEvalTest`, `HangingPiecesEvalTest`, `BlunderTest`, `ChessEngineTest`, `NextMoveTaskTest`, `EvalRegressionTest`, `IllegalPvRegressionTest`, `StalemateAvoidanceRegressionTest`, `MoveSortInvariantRegressionTest`, `ZobristHashingTest`, `PositionHashConsistencyRegressionTest`, `StsTest`, `StsDefectTest`, `ReportedScoreConsistencyTest`, `NodeCountTest`, `BenchResultTest`, `EvalBenchmarkTest` | ~5290 | Position regressions, eval component ranges, mirror-symmetry of eval, quiescence depth, static exchange evaluation (SEE) for quiescence capture ordering and SEE < 0 pruning, PV-legality regressions, iterative-deepening timings, async task lifecycle, and Zobrist-hash correctness (incremental vs from-scratch, en-passant round-trips, perft-style + randomized Chess960 consistency walks). The last three guard `bench` rather than the engine: that the search counts each visited position exactly once (`NodeCountTest`), that the aggregate arithmetic over a run is right (`BenchResultTest`), and that the suite still runs end to end (`EvalBenchmarkTest`). A miscount there would silently corrupt every comparison in [bench-history](bench-history.md). |
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

### `WeightingFunctionTest` (×58, re-counted 2026-08-28) — evaluation component coverage

The single largest test file by method count. Each test pins down one component of the evaluation function for one specific position: a particular pawn structure produces a particular `doublePawnCount`, a particular development state produces a particular `openingState`, a king under attack produces a particular `chessCount`. Together they form the documented expected behavior for [Chapter 5](evaluation.md).

Test names are very specific (`testWhiteWithDoublePawn`, `testStartPositionOpeningWeight`, `testBlackCastlingState`, …), so failures point straight at the responsible component without needing to read the test body.

Five of them cover the `containsIllegalMove` sentinel, which was uncovered until 2026-08-28. They
exist because of a near-miss rather than a failure: the sentinel looks redundant, since the search
detects the same condition twice more (`Moves.ILLEGAL` and the `canCaptureOpposingKing()` probe),
and removing it would have kept the bench signature bit-identical and the whole suite green. It is
*not* redundant for the Texel corpus builders, which call `analyzeFactors` directly — no move
generator, no quiescence — and drop a position on `isIllegalWeight`. The failure mode would have
been invisible corruption of every tuning corpus. Four of the five fail when the sentinel is
disabled; the fifth (`legalPosition_withTheSameMaterial_doesNotFireTheSentinel`) stays green on
purpose, guarding the other four from passing vacuously. See § 12.26 of the
[roadmap](roadmap.md).

**The gap was searched for elsewhere and is not there** (audited 2026-08-29). Two passes: every
method declared in `src/main` classified by whether any bench-reachable file calls it, and every
sentinel-value convention in the codebase (`ILLEGAL_WEIGHT_*`, `CHECKMATE_WEIGHT_*`,
`Moves.ILLEGAL`, `SearchNodeResult.INVALID`, `Board.illegal`/`empty`) traced to its consumers. Of
538 methods, 210 have no caller inside the search — but that column does **not** mean untested, and
reading it that way produced a false alarm on the entire UCI surface: `UciHandlerTest` is 893 lines
and 27 tests driven through `handleLine(...)`, so `handleGo`, `handlePosition` and their siblings
are exercised without ever being named. Testing through the public entry point is the intended
style, not a gap. On the sentinel side, `CHECKMATE_WEIGHT_*` has no consumer outside the search at
all, `SearchNodeResult.INVALID` is used only in `PositionSearch`, and every `Moves.isIllegal()` call
outside the search sits behind a tested entry point.

So the sentinel case is singular, and the reason names the category worth watching: its only
uncovered consumer was a **measurement driver living in test sources** — the Texel corpus builders,
which nothing tests by definition, because they *are* test-source code. Production behavior whose
sole non-search consumer is a driver under `src/test` is the blind spot; there is exactly one, and
it is now pinned.

### `GameTest.testToShortNotation` — round-trip notation

A single test exercising the full chain `long algebraic input → MoveDescription → resolve → make move → render back to short algebraic → assertEquals to expected PGN form`. Walks a 60+ move game, asserting at every ply that the produced short notation matches the expected PGN string (including `+`, `#`, `0-0-0`, promotion suffixes, capture `x`, disambiguation columns/rows). Pinning the entire forward+inverse notation pipeline in one test.


---

## 11.3 Turning a lost game into a test

`BlunderTest` grew to 63 cases by pinning real defeats, first from cutechess matches, then
mostly from lichess, and since 2026-08-29 from the anchor bracket as well. It costs about 9 min 40 s
on its own — the largest single item in the slow suite, and the reason the class carries
`@Tag("slow")`. The route below is worth following exactly, because every step of it exists
because a shortcut went wrong once.

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

### The second corpus — the anchor bracket

Lichess is not the only source of lost games. The absolute-Elo re-anchor plays **2000
engine-vs-engine games** against five externally rated opponents, and its PGNs record both
the result and what myChess thought of every move it played.
[`tools/scan-anchor-blunders.py`](../tools/scan-anchor-blunders.py) mines them:

```sh
../lichess-bot/venv/bin/python tools/scan-anchor-blunders.py --anchors TSCP,ZetaDva
```

It reuses `lichess-blunder-scan.py`'s `evaluate_move` primitive deliberately, so a loss
reported from either corpus means the same thing — **two corpora, one definition.** Same
two phases as well: a cheap scan at `--depth`, then a re-check of the survivors at
`--verify-depth`.

**Losses to the weaker anchors come first**, and the reason is not cost. A loss to an
engine of known *lower* rating almost always means something concrete went wrong; a loss
to a stronger one may simply be a stronger opponent, which is not a defect and not
actionable. The five anchors span 1609 to 2019, so the corpus sorts itself.

**The self-evaluation column is diagnosis, not detection** — the same separation § 11.3
draws for the lichess corpus, but here it comes for free because cutechess already logged
it. myChess's own score close to the truth means it saw the refutation one ply too late, a
horizon problem that depth can fix; its own score far above the truth means it never saw
it, and no depth will help. An earlier version of the tool used that self-contradiction as
the *detector* to avoid the engine cost: of its eight strongest candidates, four were noise
between 0.21 and 0.90 pawns once Stockfish was finally asked. A self-contradiction is a
place to look, not a finding.

#### The 2026-08-17 run, and what is still unread

Scanned on the v4.4.1 anchor PGNs, TSCP and Zeta Dva only — **151 of the corpus's 741
losses**. 101 candidates at or above 3.00 pawns at depth 15, of which **81 survived
verification at depth 20** (54 Zeta Dva, 27 TSCP). Raw data:
[`blunder-scan-findings.json`](../test-results/blunder-scan-findings.json), report in
[`blunder-scan-4.4.1-tscp-zetadva.txt`](../test-results/blunder-scan-4.4.1-tscp-zetadva.txt).

**Five findings became tests. The other 76 have never been worked up.** Applying this
section's own pick criterion — start from a position that was not yet lost — **58 of the 81
qualify** (28 from winning, 30 from level), and only three of those five adopted cases come
from that set. So roughly **55 verified, actionable defects are sitting unread**, which
makes this the largest known defect reserve in the project. Two examples of what is in
there, both from Zeta Dva and both unconsumed:

- move 32, `Rc7+`: from a **mate score** to −14.39, while myChess reported +5.00
- move 51, `Rd8`: +7.74 → −8.40, reported +3.00

The scale of the gap is the point. In **64 of the 81** findings myChess's own score sat at
least 3.00 pawns above the truth after the move — these are evaluation defects by the
criterion above, not horizon effects, and they are exactly the class that the search
cluster will *not* fix.

Three deliberate limits on that number, so it is not over-read. It is one version old
(v4.4.1; v4.6.0 has since changed which evaluation regime a subtree runs under, so
individual findings need re-probing before they are pinned). Three of the five anchors —
Princhess, BBC, Kojiro — have never been scanned at all, and being the *stronger* three
that is defensible rather than an omission. And a verified finding is a candidate, not yet
a test: it still has to go through *Working the case up* below, which is where a third of
the lichess candidates historically fall away.

#### The 2026-08-29 re-probe, and the second harvest

The first of those limits has since been removed by measurement.
[`tools/reprobe-anchor-findings.py`](../tools/reprobe-anchor-findings.py) ran the current
build over all 58 qualifying findings at depths 8, 10 and 12, one JSON object per line into
[`anchor-reprobe-4.6.0.jsonl`](../test-results/anchor-reprobe-4.6.0.jsonl):

| | |
|---|---|
| still reproduce | **37** (24 Zeta Dva, 13 TSCP) |
| no longer reproduce | **21** (14 Zeta Dva, 7 TSCP) |

**The step was not ceremony.** Pinning straight from the v4.4.1 list would have written a
red test for well over a third of the set. It also found a subtler trap: **8 of the 37 do
not reproduce at `SCANNER_DEPTH` at all**, only deeper — `39...d4` (10), `56...Nc5` and
`58.Rbb8` (10 and 12), `44...Ng2`, `60...Rc2`, `68...Kf2`, `44.Kg3` and `22.Bxc6+` (12
only). Pinned at the constant every one of them would have failed. The pin depth belongs to
the case, not to the suite.

Those eight depths were then checked against the run's own safety cap, since a search that
hits it returns the deepest iteration it finished rather than the depth asked for. **Seven of
the eight hit nothing** — their depths are real. The exception is `22.Bxc6+`, whose depth-10
search was truncated and reported "does not reproduce"; its depth-12 result ran to completion,
so the case is sound but nothing is known about depth 10 for it. Worth stating because the cap
does bite elsewhere: **18 of the 174 searches hit it**, 16 at depth 12 and 2 at depth 10. Those
two columns therefore mean "depth *n* or the deepest iteration finished within 60 s". Only the
depth-8 column is unqualified — its slowest search took 12.96 s — which is a second reason the
ten adopted cases are all pinned there.

A second pass, [`tools/refute-anchor-findings.py`](../tools/refute-anchor-findings.py),
asked Stockfish 18 at depth 22 for what the original scan never recorded — the move that
*holds*, and the refutation line after the move played — because every case comment quotes
both and the findings file has only the two evaluations. Result in
[`anchor-refutations.jsonl`](../test-results/anchor-refutations.jsonl). It also re-confirmed
the damage: **all 58 still measure 3.00 pawns or more**, none shrank, so the original
depth-20 verification holds under two more plies.

Twenty of them are now tests, in two batches.

*Second harvest* — five characterizations and five guards, eight from the Zeta Dva half that
had never been touched. The headline case is `32.Rc7+`: myChess holds a **forced mate**,
reports a routine +4.00, plays a check that loses, and ends at −12.78 — a swing above twenty
pawns in one move.

That case is also the clearest warning against trusting the scan's automatic
horizon-versus-evaluation label. By its criterion — own score far above the truth after the
move — it reads as an evaluation defect. It is not: six checks are available, `Re8+` mates in
9 and the other five lose to a forced mate against, so both the win and the punishment sit far
beyond any depth myChess reaches. With neither visible the engine falls back on material, and
the played check wins a bishop. The rule of thumb is a heuristic over two evaluations; it
cannot tell a mating position from a positional one, and every adopted case needs the check
its comment records.

*Third harvest* — ten more characterizations, **chosen for spread rather than for damage**.
Taking the ten largest remaining losses would have produced six variations on "grabbed
material and got punished", which pins one defect ten times. These ten cover seven families
across four endgames, five middlegames and one opening, five from each anchor, and they cost
15.8 s of search between them:

| | |
|---|---|
| `king-safety` | `29.Rxa5` (grabs a pawn into a mating attack), `33.Rae1` (opens its own king while winning +7.10) |
| `corner-grab` | `26.Qxb7` — the *same move* as the existing `12.Qxb7`, other game, other anchor — and `17...Qxh1+` |
| `passed-pawn` | `42.b3` (declines a forced promotion one square away), `47...Rd6` |
| `king-activity` | `48.h4` — ten pieces, where `48.Kf2` holds and one tempo later does not |
| `search-horizon` | `26...Qh1+`, the one case of the ten the original scan itself classified as horizon |
| `tactical-oversight` | `52.Re7+`, a check preferred to simply taking the rook |
| `pointless-exchange` | `13...Nxd4`, thirty pieces, the only opening case in the corpus |

Two of those choices earn their place by *pairing* rather than by size. `48.h4` is the
smallest position in the suite to carry a five-pawn error, so it is the cheapest available
probe of whether an endgame king term does anything. And `26...Qh1+` is kept deliberately as
a control: if a search change repairs it and leaves the others, that is evidence the scan's
horizon-versus-evaluation classification was sound.

That leaves **35 re-verified, classified candidates** uncovered — 19 characterizations, of
which 11 are pinnable at `SCANNER_DEPTH`, and 16 guards. (23 of the 58 are now covered: the
twenty above plus three of the original five; the other two of those five start from a lost
position and are excluded from the 58 in the first place.)

They are no longer a raw JSON file.
[`tools/report-anchor-blunders.py`](../tools/report-anchor-blunders.py) renders them as
[`anchor-blunders-4.6.0.md`](../test-results/anchor-blunders-4.6.0.md), the anchor
counterpart to `lichess/blunders.md` and with the same **Test** column, so an unconsumed case
is visible at a glance. Each row carries the verdict, the pin depth, the holding move and the
refutation line, which is exactly what *Working the case up* below otherwise has to
regenerate. **That readability gap is the reason the reserve sat for twelve days**: the
findings were verified the whole time, but choosing one meant re-running the tools, and the
lichess corpus — which has had its report from the start — never accumulated a comparable
backlog.

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

**What must NOT carry the marker: aggregate measurements, and constant-boundary guards.**
`StsTest` and `EvalBenchmarkTest` deliberately have no `Test family:` line, and neither should any
future suite-level metric. The same applies to
`MaterialOnlyShortcutEvalTest.theFullEvaluationStillRunsAtASwingOf200Centipawns` and
`theShortcutTakesOverAtASwingOf300Centipawns` (added 2026-08-28): they pin the two edges of
`EVALUATE_MATERIAL_ONLY_THRESHOLD` and are guards over a *design constant*, not evidence about an
evaluation weakness. Counting them in the `material-only-shortcut` family would inflate a number
that is meant to measure evaluation defects. They sit in that class because that is where someone
chasing this behavior looks — provenance follows the mechanism, per the rule further down.

Their absence had been measured rather than suspected: lowering the threshold from 200 to 100 left
the entire fast suite green, because all five family cases turn on piece captures worth 300 to
1000 cp and behave identically at either value. The family's characterizations are **one-sided
guards** — they detect the shortcut *ceasing* to fire, never it *starting* to fire somewhere new.
Both new tests were verified to fail when the constant moves, at 100 and at 300 respectively; a
boundary test that has not been shown to fail is not a guard.
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
immediately corrected a claim this document had made in prose: of the 17 king-safety cases
that existed then, not all were open defects — **four were already fixed**, so the number
arguing for § 12.21 was 13 rather than 17. (Those figures are the ones from that day; the
current counts are in the table above.)
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
| `king-safety` | 23 | 5 | 1 | Danger to its own king is not charged for. The founding pair is `22...Qb4`, three pawns up and blind to three attackers, and `strippedKing`, where the verdict is *exactly* the material balance. Then: pawn pushes in front of it (`33.f3`, `20.h3`, `38...g6`), captures that drag it out (`Kxh3`, `Kxh2`), a file opened in front of it to win a pawn (`13...fxg2`), a rook sacrifice invited (`14...g6`), a rook kept off the open file it should contest (`35.Rd3`), an attack on its file simply not scored (`23...Qd2`), a defender retreated to save it (`15...Ne8`), a pawn recaptured instead of trading off the attacking queen (`21.hxg4`), five cases from the **[anchor bracket](#the-second-corpus--the-anchor-bracket)** — a rook battery on the g-file read as +2.09 *for myChess* while it is −5.94 (`32...Ba4`), a pawn grabbed while its own king sits airy on b2 (`20.Bxf5`), a king on f7 about to be hunted by the queen, scored +2.98 where the truth is −1.03 (`55...Bxd4`, the one case whose move does not reproduce, so it pins the score), a pawn grabbed into a mating attack (`29.Rxa5`) and its own king opened while winning by +7.10 (`33.Rae1`) — and a **pawn storm against the castled king priced at nothing** (`21...Qa3`: after it White is winning by ~3.6 and myChess reads +0.02 to +0.49 at every depth from 1 to 11 — the only case in the class where the losing move was the *opponent's*, kept because myChess shares the blind spot from both sides). Four of the five fixed ones (`25.Rg7`, `16...gxh4`, `19...Nxe2`, `12.h3`) came with the v4.3.1 and v4.4.0 tables; the fifth is `captureOnF6` in `EngineTest`, described at the end of this row. One open case is the family's extreme: from [keBKOXd1](https://lichess.org/keBKOXd1) myChess reads **+5.00 for itself while mated in six** — three black pieces around its king on an opened g-file, and `19.Nb6+` is Stockfish's own best move, so nothing about move choice is in question. **The family's first and only guard belongs to the same game** and is what makes the rest interpretable: after `23.gxf3` myChess finds `Rxh3+ 24.Kg2 Rh2#` from the black side, the mate its 2172-rated opponent missed. Search finds mates in this position class; the evaluation is what does not see the danger. Before that pairing the defects left the obvious counter-argument — "it just needs more depth" — unanswered. One case arrived by a different route than the rest: `EngineTest.dontCaptureWithKingPawn` had been **`@Disabled`** since long before this tally existed, with the note "re-enable once positional evaluation is tightened" — which nobody was going to notice. Re-measured on 2026-08-18 it was still red for exactly the stated reason, and Stockfish confirms the premise: `18...Bxf6` is −0.29, `18...gxf6` (what myChess plays) is −3.90, refuted by `19.Bg4 f5 20.Bh3 Ne4 21.Qh5` against the g-file it just opened. It became a characterization pinning `g7-f6`, and **v4.6.0 closed it** — not through any king-safety work but through [§ 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460): both recaptures are captures, so the choice between them is now made with the full evaluation instead of a piece count, and myChess plays `e7-f6`. The window was tightened to ±0.5 exactly as that test's TODO prescribed. **Carry the lesson into § 12.21:** one of its then-open cases closed with no king-safety term written, so this family's count is evidence about the evaluation as a whole and not only about a missing king-safety component — quoting the raw count as the size of the king-safety hole overstates the case for that section. **The two newest cases are Chess960**, from [3RoDIOcC](https://lichess.org/3RoDIOcC), and they bound the planned term rather than argue for it: `castling960_atMove5` walks the king toward the wing white is storming at a moment when white has **zero** attack units, so no attacker-indexed term can score it at all, and `staticEval960_afterDxc6` is the class's only search-free case — `WeightingFunction.calculate` reads −194 cp where Stockfish 18's static NNUE reads +281. See [§ 11.3](#113-turning-a-lost-game-into-a-test). **A third case bounds the term from the other side** and is standard chess: in `qxd7_vsStudylovers` ([SINwv7q4](https://lichess.org/SINwv7q4)) black holds **ten** attack units on the white king and white none, which is as clear a signature as this family produces — and applying the fitted curve to all 36 legal moves still ranks `13.Qxd7` **first of 36**, exactly where the static evaluation had it and where Stockfish puts it fifteenth (depth 18). The term is not silent there; it points the wrong way, because the queen landing on d7 bears onto g7 and so counts as five attack units against the *black* king. Read the three together before drawing a conclusion from a neutral SPRT on § 12.21: a term that measures zero has not been tested against any of them. **No ordinals in this row on purpose:** counting cases in prose ("the eighteenth open case") is what let the number drift four behind the markers between 2026-08-18 and 2026-08-30. Tracked as [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo). |
| `corner-grab` | 5 | — | 2 | Material taken with a piece that then sits out of play: `9.Qe5`/`Qxh8`, `12.Qxb7`, `15...Nxa1`, and two from the anchor bracket that repeat the pattern with a queen — `26.Qxb7` takes the same poisoned pawn a second time in a different game, and `17...Qxh1+` collects the corner rook for +0.33 → −3.24 and never comes back. Two guards: `21...Qxa1` (Philidor's Legacy) pins that the search *does* refute the grab by depth 13 — the defect is that a real clock never reaches it; `17.Kxb7` (Hamppe–Meitner 1872) pins the mate that punishes taking a bishop **with the king**, found at depth 8 in Stockfish's own line. |
| `material-only-shortcut` | 2 | 3 | — | The evaluation degenerates to a piece count once `materialDelta` leaves the ±200 cp band. All of `MaterialOnlyShortcutEvalTest`. **Three of the five closed in v4.6.0** ([§ 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)), which disables the shortcut in the subtrees of *capturing* root moves: the erased positional advantage now reads 1.93 against Stockfish's ~1.6 (1), the trapped-knight surplus 1.27 of ~2.3 — about half arrives (2), and the material tie that let move ordering pick the worst recapture is broken on merit, so `Nxb3` replaces `axb3` at 2.52 instead of 1.0 (3). Case 2 was the family's only `guard` and is now `fixed`: its guard half still holds — material was always the dimension the shortcut kept, so the move was found even while the score was blind — but the blindness it marked the limit of is gone. **The split is the sharpest confirmation of the change that exists, and nobody designed it:** case 5's four root moves (`Ne7`, `Kf8`, `b6+`, `Ba6+`) are the only quiet ones in the class, so the shortcut still covers them and all four still score exactly +8.00 while Stockfish reads a forced 0.00. Four capture subtrees changed, one quiet subtree did not. **Case 4 stays open and refutes its own analysis:** the shortcut no longer covers that subtree — the score moved from exactly 6.00 to 6.21, so the positional evaluation runs — and myChess still plays `36.Qxb5` instead of the piece-winning `36.Rxf6`. The blunder was therefore not caused by the shortcut, or not by it alone; the analysis in the class comment is kept because its measurements hold, but its conclusion about that position does not. Its assertion moved from a pinned 6.00 to the property "no longer a whole number of pawns", which is the form case 5 already used and which the class comment had asked for. |
| `repetition` | — | 3 | 3 | **Fixed 2026-08-14** ([§ 12.23](roadmap.md#1223-repetition-draws-are-invisible-to-the-search--done-2026-08-15--15-elo)): the search asked for three occurrences, declined at the second, and fell through to a table entry written before the repetition existed. `PositionSearch` now asks `Board.isTwofoldRepetition()`, deciding it from the search path. The two `fixed` cases are the warm-table block and the winning-position shuffle; the guards are the cold-table control, a toggle test that brings the shuffle back with detection off — without that one the no-repetition assertion could pass for an unrelated reason — and the Immortal Draw. The third `fixed` case replays a whole lichess game ([ImKwjaJy55DV](https://lichess.org/ImKwjaJy55DV)) in which 4.4.0 shuffled a +0.9 advantage into a draw: with a warm table it reproduces the game move for move, while 4.4.1 leaves the cycle at the first opportunity. Measured 2026-08-15 at **≈ +15** — SPRT H1 after 321 games, and an event count of 0 to 18 repetition draws from a won position (p = 7.6 × 10⁻⁶). **The open case arrived from the opposite direction, on 2026-08-20**, and it is the reason this family is no longer closed: the v4.5.0 complete-PV work made the reported score honest enough to expose that the fix does not cover a table *carried across rule sets*. The repetition check is path-local by design and deliberately not stored, so a table hit of sufficient depth returns a score without visiting the children — and on lines where it hits before the repetition has accumulated on the path, the search never reaches the check and inherits the other rule set's score. In the won position it then settles for a draw, reporting **0.00 for `c1-d1`** on the repeating line `c1-d1 c2-d2 d1-c1 d2-c2`. **The Open column reads `—` even so, and that is not an oversight:** the case is characterized inside `ThreefoldRepetitionTest.withRepetitionDetectionDisabledTheShuffleReturns`, which carries a `guard` marker because its first phase is the toggle control, so no `defect` marker exists to count. The tally is derived from markers and one test can only carry one; where the prose and the number disagree, the number is what a `grep` returns. That test's second phase asserts `GameResult.DRAW`; its cold-table counterpart stays green and keeps +15.05, which is what makes the case attributable to the poisoned table rather than to the position. **It cannot occur in play** — both rules default to on, and the one place in `src/main` that turns them off (`PGNImporter.importGame`) uses a table of its own. **Decided won't-fix on 2026-08-23**, and this is the family's one open case that is deliberately not going anywhere. The repair would be to make the rule set part of the table identity — XOR a constant derived from the two rule flags into the key at the two access points in `PositionSearch`, two XOR and provably neutral — but it would be production code exercised only by this test, and the v4.5.0 episode is the argument against that trade: two defects equally real in the code and equally absent in practice cost **−44.4** and **−166** Elo to repair ([§ 12.25](roadmap.md#1225-tried--repairing-the-roots-move-choice-after-the-pv-re-search-reverted-twice-444-and-166-elo)). A defect with zero occurrences does not earn a change to the search. What would reopen it: a caller handing `importGame(GameConfig)` a shared table with a rule switched off, or a second config-dependent rule in the early-exit condition of `alphaBetaSearchPre`, which already tests two flags — so this is a class of hazard rather than one instance. The assertion stays as it is meanwhile, pinning the behavior so a future change to the table cannot alter it unnoticed. |
| `endgame-technique` | 5 | 3 | — | Endgame-specific knowledge missing: not occupying a promotion square (`75.Ba1`), and a won knight endgame priced at +1 where Stockfish has +3.92 (`49.Kd4`, [82EFspXF](https://lichess.org/82EFspXF)). The second case is the sharpest evidence in the suite that the evaluation carries almost no weight where material is level — both sides hold knight and three pawns, so the material-only shortcut is not involved and the positional terms genuinely do run. Three further open cases come from the anchor bracket: a nine-piece ending where `67...Rf4` lets the b-pawn run, −0.32 → −8.40, and the family's second passed-pawn failure — together they point at the value of a pawn about to promote, which no piece-square table expresses — plus `d3` pushed before the rook was secured and a promotion walked straight into a capture (`c1=Q`). Fixed: trading into a lost pawn endgame (`66.Nxe5`), which now scores below −0.9 where it once read −0.04; the winning check `Bc3`, now found; and `R6xa7`, no longer taken with the wrong rook. |
| `drawn-endgame-overvaluation` | 1 | — | — | The mirror image of `endgame-technique`: material the evaluation **cannot convert** is priced at face value. All of `DrawnEndgameEvalTest` — four positions the Syzygy tablebase proves drawn (three of rook+knight against rook, one of rook+bishop against rook) score **+3.53 to +3.71 pawns** on v4.4.2, and depth 12 moves that by at most 0.15, so no amount of search fixes it. The first fixture comes from [OcR3sqSx](https://lichess.org/OcR3sqSx), where myChess spent the entire fifty-move budget — 100 plies — trying to win a proven draw. **The reference is a proof, not an estimate**, which is unique in this suite: five pieces are exhaustively solved, so there is no reference uncertainty to argue about. Tracked as [roadmap § 12.24](roadmap.md#1224-endgame-scaling--material-advantages-that-cannot-be-converted--s--520-elo). |
| `tactical-oversight` | 4 | 5 | — | Walks into a concrete tactic. Fixed: a pawn grab losing to a fork (`39.Rxd5`), a knight move abandoning the pawn it defended (`21.Nf3`), and three from the anchor bracket that the search now handles — `Rd8` no longer walks into mate, `Rf3` no longer releases the win, `Bc2` no longer collapses a level position. Four **open** cases, all from the anchor bracket: `25...Qc3` leaves the rook on d8 to `26.Qxd8`, turning 0.00 into −8.23; `Ne4` retreats where it could fork; `Re7` moves the only defender of c2; and `Re7+` prefers a check to taking the rook. In none of them is myChess optimistic about the position beforehand the way the king-safety cases are (`Qc3`: −0.49 against Stockfish's 0.00), so here the defect looks like move selection rather than evaluation — which is what separates this family from `king-safety` and puts its fixes in reach of the search rather than of a new term. |
| `unsound-attack` | — | 1 | — | Its own attack over-valued: the knight sacrifice `16.Ng6` rated +1.53. **No open case** — repaired, now guarding. |
| `king-safety` — **depth-stability check, all 19 cases as they stood on 2026-08-18/19** | | | | A snapshot, not a running total: the family has 23 open cases today, and the ones added since that date were not part of this check. Every case was re-measured to ask whether the defect survives deeper search, because a characterization the search fixes on its own argues for [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo) far less than one it does not. Thirteen pin a *move* and were re-searched at depths 8-12; six pin an *evaluation* and were checked against Stockfish 18 at depth 24 instead. Raw data: [`kingsafety-depth-stability-4.4.2.jsonl`](../test-results/kingsafety-depth-stability-4.4.2.jsonl) and [`kingsafety-score-stability-4.4.2.jsonl`](../test-results/kingsafety-score-stability-4.4.2.jsonl). **7 are depth-stable evaluation defects**: `qd2`, `hxg4`, `captureOnF6` (which keeps `g7-f6` at all five depths — it was mis-sorted as score-pinning at first, since it asserts through `testPosition` rather than `assertEngineAvoids`), plus `qb4`, `bxd4` and the two already documented, `keBKOXd1` and `21...Qa3`. `qb4` and `bxd4` are the family's strongest single pieces of evidence: at depth 12 myChess still reads −0.45 where the truth is **−8.10**, and exactly 0.00 where it is **−3.00**, with no convergence over five depths. **5 oscillate** — right at one depth, wrong at the next, right again (`f3`, `fxg2`, `rd3`, `g6_38`, `h3`; `20.h3` is right at depth 11 and wrong again at 12). That is the load-bearing observation: a sound evaluation converges with depth instead of flip-flopping, so oscillation means two moves sit within noise of each other and the winner depends on which depth the clock allowed — deeper search does not repair it, it reaches the coin-flip sooner. **6 converge** and are what the search cluster would fix as a side effect (`bxf5`, `kxh2`, `g6_14` from depth 9; `ba4`, `kxh3`, `ne8` from depth 11). **1 has a questionable premise**: `strippedKing` claims the shelter is worth ~0 cp and that black holds a perpetual, but Stockfish reads +1.55 and myChess +1.71 to +1.95 against material of +2.20 — a real but small underweighting, ~0.3 pawns against Stockfish's 0.65. It keeps its marker so the test is not orphaned; read the count as **12 of 19 that deeper search would not fix, of which one is thin**. The whole check is relative to *today's* search and must be repeated after LMR/PVS: king danger matures beyond whatever horizon exists, so the six converging cases may be replaced by a fresh set at the new depth. |
| `search-horizon` | 7 | — | — | The one family whose cause is **reach, not knowledge**: myChess plays the move at depth 8 and abandons it two to four plies deeper, so the evidence points at roadmap §§ 12.1–12.6 (LMR, PVS, history ordering) rather than at the evaluation. Five of the seven are `StsDefectTest`, and it was the first family sourced from a curated suite instead of from lost games — 1188 positions nobody chose with myChess in mind. Cases: a rook removed from the only square guarding c7, **mate in five**, 13.66 pawns, corrected at depth 11 (`Square Vacancy.099`); a queen stranded after grabbing a3, corrected at depth 12 — the deepest (`Square Vacancy.079`); `1.h3` allowing the `1...f3` break (`Center Control.071`); the `1.h4` undermining thrust declined (`Undermine.098`); and `1.c6` simply losing the pawn to `bxc6` (`King Activity.035`). The other two come from the anchor bracket and are the family's only lost-game entries: `32.Rc7+` abandons a forced mate (reclassified here from `tactical-oversight` once Stockfish showed the refutation sits far deeper than the original note claimed), and `Qh1` plays the check that ends its own attack. Every case names its correction depth, which is what makes the label a measurement — and what will explain a flip. **These are more fragile than an evaluation characterization on purpose:** an evaluation case flips when its defect is fixed, a horizon case flips as soon as *any* search improvement sees more at depth 8. The family exists because ranking STS misses by centipawn loss selects horizon effects systematically — 18 of 18 classified so far, clustering at depth 9 — which is a finding about the measurement, not about the engine. See [`sts-history.md`](sts-history.md). |
| `pointless-exchange` | 4 | — | — | Exchanges valued by material alone, not by what they leave behind. Three are from `StsDefectTest` and kept at every depth from 8 to 11, so none of them is a reach problem: `AT.098` trades the queens off from +0.84 into **−0.98** where the suite's `Qd7` holds +0.82, and `Offer of Simplification.090` hands the bishop over for the f6 knight at once (+2.12 → +0.22) when Stockfish's own line takes on f6 two moves later — the capture is right, the timing is not. myChess's own score *rises* from +47 to +66 cp across those depths in the second case, so it grows more confident the longer it looks. `AT.063` gives the queen away on f3 for a materially equal trade. The fourth is the family's only lost-game entry, `Nxd4` from the anchor bracket, which trades away the initiative. Backed by the aggregate, and this one survived the check: theme 15, *Avoid Pointless Exchange*, produced only **19 best moves of 73** in the v4.4.2 run — the lowest best-move count of any theme — and the depth-10 run confirms it is evaluation-limited at 18 % of headroom captured. A quiescence search driven by captures plus an evaluation with no term for keeping tension is biased toward exactly these moves. See [`sts-history.md`](sts-history.md). |
| `king-activity` | 3 | — | — | The king as a piece that wants to be *active*, not merely safe — distinct from `king-safety` above, and untouched by the shelved king-safety terms. `StsDefectTest.kingActivity085`: from +2.13 myChess plays the flank pawn `1.g4` instead of walking the king in with `1.Kf1` (+2.65) and the advantage is gone, **0.00**. Worth **1 of 100 points at every depth from 8 to 11** — one of the flattest trajectories in the scan. `kingActivity005` is the same idea one move earlier: a knight sortie into `Bxe4` where the king wanted to step up. The third case is the family's only lost-game entry, `48.h4` from the anchor bracket, where the king stands idle in a ten-piece ending — which matters because it shows the family is not an artifact of the STS position set. The `085` case joins both findings of the STS theme table in one position: theme 11 scores 62.6 %, and the two weakest themes of the run are both flank-pawn advancement. It resembles the open `king-safety` cases (`20.h3`, `33.f3`), but that is a resemblance between positions, not shared evidence, and it is **not** tracked with § 12.21: that section is about king *safety* — the king under attack — while this family is about the king as an *active* piece. The STS has no king-safety theme at all, so nothing in the suite argues for or against § 12.21. Theme 11 itself turned out **depth**-limited (24 % of headroom captured at depth 10), so the theme score does not back this family either; the two cases rest on their own flat trajectories. |
| `flank-pawn-advance` | 1 | — | — | A pawn advance on the side where the opponent's king sits is worth nothing to the evaluation, because no term expresses it. `StsDefectTest.akpc036`: the suite's `1.Qf6` prepares `g4-g5` and holds +1.05; myChess plays the neutral `1.Rd2` and +1.14 becomes **−0.09**. Worth **5 of 100 points at all four depths**, and the position contains no tactic, no capture, nothing a deeper search would stumble over. **Its theme does not back it**, and the correction is instructive. Theme 8 is the weakest of all fifteen at depth 8 (59.5 %), which is why the case was picked — but the depth-10 run showed the theme captures 21 % of its remaining headroom from two extra plies and is therefore *depth*-limited (see [`sts-history.md`](sts-history.md)). Its low score means myChess cannot calculate those positions out, not that the concept is missing. This one position is the exception within it, flat at 5 points across four depths. The same holds for theme 9 (18 %). The neat story — flanks weak because the piece-square tables carry no gradient there, center strong at 76.2 % because they do — did not survive measurement: *Pawn Play in the Center* turned out to be the evaluation-limited one, at 7 %. |
| `passed-pawn` | 3 | — | — | A pawn two squares from promotion priced as an ordinary pawn: the piece-square tables express where a pawn stands, not that it is about to become a queen. `StsDefectTest.squareVacancy062` is the largest verified evaluation loss in the scan at **4.27 pawns** — `1...Qf2+` looks forcing and loses to `2.Qxf2 Rxf2 3.c6 Nf8 4.c7`, read as **−3.41** against +1.49 for the suite's `Qc2`, and worth 14 of 100 points at every depth from 8 to 11. The other two are lost-game entries from the anchor bracket and show the same thing from the winning side: `Rd6` does not run the pawns while winning, and `b3` leaves the d6 pawn unqueened. Same root as the two passed-pawn failures already noted under `endgame-technique` above, which is where a fix would be measured. |
| `rook-activation` | 4 | — | — | **The deepest evaluation family the suite produced**, and the one whose four cases make it a property of the evaluation rather than a quirk. In each, the better move activates a rook — to the seventh rank or onto an open file — and myChess plays a quiet piece move or a trade instead: `1.0-0` rather than `Rc7` (`7th Rank.078`, +1.58 → 0.00 because black takes the c-file first), `1.Rxf8+` rather than `Rf7` (`7th Rank.090`, a check that trades the advantage away), `1...d6` rather than `Rh8` onto the file the white king sits on (`Open Files.041`, +2.61 → 0.00), and a knight sortie losing the g5 bishop where the rook belonged on e5 (`Center Control.001`, worth 1 of 100 points at all four depths). The mechanism is nameable: mobility rewards the squares a rook can *see* and the piece-square tables reward centralization, but nothing prices a rook **on** the seventh or the tempo of seizing a file, because both pay off past the horizon. All four are in `StsDefectTest`, all kept at every depth from 8 to 11. |
| `pawn-thrust` | 2 | — | — | A pawn advance that breaks or undermines an enemy pawn chain, declined for a quiet piece move. `Undermine.023`: `1.b4!` undermines the a5/b6 pair in front of the black king for +1.39; myChess plays `1.Qc2` and `1...c5` levels the game. `Undermine.073`: from a **won** +3.65, `1.d5!` cracks the c6/e6 chain open, and `1.Ne5` instead leaves only +1.35. Related to `flank-pawn-advance` above and probably the same missing term seen on a different wing — a fix should be measured against both families at once. |
| `defensive-resource` | 1 | — | — | A position that holds only through a forcing continuation, scored as if a quiet move were available. `Square Vacancy.019` is the second-largest verified evaluation loss in the scan at **3.88 pawns**: Stockfish reads it as exactly balanced, but only because `1.Qf8` keeps checking, and myChess plays the quiet `1.Rg2` into −4.44. The trajectory 19 → 0 → 19 → 19 over depths 8 to 11 shows depth 9 preferring something worse still. **Provisional — one case.** Treat it as a hypothesis until a second position lands in it. |
| **total** | **65** | **20** | **6** | 91 markers across six test classes, re-derived 2026-08-30. A `grep` for `Test family:` returns 93 hits in seven files: `FiftyMovesRuleTest` mentions the marker twice in prose to explain why it deliberately carries none. **The previous figures here were 48 / 15 / 6 and had been wrong for some time** — sixteen `defect` and five `fixed` markers were added without the table following. The counting command above was not at fault; it returns the right numbers and always did. The lesson is the one the `king-safety` row now states: re-derive this table from the markers when touching it, and keep counted nouns out of the prose so that a stale row is visibly stale instead of quietly wrong. |

The tally spans **every** test class, not just `BlunderTest` — `repetition` for instance
draws its seven cases from `ThreefoldRepetitionTest` and `BlunderTest`.

**What the split is for.** The `Open` column is the evidence; `Fixed` and `Guard` are history
and boundary markers. Keeping them in the same family is deliberate — a repaired case is the
best possible regression test for the theme, and a `guard` says where a defect stops, which
is as much a part of understanding it as the defect itself. But only `Open` may be quoted in
a prioritization argument. Two families read very differently once split:
`tactical-oversight` and `unsound-attack` have **no open case at all**, so neither is an
argument for anything; they are repairs holding. `material-only-shortcut` is the newest
illustration of why the split matters: it read 4 open before v4.6.0 and reads 2 now, so quoting
its old count would overstate the case for further work on the shortcut by a factor of two. `repetition` was the third until
2026-08-20, and its return to one open case is worth reading as a caution about this very
column: the family was closed on the strength of a mechanism fix that turned out to cover
one rule set and not a table carried between two. A family with no open case means nobody
has found the next one yet, not that none exists.
Conversely `king-safety` keeps 22 open cases across many distinct game situations, which is
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
for a shortcut nobody looks among twenty-three king-safety cases. The scanner's coverage
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

The 23 open king-safety cases are the argument for the roadmap's ordering, and two of them —
`15...Ne8` and `21.hxg4` — additionally bound how *large* the term has to be: in both, correct
positional signal loses to a piece or a pawn of material, so a penalty worth a few dozen
centipawns would not change either decision. All three shelved attempts in § 12.21 were scaled
in exactly that range.

The two newest, both **Chess960** and both from [3RoDIOcC](https://lichess.org/3RoDIOcC), bound
the term from the other side — they say where it does *not* reach.
`castling960_atMove5_characterizesChoosingTheStormedWing` is the sharper of the two: myChess
castles toward the wing white is storming, and at that moment white has **zero** attack units on
the black king. Every variant of the planned `KING_ATTACK_PENALTY`, ours and the Audax fork's,
indexes on attackers already bearing on the zone, so all of them score that position at 0 and
leave the move exactly as attractive as it is today. Recording it before the term is built is
the point: it is the difference between a term that underperforms and a term that was never
going to cover the case. Its sibling
`staticEval960_afterDxc6_characterizesBlindnessToFiveAttackers` is the one search-free case in
the class — `WeightingFunction.calculate` reads −194 cp where Stockfish 18's *static* NNUE
evaluation reads +281 and its search mates in 19, with five attack units on the black king — so
it needs no argument at all about horizon versus evaluation. The five `fixed` cases carry a second lesson worth keeping in view:
they fell to *tables* (the v4.3.1 king endgame table, the v4.4.0 PeSTO tables) rather than
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
