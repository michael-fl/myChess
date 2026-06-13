# 12. Roadmap: Improving Playing Strength

This chapter lists the missing ingredients that would move myChess closest to a competitive classical engine, in rough order of expected return on effort. Each entry quotes a ballpark Elo estimate, an effort tag (S / M / L), and points to the place in the existing code where it would land.

The numbers are *order-of-magnitude*, not measurements — actual gains depend on tuning, the search depth they are measured at, and interaction with other components. They are drawn from the public chess-programming literature ([CPW](https://www.chessprogramming.org/)) and from typical engines of comparable scope. Wherever a feature only helps when paired with another, that pairing is noted.

The README's [§ 1.2 *Scope and status*](../README.md#12-scope-and-status) already names the absent items at the level of "what the engine does not do (yet)". This chapter is its actionable counterpart.

---

## 12.1 Transposition table — **S → M, ≈ 150–300 Elo**

The single biggest missing optimization, and the one the README already flags. A transposition table (TT) caches per-position search results keyed by Zobrist hash, so positions reached through different move orders are evaluated once.

- The hash already exists ([`Board.calculatePositionKey()`](../src/main/java/org/michaelfl/mychess/Board.java), [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding)).
- Each TT entry stores `{key, depth, score, bestMove, bound}` where `bound ∈ {EXACT, LOWER, UPPER}`. A fixed-size open-addressed array with depth-preferred or always-replace policy is enough.
- The TT also feeds [§ 7.1 best-known-move ordering](search.md#71-best-known-move-pv-ordering): on a TT hit, try the stored `bestMove` first — strictly more informed than the previous-iteration PV alone.
- Wire-in points: [`PositionSearch.calculateNextMove`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) (probe at node entry, store on exit) and `MoveSorterImpl.reset(...)` (accept a TT move).

Caveats: must clear or use a generation counter between games; mate-score adjustment by ply on store/probe; not safe in a parallel search (out of scope here).

## 12.2 Null-move pruning — **S, ≈ 50–100 Elo**

Pass the turn to the opponent at depth ≥ 3 and search the reply with reduced depth `R = 2 or 3`. If the result still exceeds beta, the original position is so good for the side to move that a real move can only confirm it — return beta.

- One conditional branch inside the recursive node, plus a `Board.switchTurn()` / restore pair (no piece is moved). The `GameStatus` stack already supports a turn flip via [`GameStatus.switchTurn()`](../src/main/java/org/michaelfl/mychess/GameStatus.java).
- Disable when the side to move is in check or has only pawns + king (avoid zugzwang). The existing `isEndGame()` heuristic is too crude — gate on actual non-pawn material instead.
- Pairs naturally with [§ 12.1 TT](#121-transposition-table--s--m--150300-elo): TT cutoffs from the reduced-depth search return immediately.

## 12.3 Late move reductions (LMR) — **S, ≈ 50–100 Elo**

After the first few moves at a node (those that have already passed [§ 7.1 PV / 7.2 killer ordering](search.md#71-best-known-move-pv-ordering)), reduce the search depth by 1–2 for quiet moves. If the reduced search beats alpha, re-search at full depth.

- Adds two integer comparisons in the move loop in `calculateNextMoveSub`. Disable on captures, promotions, and check-givers.
- Synergises strongly with TT and a [§ 12.5 history heuristic](#125-history-heuristic--s--3050-elo): both make the *first few* moves much more likely to be best, which is exactly the precondition for LMR's gamble to pay off.

## 12.4 Check extensions — **S, ≈ 15–30 Elo**

When the side to move is in check, increment search depth by 1 instead of decrementing. Cheap, reliable, hard to get wrong.

- Already detectable: [`Board.isKingChecked(moveGenerator)`](../src/main/java/org/michaelfl/mychess/Board.java) returns a boolean.
- Watch the *extension budget* — uncontrolled extensions can blow up depth on long forced lines. A common cap is "total extensions per path ≤ ply at root".

## 12.5 History heuristic — **S, ≈ 30–50 Elo**

A `int[2][64][64]` table indexed by `(color, fromField, toField)` is incremented (typically by `depth²`) whenever a quiet move causes a beta cutoff. The move sorter uses these counts as the weight key for quiet moves in [`bucketRemainingMoves`](../src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java).

- Complements [§ 7.2 killer moves](search.md#72-killer-moves), which only remember two moves per depth — history is dense across all `from→to` pairs.
- Decay (e.g. shift right by 1 every iteration) keeps the table responsive across iterative-deepening iterations and across games.

## 12.6 Static Exchange Evaluation (SEE) in quiescence — **M, ≈ 30–50 Elo**

In [`QuiescenceSearch`](../src/main/java/org/michaelfl/mychess/QuiescenceSearch.java), prune captures whose SEE score is negative — i.e. the resulting exchange sequence loses material. The current quiescence loops over every capture, including obviously losing ones like `QxP` defended by a pawn.

- Implement `Board.see(toField, attackerPiece, defenderColor)`: alternately swap least-valuable attackers, return the resulting material delta.
- Also useful in the main search for ordering "winning vs. losing captures" beyond the current [`bucketWinningCaptures` / `bucketOtherCaptures`](../src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java) split based on victim − attacker (i.e. a one-ply MVV/LVA approximation).

## 12.7 Evaluation upgrades — **M, ≈ 50–100 Elo combined**

[§ 5 *Evaluation Function*](evaluation.md#5-evaluation-function) ends with a list of features deliberately omitted. Adding the cheapest ones individually buys little; bundling them is worthwhile. In rough cost order:

- **Bishop pair** (+30 cp when a side has both bishops). One bit-test added to the material scan.
- **Passed pawns** — bonus scaled by rank. Detection is one row-and-adjacent-file scan per pawn; do it inside the existing `calculateForWhitePawn` / `calculateForBlackPawn` loops to amortise.
- **King safety beyond castling** — count enemy attackers on the 3×3 square ring around the own king, weighted by attacker type. The pseudo-move scan in `WeightingFunction` already enumerates attackers; add a per-square attacker-count side table.
- **Proper endgame detection** — replace [`GameStatus.isEndGame() { return plyCount > 60; }`](../src/main/java/org/michaelfl/mychess/GameStatus.java) with a material-based criterion (e.g. `total non-pawn material < threshold`). This alone fixes the endgame king-PST cutoff in [§ 5.2](evaluation.md#52-piece-square-tables) and makes [§ 12.2 null-move pruning](#122-null-move-pruning--s--50100-elo) safer.
- **Tapered evaluation with PeSTO PSTs** — replace the hand-tuned [Simplified PSTs](https://www.chessprogramming.org/Simplified_Evaluation_Function) currently in [`PieceSquareTables`](../src/main/java/org/michaelfl/mychess/PieceSquareTables.java) with the auto-tuned [PeSTO](https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function) tables (separate midgame and endgame tables per piece type), interpolated by remaining non-pawn material. Requires the proper endgame detection above. Two design choices specific to myChess:
  - **Column-symmetrize the tables before use.** PeSTO is trained on standard-chess games where kingside castling dominates, so its tables encode column asymmetries (a-file ≠ h-file) that are statistical artifacts of the training corpus, not chess principles — the knight table has a-rank/h-rank values differing by ~80 cp on the back rank. For Chess960 ([§ 12.11](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant)) this asymmetry is actively harmful (no side dominates after castling); for standard chess it likely costs only 5–10 Elo. Mirror-average every column pair (a↔h, b↔g, c↔f, d↔e) at table-load time — one shared symmetric table for both variants is much simpler than maintaining two separate sets, and the Elo trade-off is well worth the reduced complexity.
  - **The existing `invert()` for the black tables stays unchanged.** It only flips ranks (1↔8 etc.) and doesn't touch column ordering, which is exactly what antisymmetry of the eval (`MirrorEvalTest`) requires.
- **Mobility weight retuning** — the six per-piece-type weights in `WeightingFunction.mobilityWeightOfPiece` are hand-tuned heuristics that have never been ELO-validated (see [§ 5.3 tuning observations](evaluation.md#tuning-observations) for the analysis). Pawn = 20 is high (conflates "not blocked" with "well-placed"); rook = 10 is flat across positions where an open-file rook should outscore a back-rank shuffle. A short SPSA-style sweep, or even a handful of candidate-tuple gauntlets, would likely yield 10–30 Elo without any new feature work.

## 12.8 Aspiration windows — **S, ≈ 20–40 Elo**

At each iterative-deepening iteration, search with a narrow window `[score − 50, score + 50]` around the previous iteration's score. Re-search with the wider window only on a fail-high or fail-low.

- One change in [`PositionSearch.calculateNextMove`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java)'s deepening loop.
- Pairs with [§ 12.1 TT](#121-transposition-table--s--m--150300-elo): without it, re-searches are expensive enough that the heuristic can be a net loss.

## 12.9 UCI protocol — **M (1–2 days), no Elo directly but unblocks GUI + measurement**

myChess currently has no programmatic move-by-move interface other than the REPL. A minimal [UCI](https://gist.github.com/DOBRO/2592c6dad754ba67e6dcaec8c90165bf) implementation (≈ 200 lines) gives the engine two huge things at once:

1. **A real GUI.** Once myChess speaks UCI, any UCI-aware GUI on macOS (see below) renders a board, accepts mouse input, manages clocks, exports PGN, and runs analysis — no GUI code needs to be written in myChess.
2. **Measurement against external opponents.** [`cutechess-cli`](https://cutechess.com/) runs automated gauntlets against other UCI engines (myChess-vs-Stockfish, myChess-vs-myChess-old, …), which is exactly the workflow needed to verify the Elo claims in this chapter.

This makes UCI **the recommended very first investment** of the whole roadmap — both because it produces an immediate visible payoff (a playable GUI) and because, once it's in place, [`cutechess-cli`](https://cutechess.com/) subsumes the self-play loop in [§ 12.10.3](#12103-self-play-tournament--m-1-day) and the rest of the in-process harness becomes a per-change diagnostic rather than the primary measurement tool.

### Minimal viable UCI: the 2-day path to a GUI

The full UCI protocol is large, but the subset needed for **"plays in HIARCS or Cute Chess"** is small. These eight commands are sufficient:

| Command | Direction | What myChess does |
|---|---|---|
| `uci` | GUI → engine | reply `id name myChess`, `id author …`, `uciok` |
| `isready` | GUI → engine | reply `readyok` |
| `ucinewgame` | GUI → engine | reset per-game state (empty handler for now) |
| `position [startpos\|fen …] [moves …]` | GUI → engine | rebuild `Board` from FEN, replay moves |
| `go [movetime N \| wtime N btime N \| depth N]` | GUI → engine | start `nextMoveAsync`, write `bestmove` when done |
| `stop` | GUI → engine | `NextMoveTask.cancel()` |
| `bestmove e2e4` | engine → GUI | the result of `go` |
| `quit` | GUI → engine | exit |

Optional `info depth … nodes … pv …` lines during search make the GUI's analysis panel light up but aren't strictly required to play. Not needed for a first version: `setoption` (waits for the TT in [§ 12.1](#121-transposition-table--s--m--150300-elo)), `ponder`, `UCI_Chess960` (waits for [§ 12.11](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant)).

Three concrete sub-steps:

#### 12.9.1 FEN importer — **½–1 day**

`Fen.exportFEN` exists; the inverse does not. Without a FEN importer the `position fen …` command can't be honored, which makes UCI useless. The importer also has standalone value — it lets the REPL accept `fen …` as input, not just output it.

- Parse the six FEN fields: position, side-to-move, castling rights, en-passant target, half-move clock, full-move number.
- Reconstruct `Board` + `GameStatus` and push an initial entry onto the status stack.
- ~50 LOC, plus FEN-round-trip tests (export → import → export should be byte-equal).

#### 12.9.2 `UciHandler` — **1 day**

A new class parallel to [`CommandHandler`](../src/main/java/org/michaelfl/mychess/CommandHandler.java).

- Reads stdin line-by-line via `IO.readln()` (same pattern as the REPL).
- Token-parses the eight commands above.
- Long-algebraic move parser: UCI sends `e2e4` (no dash, no piece letter); reuse [`SimpleNotationImporter`](../src/main/java/org/michaelfl/mychess/SimpleNotationImporter.java) with a trivial pre-processor that re-inserts the dash.
- Time management: at `go wtime 300000 btime 300000 movestogo 40` allocate roughly `wtime / (movestogo + safety)` for this move. `go movetime 5000` is trivial: that many seconds. *This is a flat per-move budget — no clock-aware time hoarding, panic mode, or complexity-based scaling; see [§ 12.12](#1212-real-time-management-heuristics--s--m--3060-elo).*
- **Important:** `System.out.flush()` after every reply line, otherwise the GUI never sees output (Java's default stdout is line-buffered when connected to a pipe — many GUIs hang silently on this).
- Start-up: in `MyChessMain`, if `args[0].equals("uci")` (or simply if the first stdin line is `uci`), run the `UciHandler` instead of the REPL.

#### 12.9.3 Connect HIARCS, run a baseline gauntlet — **½ day**

Final step, almost no code:

1. Build the JAR: `mvn package`.
2. Install HIARCS Chess Explorer Free from [hiarcs.com](https://www.hiarcs.com/) and Stockfish via `brew install stockfish`.
3. In HIARCS: *Settings → Engines → Add Engine*, type **UCI**, command `java -jar /path/to/myChess.jar uci` (probably via a wrapper shell script that sets `JAVA_HOME` to JDK 25).
4. Play a few games manually against myChess — sanity check that the protocol works end-to-end.
5. Optional but recommended: `brew install cutechess`, then run a baseline gauntlet against Stockfish at fixed depth 1, 2, 3 (those correspond to roughly 1500 / 1800 / 2100 Elo). 100 games each. That gives a *measured* absolute strength baseline for myChess before any optimization in this chapter begins — every later improvement can be re-measured against the same Stockfish depths to see the delta.

After this third step, every later roadmap entry can be both **played** (HIARCS) and **measured** (cutechess-cli + Stockfish + earlier myChess builds).

### Recommended GUIs on macOS

All free, all native Mac builds, all UCI-capable. None of the popular Windows-only options (ChessBase/Fritz, Arena) run natively on macOS.

| GUI | Strength | Best for |
|---|---|---|
| [**HIARCS Chess Explorer Free**](https://www.hiarcs.com/) | Polished native Mac app, full opening-book / analysis features. | Manual play and game analysis against myChess. *Recommended primary GUI.* |
| [**Cute Chess**](https://cutechess.com/) | Open source, includes `cutechess-cli` for batch tournaments. `brew install cutechess`. | Automated engine-vs-engine matches and gauntlets — exactly the measurement workflow this chapter needs. |
| [**Banksia GUI**](https://banksiagui.com/) | Modern interface, integrated 960 startposition generator. | A middle ground between HIARCS (manual play) and Cute Chess (batch testing). |

Stockfish (also UCI, also Mac-native via Homebrew) is the standard hobby-engine yardstick: Stockfish at fixed depth 1 corresponds to roughly 1500 Elo, depth 2 to ~1800, depth 3 to ~2100, etc. A small gauntlet against several depth levels gives an absolute strength estimate for myChess.

## 12.10 In-process measurement harness — **S–M, no Elo, but adds fast per-change diagnostics**

Once [§ 12.9 UCI](#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) is in place, [`cutechess-cli`](https://cutechess.com/) already covers the "full-game tournament" measurement. The harnesses below are **complementary**, not replacements — they are much **faster** signals during development (seconds, not hours) and surface *where* a change helped, which a tournament score does not.

In particular: once cutechess-cli is available, the self-play loop in [§ 12.10.3](#12103-self-play-tournament--m-1-day) becomes optional — keep it only if you want a zero-external-dependency fallback.

### 12.10.1 Node-count benchmark — **S, ½ day**

For every search-side change in this chapter (TT, LMR, null-move, ...) the most direct signal is: at the same depth, on the same position, **how many nodes did we visit?**

- Pick ~20 positions (mix of opening, middlegame, endgame).
- For each: search to a fixed depth, record the best move and `Statistics.getPositionsCount()`.
- Compare against a previously recorded baseline.

[`Statistics`](../src/main/java/org/michaelfl/mychess/Statistics.java) already collects everything needed. New code: ~30 LOC for a `BenchCommand` plus a hard-coded FEN fixture list.

What it doesn't catch: changes that produce a *different* best move (better or worse). For that, the next two harnesses are needed.

### 12.10.2 EPD test-suite runner — **S, 1 day** (assuming FEN-import from § 12.9 is in place)

[EPD (Extended Position Description)](https://www.chessprogramming.org/Extended_Position_Description) is FEN plus a `bm` ("best move") tag. The engine is given each position, allowed N seconds, and the proposed move is checked against `bm`. Score = % positions solved.

**Recommended starter suite: [WAC ("Win at Chess")](https://www.chessprogramming.org/Win_at_Chess) — 300 tactical positions.** It's the canonical hobby-engine benchmark for three reasons:

1. **Calibrated for myChess's expected strength.** A strong modern engine solves all 300 in seconds. A hobby engine at depth 6–8 typically scores 220–280/300, which is exactly the resolution band needed to see whether an optimization helped.
2. **Small and freely available.** ~25 KB, plain text, no licensing issue. Easy to embed under `src/test/resources/` or `data/`.
3. **Tactical focus matches what myChess will improve first.** The search optimizations in §§ 12.1–12.6 are tactical; WAC measures exactly that. Strategic suites (see below) make more sense after the search is solid.

When the WAC score plateaus, graduate to **[STS ("Strategic Test Suite")](https://www.chessprogramming.org/Strategic_Test_Suite) — 1500 positions in 15 themes** (open files, pawn structure, king safety, ...). STS gives a per-category breakdown, which directly tells you *which* evaluation component (§ 12.7) is weakest. STS is the right benchmark for measuring eval upgrades; it's worth the extra setup once the search side is stable.

Code: a `Pgn`-style parser for EPD (~50 LOC) plus a runner that hooks into `Game.getEngine().nextMoveAsync(...)` and matches the resulting move against `bm` (~50 LOC). Output: `Solved: 251/300 (83.7%), avg time 3.2s, total 16:01`.

### 12.10.3 Self-play tournament — **M, 1 day**

The most direct measurement of "playing strength" — pit two myChess builds against each other and read off the Elo delta from the score.

- A list of balanced opening FENs (50–100 positions). For now: pick from the populated [`OpeningDB`](../src/main/java/org/michaelfl/mychess/openingdb/OpeningDB.java), or ship a fixed list.
- For each opening, play **twice** (A=white, then A=black) so the opening doesn't bias either side.
- Tally `wins/draws/losses` for A.
- Convert to Elo: `eloDelta = -400 · log10(1 / score − 1)` where `score = (wins + 0.5·draws) / games`. Add a Wald-style confidence interval; with 200 games and a 60% score the 95% CI is roughly ±40 Elo.

`Game` already accepts two `ChessEngine` instances and `auto` plays one game between them ([§ 2.4](../README.md#24-concurrency-and-async-move-calculation)); the missing piece is the outer loop and the statistics. ~150 LOC for a `TournamentCommand`.

**Two pitfalls worth knowing up front:**

- *Draw rate.* Similar engines remise 40–60% of games. Underweighting draws (counting only wins) understates the difference; use the full `wins + 0.5·draws` score. To force more decisive games, use shorter time controls (5+0.1 instead of 30+0) or a tactical-rich opening set.
- *Blind spot.* If both versions share the same bug — e.g. both play `1.e4` poorly — self-play never reveals it. The opening fixture set helps, but the only real cure is an *external* opponent (which is what § 12.9 UCI gets you).

### Recommended order

1. **Build § 12.10.1 (node bench) first.** Half a day, immediate feedback on every search change.
2. **Build § 12.10.2 with WAC next**, after FEN-import lands (which § 12.9 needs anyway). Catches search-correctness regressions and tactical eval changes.
3. **Build § 12.10.3 (self-play loop) third.** Slower per signal but the only of the three that measures end-to-end playing strength.
4. **Add STS later**, once the eval upgrades in § 12.7 begin.
5. **§ 12.9 UCI on top of all this** validates the in-process numbers against external opponents — Stockfish at fixed-depth-1 is a well-known hobby-engine yardstick (~1500 Elo).

With (1)+(2)+(3) in place, every roadmap entry can be measured locally before merging. UCI becomes a sanity check, not a prerequisite.

## 12.11 Chess960 (Fischer Random) support — **M, no Elo on standard chess but opens a new variant**

[Chess960](https://en.wikipedia.org/wiki/Fischer_random_chess) is the variant invented by Bobby Fischer where the back-rank pieces are placed in one of 960 randomized starting positions (constrained so that bishops are on opposite colors and the king stands between the two rooks). Pawn moves and piece moves are unchanged; only the starting setup and the castling rules differ. All major modern engines (Stockfish, Lc0, Komodo) support it.

UCI handles 960 via the `UCI_Chess960` option, set by the GUI:

```
→ setoption name UCI_Chess960 value true
→ position fen bqnbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBNRKR w HFhf - 0 1
→ position fen ... moves b1d1                  ← castling: king "captures" own rook
```

Two protocol-level differences from standard chess:

1. **Shredder-FEN castling rights.** Instead of `KQkq` (which assumes standard king/rook squares) the rights are given as the **file letters of the castling rooks** — uppercase for white, lowercase for black, the letter closer to the H-file being king-side. Both Shredder-FEN and the alternative X-FEN (which keeps `KQkq` for the standard position and only switches to file letters for the other 959) must be readable.
2. **King-captures-rook castling notation.** Since king and rook can start on arbitrary squares, the castling move is encoded as king's start square → rook's start square (e.g. `b1d1` if the king is on b1 and the queen-side castling rook is on d1 — the engine then knows the king lands on c1 and the rook on d1).

### What it takes to add to myChess

Several core components have standard-chess assumptions hard-coded and would need to be generalized:

- **[`MoveGenerator`](../src/main/java/org/michaelfl/mychess/MoveGenerator.java) castling logic** ([§ 4.3](move-generation.md#43-castling-legality)) — start/target squares for the king and the rook currently assume e1/g1/c1/h1/a1 etc. Must come from the position instead.
- **[`Fen`](../src/main/java/org/michaelfl/mychess/Fen.java)** — read/write Shredder-FEN and X-FEN castling fields. Also needs the FEN *importer* that § 12.9 introduces.
- **[`GameStatus`](../src/main/java/org/michaelfl/mychess/GameStatus.java) castling-rights bits** — today store only "still possible y/n" per side and direction. For 960 they additionally need the rook's *file*, since the rook is not on a fixed square.
- **Zobrist hashing** — castling-rights component grows from 4 bits to up to 16 (8 possible rook files × 2 sides). The [`RandomNumbers`](../src/main/java/org/michaelfl/mychess/RandomNumbers.java) table needs more slots.
- **[`Move`](../src/main/java/org/michaelfl/mychess/Move.java) encoding** — the existing `typeCastlingKingSide` / `typeCastlingQueenSide` flags already exist, so the packed-int format itself is fine. But `makeMove` / `revertMove` must use the type flag rather than from-square arithmetic to recognize castling.
- **Test coverage** — every existing castling test ([§ 11.2](testing.md#112-notable-test-cases) `MoveGeneratorTest` castling matrix, `BoardTest` castling-state transitions) needs a 960 counterpart.

Realistic effort: 3–5 days, mostly concentrated in `Board.makeMove`, `Fen` and `MoveGenerator`.

### Why this sits at the end of the roadmap

Chess960 gives myChess *no Elo on standard chess* — it only opens a new variant. The variant-specific code is largely orthogonal to the search and evaluation upgrades in §§ 12.1–12.7: a 960 game is decided by the same kind of search and evaluation as a standard game, so the strength improvements that matter are already covered above. Putting 960 first means investing 3–5 days for zero strength gain on the format the engine actually plays today.

That said, 960 has two genuine upsides once the core engine is solid:

- **A no-opening-book benchmark.** myChess's [opening book](opening-database.md#9-opening-database) doesn't apply to 960 (each starting position is unique). The first 10–15 moves of a 960 game are pure engine search, which makes 960 self-play matches a much cleaner *search quality* signal than standard self-play (where book differences distort early evaluation).
- **External validation against Stockfish-960.** Stockfish supports 960 natively. A `cutechess-cli -variant fischerandom` gauntlet against fixed-depth Stockfish in 960 mode is straightforward once myChess speaks UCI + 960.

### Recommended Mac GUIs that support 960

All three GUIs listed under [§ 12.9](#129-uci-protocol--m-no-elo-but-unblocks-measurement) support Chess960:

- **HIARCS Chess Explorer Free** — full 960 support including setup of any of the 960 starting positions, FEN-based load, manual play.
- **Cute Chess / cutechess-cli** — `-variant fischerandom` for engine matches; setting up a 960 gauntlet is a single command-line flag.
- **Banksia GUI** — includes a one-click random-960-position generator alongside standard play.

## 12.12 Real time management heuristics — **S → M, ≈ 30–60 Elo**

myChess currently reads `wtime`/`btime`/`movestogo` from the GUI and converts them into a flat per-move budget (see [§ 12.9.2](#1292-ucihandler--1-day) / [`UciHandler.computeClockBudgetMillis`](../src/main/java/org/michaelfl/mychess/UciHandler.java)). The engine itself only ever sees `millisPerMove` — it has no notion of a remaining clock that persists across `go` calls.

**Partial implementation already in place: skip-hopeless-iteration heuristic.** `PositionSearch` now tracks a per-depth moving average of past iteration times in [`IterationTimings`](../src/main/java/org/michaelfl/mychess/engines/IterationTimings.java) and skips a deepening iteration whose estimated cost exceeds the remaining budget; recovered time stays on the clock and feeds later moves in clock-based TCs. A probing override with a remaining-time ratio gate prevents the SMA from freezing permanently. Tuning knobs live in [`EngineTuning`](../src/main/java/org/michaelfl/mychess/engines/EngineTuning.java). See [search § 6.5.1](search.md#651-skip-hopeless-iteration-heuristic) for the design details.

That's protocol-compliant and good enough for tests and casual play, but in long real games against any Stockfish-grade opponent it still leaves Elo on the table because the budget is wrong on most moves:

- **No time hoarding.** Quick book-style early moves don't bank time for later critical positions. Every move gets the same `remaining/movesToGo` slice regardless of how long the previous one actually took. *(Partially mitigated by the skip heuristic above — saved iteration time stays on the clock, so the next move sees a higher `remaining`. But there is still no proactive banking decision per move.)*
- **No panic mode.** Below a low-clock threshold (say <10 s for 30 moves) the engine should drop quiescence-extension depth, skip non-PV info-line emission, and return *anything* legal rather than search out an iterative-deepening level. Currently it just gets a tiny budget and possibly times out on the active iteration.
- **No complexity scaling.** Tactical positions (in check, lots of captures, hanging pieces) deserve more time; quiet positions less. A simple "spend 1.5× budget if the previous iteration's score swung > 50 cp" heuristic alone is worth ~20 Elo on faster time controls.
- **No multi-phase awareness.** With a "40/90 + rest" classical control, the move just after the time-control switch suddenly has a much bigger clock — the engine doesn't know to use it for the typically-tactical move 41.
- **No instant-move shortcut.** When only one legal move exists (recapture, only-move-out-of-check) the engine still spends its full budget searching. Detecting `legalMoves.size() == 1` and returning instantly is one Sonar-pass-worth of trivial code.

### What it takes to implement

The clean way: introduce a `TimeManager` class that lives on the `Game` (or `UciHandler`) and exposes `allocateBudget(GoArgs, gameState) → BudgetMs`. It internally tracks the rolling actual-vs-allocated-time deltas across recent moves and adjusts. Engine config gains optional fields `softLimitMs` (target) and `hardLimitMs` (absolute timeout — search must return immediately when crossed even mid-iteration). The search then uses the soft limit to decide whether to start a new iterative-deepening iteration, and the hard limit as a safety cutoff.

Realistic effort: ~1 day for the TimeManager skeleton + soft/hard limits in `PositionSearch`; another ~1 day for tuning the heuristics with `cutechess-cli` matches (which is itself why this entry depends on § 12.9 UCI being done first — without measurement, the tuning is guesswork).

### Why it's separate from the other Elo entries

Search optimizations (TT, LMR, null-move, …) make the engine *think faster*. Time management makes the engine *use the time it has smarter*. Both compound: a 2× faster search with bad time management still wastes the speedup; smart time management with a slow search hits its budget without going deep. Time management is the smaller of the two effects (rough estimate 30–60 Elo total) but it's load-bearing for any tournament work.

This entry intentionally comes *after* the search optimizations in the recommended order — without TT and friends the engine is too slow for the budget tuning to matter; with them, even modest time management improvements show up clearly.

## 12.13 ~~Switch alpha-beta from fail-hard to fail-soft~~ — **DONE**

*Done — implemented as preparation for the transposition table ([§ 12.1](#121-transposition-table--s--m--150300-elo)).*

Both `PositionSearch.alphaBetaSearchI` and `QuiescenceSearch.quiescenceSearch` now return the true unclamped score on beta cutoff and on fail-low. The previous `SearchNodeResult.window(weight, α, β)` helper and the alpha/β-taking factory overloads (`create`, `draw`, `stalemate`, `checkmateSelf`) are gone; terminal-node factories return raw scores. The `ILLEGAL_WEIGHT_POS` sentinel survives trivially since nothing clamps anymore. The `if (alpha >= 0) return alpha` shortcut in `checkmateOrStalemate` is removed — checkmate/stalemate now always return the true terminal score regardless of α.

The alpha-beta search tree is identical to fail-hard (same cutoff conditions, same best-move selection). What changes is the value returned at the boundary: a fail-high node returns *how far above β* it landed, a fail-low node returns *how far below α*. That information is what [§ 12.1 TT](#121-transposition-table--s--m--150300-elo) uses to store sharper lower/upper bounds, and what [§ 12.8 aspiration windows](#128-aspiration-windows--s--2040-elo) uses to set a tighter re-search range.

Regression test: [`QuiescenceSearchTest.quiescenceFailSoft_betaCutoffReturnsUnclampedWeight`](../src/test/java/org/michaelfl/mychess/QuiescenceSearchTest.java) constructs a stand-pat position, runs quiescence with both wide and tight β, and asserts the tight call returns the unclamped stand-pat (a fail-hard implementation would clamp to β).

## 12.14 Color asymmetry: investigate the W>B bias seen in cross-version matches — **S, evidence weakening**

> **Update June 2026:** the original "W>B bias is a real engine defect worth 30–50 Elo" hypothesis has lost support after three additional cutechess matches. The cross-version-artifact explanation is now the more plausible reading. See the *updated interpretation* section below.

Across five cutechess matches during the spring 2026 mobility-tuning sessions (positionFactor x2, mobilityFactor x2, mobility-rebalance, no-mobility, mobility-factor=0.15) a striking pattern emerged: in every match where the engine had any form of mobility weighting enabled, **myChess scored noticeably better as white than as black** — typically 40–65 Elo difference between colors. The single experiment where the asymmetry disappeared was the no-mobility ablation; with mobility re-enabled (at any factor in [0.1, 0.2]) the W>B gap returned, including in the strongest form (~65 Elo) at factor 0.15.

This is unusual. The engine's static eval is supposed to be color-antisymmetric (`eval(p) == -eval(mirror(p))`), and [`MirrorEvalTest`](../src/test/java/org/michaelfl/mychess/MirrorEvalTest.java) enforces that invariant. If self-play matches reproduce the same pattern, it implies a side-to-move-dependent bias somewhere in the eval or search machinery that the existing mirror test doesn't catch — and if that bias is fixed, white and black should play equally well, recovering the typical ~25 Elo of pure first-move advantage but not 60+. That's the size of the gap on the table.

### Updated interpretation (June 2026)

Three follow-up cutechess matches against `myChess-3.4.0` muddy the original picture:

| Variant vs 3.4.0 | W/B for myChess-new | Elo vs 3.4.0 |
|---|---|---|
| no-mobility | 0.491 / 0.504 (~3 Elo) | −1.7 ± 21.4 (neutral) |
| threadWeightFactor 0.17 | 0.452 / 0.448 (~3 Elo) | −34.8 ± 25.6 (regression) |
| threadWeightFactor 0.05 | 0.507 / 0.501 (~4 Elo) | +3.0 ± 20.8 (neutral) |

In all three, the W/B asymmetry is small or absent — even though only the no-mobility build actually disables a major eval component. The threadWeight variants leave mobility fully intact. Under the original "asymmetric mobility code" hypothesis, those should still show W>B; they don't.

What separates the asymmetric-W>B and the symmetric-W=B experiments more cleanly is **whether the variant is meaningfully different in Elo from 3.4.0**: the asymmetric ones (positionFactor doubled, mobilityFactor doubled, mobility rebalance, factor 0.15) all showed real-but-modest strength changes; the symmetric ones (no-mobility, threadWeight 0.05, threadWeight 0.17) either matched 3.4.0 closely or differed only via a strong regression. That pattern fits **cross-version-artifact** much better than **systematic engine defect**: when both engines play near-identical chess, the opening set distributes wins symmetrically between colors; when one engine has a slight edge, that edge concentrates into one color through whatever asymmetric pairing the book introduces.

The investigation plan below remains valid but **its premise is shakier than originally written**. Run step 1 only if interested in a definitive closure — otherwise the time is better spent on the search optimizations in §§ 12.1–12.8, which are documented Elo wins.

### Why this is worth pursuing

- **Real Elo.** 30–50 Elo is in the same league as null-move pruning or LMR.
- **Cheap to investigate.** The first three steps below are pure measurement and code reading; no risky changes until the cause is understood.
- **Possibly a correctness bug, not a tuning issue.** If a mobility-counting path treats the side to move differently from the other side, that's a defect — not a parameter to twiddle.

### Investigation plan

**Step 1 — confirm via self-play (½ day, no code change):**
Run a cutechess self-play match `myChess-3.4.0` vs `myChess-3.4.0` (literally the same binary on both sides), 800 games on the same balanced opening set used previously. The cross-version matches between two *different* engine builds could leak color preference through opening-book asymmetries or the relative-strength gap. A same-binary match isolates the engine itself. Expected outcome under a hidden bias: white scores noticeably > 50%, black noticeably < 50%, total well above the 52–53% white-first-move baseline.

**Step 2 — bisect with no-mobility build (½ day):**
Repeat step 1 using the no-mobility build (the [`version-3.4`](https://example/branch) branch's `d324ecd` revert as the binary). If the asymmetry disappears in this self-play but persists in step 1, the mobility code is the proximate cause. If the asymmetry persists in both, it's elsewhere (PSTs, castling, threat weight, …) and the no-mobility correlation was coincidence over five matches.

**Step 3 — code audit on `WeightingFunction` for side-of-move dependencies (1 day):**

Likely places where a side-to-move asymmetry could leak into a "should-be-symmetric" mobility count:

- [`calculateForWhitePawn` vs `calculateForBlackPawn`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) — pawn move generators are written separately per color. Subtle off-by-one in en-passant or double-step handling could give white more "available moves" than black on otherwise mirror positions. Add a focused test: build a position, mirror it via the existing `MirrorEvalTest` helper, compare *per-component* (mobility, threats, chess count, etc.), not just the final weight.
- [`capture()` handling of `oppositeKing`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) — `containsIllegalMove` is set when `turn == color`. If the threshold for "side to move can capture opposing king" fires asymmetrically (e.g., in pinned positions, en-passant pins), white might get an extra threat credit black doesn't.
- The **6th-rank vs 3rd-rank pawn check** in `calculateForWhitePawn` / `calculateForBlackPawn` (en-passant detection) — uses `lastMove`. If the en-passant trigger condition is slightly different for the two colors (a different rank check), one side gets a phantom capture credit.

**Step 4 — fix and verify (½–1 day):**
Once a candidate source is identified, write a unit test that captures the asymmetric output for a specific mirrored position pair. Fix the code. Re-run step 1's self-play. The W/B gap should drop to ~25 Elo (pure first-move advantage) instead of 60+.

### What this is *not*

- Not a tuning step (no factor is being adjusted).
- Not a refactor for its own sake — only act once step 1 confirms the asymmetry reproduces in same-binary self-play. If step 1 shows ~50/50, the previously observed W>B pattern was an artifact of comparing different engine versions (opening book, draw adjudication, …) and the whole investigation is dropped.

### Why this slot in the roadmap

This entry is independent of the search-optimization chain (§§ 12.1–12.8) and the eval upgrades (§ 12.7). The investigation can run in parallel with any of them. Recommended trigger: after the in-process measurement harness (§ 12.10) is in place — a node-count bench for the eval delta and a 100-position EPD pair makes the bisection in step 3 much cheaper than full cutechess matches.

## 12.15 ~~Pawn-structure connection-quality term~~ — **investigated, not productive**

*Investigated June 2026 across seven SPRT measurements. The "connection-quality" pawn-structure heuristic (count own-color pawn neighbors per pawn, normalize to [0, 1] via `2 * (pawnCount - 1)`, apply as scaled eval delta) is consistently not strength-positive in any tested configuration. The branches `pawn-structure` and `pawn-structure-narrow` remain in the repository as research archives but are not merged.*

### What was measured

Seven SPRT runs against the then-current master (3.5.1 for the first six, 3.5.2 for the last two), 400–800 games each, TC 40/60, SPRT bounds `elo0=-3, elo1=10, α=β=0.05`:

| Run | Config | Window | Factor | Doubled-pawn baseline | Pooled Elo |
|---|---|---|---|---|---|
| v1 | combined formula (doubled-pawn folded into structure score) | ±2 ranks | 0.5 | old (−0.10, adjacent-only) | −18.7 ± ~22 |
| v2 (1st run) | split formula | ±2 ranks | 0.5 | old | −3.5 ± 21.7 |
| v2.1 (confirm) | split formula | ±2 ranks | 0.5 | old | −26.3 ± 22.5 |
| v3 | split formula | ±2 ranks | 1.0 | old | −26.4 ± 22.4 |
| v4 | split formula | ±2 ranks | 0.3 | old | −34.8 ± 25.5 |
| narrow (1st run) | split formula | **±1 rank** | 0.5 | new (−0.15, full-file scan) | +9.1 ± 21.2 |
| narrow-2 (confirm) | split formula | **±1 rank** | 0.5 | new | −4.3 ± 21.6 |

Pooled narrow (1600 games): roughly **+2.5 ± 15 Elo, LOS ~60%** — statistically indistinguishable from neutral.

The doubled-pawn detection improvement (full-file scan + theory-conformant `-0.15` penalty, see [§ 12.7 / commit `40a5ec7`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java)) was isolated separately in an 800-game SPRT and came out **−5.6 ± 21.3 (LOS 30%)**. Merged anyway because it fixes a correctness bug in the old adjacent-only detection (non-adjacent doubled pairs like a3+a5 were silently missed) and the −0.15 value is the chess-theory standard.

### What we learned

1. **The ±2-rank neighbor window is too wide.** Five SPRT runs with the ±2-rank definition (v1–v4) were all clearly negative. The window includes pawn pairs that cannot actually defend each other (e.g. a3 and b5), so the "connection" signal is noisy and counts non-defending pairs as positive structure.

2. **The ±1-rank window is meaningfully better** (~24 Elo swing relative to ±2) but the resulting strength change against the new 3.5.2 baseline is too small to be worth the eval-code complexity (~80 lines, two more array reads per pawn per eval call).

3. **The non-monotonic factor-strength curve in v2/v3/v4** (factor 0.3, 0.5, 1.0 all negative, with 0.3 the worst) was an artifact of the v2 single-run measurement being on the high tail of the variance distribution. The v2.1 confirmation (−26 vs the original −3.5 at identical config) demonstrated that single 400–800-game SPRT runs at the connection-quality signal magnitude have CI bands too wide to support fine-grained factor tuning conclusions.

4. **The connection-quality concept itself does not seem tractable in this design.** Any future pawn-structure work in myChess should target qualitatively different features (passed pawns, isolated pawns, backward pawns, king pawn shelter, weak squares) rather than tuning further variants of "count pawn neighbors and add a fraction of the count".

### Cross-cutting observation: W/B asymmetry was unreliable

Run-to-run W/B asymmetry varied dramatically at the 400-games-per-color sample size: v2 showed ~0 gap, v2.1 showed ~23 Elo gap, v4 showed ~38 Elo gap, narrow showed ~30 Elo gap, narrow-2 showed ~14 Elo gap — all with the same engine pair and the same opening set. This *further* undermines the §12.14 "W>B is a real engine defect" hypothesis: at the sample sizes used in that section's evidence, color asymmetry is dominated by variance, not signal. **A definitive §12.14 investigation would need either much larger sample sizes (≥ 2000 games per match) or color-balanced opening-pair scheduling.**

### Why this slot in the roadmap

Documents the closure so the heuristic family isn't unwittingly re-attempted. The two research branches (`pawn-structure`, `pawn-structure-narrow`) are kept for reference. If pawn-structure work resumes, start from a different feature family — see point 4 above.

## 12.16 ~~Remove `threadWeight` term from the evaluation function~~ — **investigated, not productive**

*Investigated June 2026 across two SPRT measurements. Removing the `threadWeight` "soft-material" term (the per-capture-target bonus scaled by `threadWeightFactor`, originally `0.02`) gave a roughly neutral, slightly negative pooled result and is not merged. The branch `no-thread-weight` is kept as a research archive.*

### What the term did

`threadWeight[color]` accumulated, during the per-piece eval scan, a small bonus for every potential capture target the side could threaten — roughly `weightOfPiece[capturedPiece]` per pseudo-legal capture, plus `+4` for any move that put the opposing king in check. Multiplied by `threadWeightFactor = 0.02f` in the final sum. Conceptually a coarse approximation of "side-to-move can take stuff," which a working quiescence search ([`QuiescenceSearch`](../src/main/java/org/michaelfl/mychess/QuiescenceSearch.java)) already covers more precisely. The hypothesis was: with QSearch in place, `threadWeight` is redundant or actively noise, and removing it should be neutral-to-positive.

### What was measured

Two 800-game SPRT runs against `myChess-3.5.2`, TC 40/60, SPRT bounds `elo0=-3, elo1=10, α=β=0.05`:

| Run | W-L-D | Elo | LOS | SPRT |
|---|---|---|---|---|
| 1 | 325-298-177 | +11.7 ± 21.3 | 86.0% | did not terminate (llr +0.91) |
| 2 | 290-344-166 | **−23.5 ± 21.5** | 1.6% | **H0 accepted at lbound** (llr −2.93) |
| **Pooled (1600)** | **615-642-343** | **~−6 ± 15** | **~22%** | — |

Two independent measurements of the same configuration differed by **35 Elo**. Run 1 looked like a clear win; run 2 was a clear regression. Pooled estimate is approximately neutral with a mild negative lean.

### What we learned

1. **The term is not measurably harmful at factor `0.02`.** The earlier impression that "less `threadWeight` = better" came from a single factor-`0.17` measurement that was clearly a regression. With factor `0.05` at ~neutral and factor `0.00` (this experiment) also at ~neutral, the whole bottom half of the factor range is statistically indistinguishable. Only large factors clearly hurt.
2. **No clear case for removal.** The simplification argument (~10 fewer lines, two fewer increments per `capture()` call) would be defensible if the change were Elo-neutral or positive. With a pooled point estimate of −6 Elo, the code shrink does not justify the potential strength loss.
3. **`threadWeight` and `QSearch` are not fully redundant after all.** If they were, removing `threadWeight` should be exactly neutral. The slight pooled regression hints that `threadWeight` still contributes some useful signal at the leaf (presumably positions just past the QSearch horizon where a potential capture should weigh into the static eval), even if that signal is weak.

### Methodology lesson — small-effect SPRT noise floor

This is now the **third** investigation in §§ 12.15–12.16 where a single 800-game SPRT measurement was misleading by ≥ 13 Elo at our usual CI of ±21:

| Investigation | Run 1 (point est.) | Run 2 (point est.) | Δ |
|---|---|---|---|
| pawn-structure v2 (split, ±2 rank, factor 0.5) | −3.5 | −26.3 | 23 Elo |
| pawn-structure narrow (split, ±1 rank, factor 0.5) | +9.1 | −4.3 | 13 Elo |
| no-thread-weight (this entry) | +11.7 | −23.5 | **35 Elo** |

**Implication for future small-effect investigations:** when the true Elo effect is plausibly in the ±10 band, an 800-game SPRT is the wrong instrument. Concrete options for next time:

- **Default to 1600+ games** per measurement when the expected effect is small. CI shrinks from ±21 to ~±15; SPRT also has more chances to terminate cleanly.
- **Color-balanced opening pairs** (Gauntlet-style: every opening played from both sides by both engines). Halves the W/B-variance contribution to the run-to-run drift.
- **Treat single-run "promising" results as hypothesis-generating, not decision-grade.** A second independent run is mandatory before merging anything in the ±10-Elo band.

**SPRT with a large game budget is self-tuning sample size.** A 1600-game budget does not mean every match runs 1600 games. If the true effect is large enough to cross either SPRT bound, the match terminates early — and we save the remaining budget. If the true effect sits inside the ±10-Elo band, the match runs to the limit and we read the pooled point estimate off the final score. §12.17 (`chessFactor` removal) demonstrates this: 1600-game budget, real effect ≈ −14 Elo, SPRT terminated cleanly at 1199 games (75% of budget). The three earlier 800-game runs in §§ 12.15–12.16 ran into their limit precisely because their true effects sat inside that ±10 band — a 1600-game budget would have produced the same "indistinguishable from neutral" verdict, just from one match instead of two.

A small-effect SPRT bench probably belongs in [§ 12.10 (in-process measurement harness)](#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — fixed seeds, color-balanced pairs, fast turnaround so that the ±15 CI is the default, not the exception.

### Why this slot in the roadmap

Documents the closure so the `threadWeight` removal isn't unwittingly re-attempted. The `no-thread-weight` research branch stays in the repository for reference. The methodology lesson above is the more durable takeaway — it shapes how we should run *any* future investigation in the small-Elo band.

## 12.17 ~~Remove `chessFactor` term from the evaluation function~~ — **investigated, term confirmed productive**

*Investigated June 2026 with a single 1600-game-budget SPRT against `myChess-3.5.2`. Removing the `chessFactor` "can-give-check" bonus produced a clear, statistically significant Elo regression. The term stays in the evaluation. The `no-chess-factor` branch is kept as a research archive.*

### What the term did

`chessCount[color]` was incremented inside `capture()` whenever the per-piece move scan found that the side could "capture" the opposing king — i.e., the side could play a check on the next ply. Multiplied by `chessFactor = 0.25f` in the final eval sum, this was a flat **+0.25 pawn unit bonus per available check** at the eval leaf. The hypothesis — analogous to §12.16 — was that quiescence search already covers forcing moves and the bonus might be redundant or noise.

### What was measured

One 1600-game-budget SPRT against `myChess-3.5.2`, TC 40/60, SPRT bounds `elo0=-3, elo1=10, α=β=0.05`:

| Run | W-L-D | Elo | LOS | SPRT |
|---|---|---|---|---|
| 1 | 421-468-310 (1199 games) | **−13.6 ± 16.9** | 5.7% | **H0 accepted at lbound** (llr −2.98, terminated at 75% budget) |

W/B split:
- White: 221-220-159 → ~+1 Elo (essentially neutral)
- Black: 200-248-151 → ~−28 Elo (clear regression)
- W/B gap: 29 Elo — concentrated almost entirely on the Black side.

This is the **cleanest negative result** in the eval-removal investigation series so far: SPRT terminated cleanly (no game-limit fallback), CI is ±17 instead of the ±21 from 800-game runs, the regression is bigger than the CI by a factor of ~2.5 (compared to roughly 1.0 for the threadWeight and pawn-structure-narrow point estimates), and no confirmation run is needed.

### What we learned

1. **`chessFactor` is genuinely contributing strength.** Unlike `threadWeight` (§12.16, removal was pooled-neutral), removing the check-bonus loses measurable Elo. The two terms are not symmetric in their value despite both being "soft attack signals."

2. **QSearch and `chessFactor` are complementary, not redundant.** Quiescence search in myChess follows captures only — it does not extend on checks (no check-extension feature is implemented; see [§ 12.4](#124-check-extensions--s--1530-elo)). So a leaf node where the side *could* give check next ply has no way to surface that information to alpha-beta unless the static eval encodes it. `chessFactor = 0.25` is effectively a cheap proxy for the missing check-extension: it nudges the search toward lines with forcing moves available, which often correlate with king-attack themes the rest of the eval doesn't directly capture.

3. **Cost/benefit is the inverse of §12.16.** `threadWeight` cost ~10 lines and delivered pooled-neutral Elo (so removal was defensible on simplification grounds, just not necessary). `chessFactor` costs ~5 lines and delivers ~+14 Elo (so removal would be a clear regression, simplification argument loses). The two terms look superficially similar in the code but play very different roles.

4. **Possible follow-up: ~~remove~~ *upgrade* the term.** If `chessFactor` is a poor man's check-extension, then implementing [§ 12.4 (check extensions)](#124-check-extensions--s--1530-elo) properly might subsume the term and possibly add another +5–15 Elo on top. The natural sequence is: keep `chessFactor` for now → implement check extensions → re-run the removal experiment with extensions in place → expect the regression to shrink or vanish (if extensions fully cover the signal).

### Methodology — SPRT self-tunes with adequate budget

This run also confirms the §12.16 takeaway about budget sizing in practice. With a 1600-game-budget SPRT:

- Real effect ≈ −14 Elo (larger than the SPRT's `elo0 = −3` lower threshold by margin) → terminated at 1199 games, 75% of budget.
- A confirmation run would not have changed the verdict — the original run already crossed the bound.
- No "noise floor" misleading us: the LOS of 5.7% with a 16.9-Elo CI is not the "noise" range we saw in §§ 12.15–12.16.

This is the budget-policy this section's table should be read against: 1600 is the **maximum**, not the typical, and real effects come in well before that.

### Why this slot in the roadmap

Documents the closure: `chessFactor` is not a candidate for removal. The `no-chess-factor` branch stays in the repository as a research archive so the same experiment isn't accidentally re-attempted. The more interesting open question — whether implementing [§ 12.4 check extensions](#124-check-extensions--s--1530-elo) would let us *then* drop `chessFactor` for free — is captured as a sequencing note in point 4 above.

## 12.18 ~~Remove `EVALUATE_MATERIAL_ONLY_THRESHOLD` shortcut~~ — **investigated, mechanism strongly confirmed productive**

*Investigated June 2026 with a single 1600-game-budget SPRT against `myChess-3.5.2`. Removing the material-only leaf shortcut produced the **strongest negative result and earliest SPRT termination** of the whole eval-removal series. The shortcut stays in the search. The `no-material-only-treshold` branch is kept as a research archive.*

### What the shortcut did

[`PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD = 200`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) and the matching `materialDelta` running counter (carried through `SearchNodeContext` and the `QuiescenceSearch` recursion) implemented a leaf-eval shortcut: whenever the cumulative material delta since the search root exceeded ±200 centi-pawns, `calculatePositionWeight` returned the raw `materialWeight` and skipped the full positional evaluation (`WeightingFunction.calculate` — PSTs, mobility, threat weight, castling, doubled pawns, etc.).

Conceptually a "you're already up/down a couple of pawns, don't fuss about positional fine print" rule. The removal hypothesis: with the full eval cheap and `QuiescenceSearch` already handling captures cleanly, the shortcut might be redundant or even harmful (positional features could pick the better move within a class of materially-equivalent leaves).

### What was measured

One 1600-game-budget SPRT against `myChess-3.5.2`, TC 40/60, SPRT bounds `elo0=-3, elo1=10, α=β=0.05`:

| Run | W-L-D | Elo | LOS | SPRT |
|---|---|---|---|---|
| 1 | 179-233-134 (546 games) | **−34.5 ± 25.4** | 0.4% | **H0 accepted at lbound** (llr −2.96, terminated at 34% budget) |

W/B split:
- White: 94-114-65 → ~−26 Elo
- Black: 85-119-69 → ~−43 Elo
- W/B gap: ~17 Elo (within the noise range we've seen at < 300 games per color)

This is the cleanest, fastest, and largest-magnitude negative result of the eval-removal series. The CI lower bound is ≈ −60 Elo, the upper bound ≈ −9 Elo — even the most optimistic reading of the data places the shortcut's contribution above any noise floor.

### What we learned

1. **The shortcut is a major strength contributor — ~34 Elo at TC 40/60.** Three non-exclusive mechanisms likely combine to produce this:
   - **Speed → depth.** Skipping `WeightingFunction.calculate` for an entire leaf class (when material is decisive) is a non-trivial node-time saving. More nodes per second translates directly into more search depth in time-bounded play.
   - **Noise suppression in decided positions.** Positional features can register short-term disadvantages even when the material verdict is already settled. The shortcut forces the engine to commit to the material truth in those leaves instead of being pulled toward positionally-attractive but materially-losing continuations.
   - **Eval-extreme avoidance.** In highly imbalanced positions, some positional components (mobility, PST, threat) can produce values that overreact to the material differential. The shortcut bypasses these pathological cases.

2. **"Skip the full eval when material says X" is a real heuristic, not just an optimization.** This contradicts the naive intuition that more information is always better; in fact, with the eval still imperfect (no king-safety term, no passed-pawn term, no proper piece-square evaluation in late game), the *less* information path can be more accurate in materially-decided leaves.

3. **The shortcut and QSearch are complementary.** QSearch handles the local tactical horizon by following captures; the material-only shortcut handles the global material verdict by suppressing positional noise once material is clearly tilted. They cover different parts of the eval-correctness space.

### Methodology — SPRT termination at 34% budget

This run is the cleanest demonstration of the §12.16 self-tuning principle:

| Investigation | True effect ≈ | SPRT termination | Confirmation needed? |
|---|---|---|---|
| chessFactor removal (§12.17) | −14 Elo | 1199 / 1600 games (75% budget) | no — terminated cleanly |
| **material-only-shortcut removal (this entry)** | **−34 Elo** | **546 / 1600 games (34% budget)** | **no — strongly terminated** |
| narrow / threadWeight removals (§§12.15–12.16) | ≈ 0 Elo | ran to limit, pooled | yes — needed confirmation runs |

The pattern is monotonic and clean: the bigger the true effect, the earlier SPRT terminates and the less budget is consumed. With 1600 as the budget ceiling, large effects pay only a fraction of that ceiling. Small effects run to the limit and produce a pooled point estimate — which is what we want, because at those magnitudes the only useful question is "indistinguishable from zero?" and pooling answers exactly that.

### Why this slot in the roadmap

Documents that the `EVALUATE_MATERIAL_ONLY_THRESHOLD` shortcut is not a candidate for removal — it carries ~34 Elo of measurable strength. The `no-material-only-treshold` branch stays in the repository as a research archive. The investigation also strengthens the methodology baseline for future eval-removal work: when the SPRT terminates inside the first half of the budget, the verdict is generally not in question and a confirmation run does not add value.

A second-order open question: the 200-centi-pawn threshold itself was never tuned. It's plausible that 150 or 300 might be slightly better. Worth a future single-run SPRT each, but only after higher-priority items in §§ 12.1–12.8.

---

## Suggested implementation order

| Step | Item | Combined effort | Cumulative Elo (rough) |
|---|---|---|---|
| 1 | [§ 12.9 UCI minimal](#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) — FEN importer + `UciHandler` + HIARCS/Stockfish baseline gauntlet | M (1–2 days) | — (GUI + baseline measurement) |
| 2 | [§ 12.10 In-process harness](#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — node-count bench + WAC EPD runner (self-play loop optional, covered by cutechess-cli from step 1) | S | — (per-change diagnostics) |
| 3 | [§ 12.1 Transposition table](#121-transposition-table--s--m--150300-elo) (fail-soft alpha-beta is already in place, see [§ 12.13](#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--done)) | M | +150 – +300 |
| 4 | [§ 12.3 LMR](#123-late-move-reductions-lmr--s--50100-elo) + [§ 12.5 history](#125-history-heuristic--s--3050-elo) | S | +250 – +450 |
| 5 | [§ 12.2 Null-move pruning](#122-null-move-pruning--s--50100-elo) | S | +300 – +550 |
| 6 | [§ 12.4 Check extensions](#124-check-extensions--s--1530-elo) + [§ 12.8 aspiration](#128-aspiration-windows--s--2040-elo) | S | +340 – +620 |
| 7 | [§ 12.6 SEE](#126-static-exchange-evaluation-see-in-quiescence--m--3050-elo) | M | +370 – +670 |
| 8 | [§ 12.7 Eval upgrades](#127-evaluation-upgrades--m--50100-elo-combined) | M | +420 – +770 |
| 9 | [§ 12.12 Real time management](#1212-real-time-management-heuristics--s--m--3060-elo) | S–M | +450 – +830 |
| 10 | [§ 12.11 Chess960](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant) (optional, opens a new variant) | M | — (on standard chess) |

The order is deliberate: **UCI first**, because (a) it yields an immediately visible GUI, (b) `cutechess-cli` becomes available as the measurement workhorse, and (c) a baseline gauntlet against fixed-depth Stockfish anchors every later improvement against a stable external reference. The in-process harness then adds fast per-change diagnostics. TT is the next biggest single jump, and LMR / null-move / aspiration all assume it exists. The eval upgrades come last because their interactions with the search are the easiest to misjudge without measurement. Chess960 is last of all because it gives zero Elo on standard chess and is best tackled once the core engine is strong.

What is *not* on this list — neural-network evaluation (NNUE), parallel search ("Lazy SMP"), and endgame tablebases — would each be a much larger project than anything above, and would shift the character of the engine away from "hand-written, single-threaded, study-friendly". They are out of scope for the foreseeable future.
