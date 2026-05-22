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
- **Tapered evaluation** — separate midgame and endgame piece-square tables, interpolated by remaining non-pawn material. Two tables per piece type, one extra multiply per PST lookup. Requires the proper endgame detection above.

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

## 12.13 Switch alpha-beta from fail-hard to fail-soft — **S, no direct Elo, enables aspiration / TT tightening**

[`PositionSearch.SearchNodeResult.window()`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) implements the classical *fail-hard* convention: every node clamps its return value to the calling node's `[alpha, beta]` window. Switching to *fail-soft* — return the true value, unclamped — is the same algorithm logically (alpha-beta pruning makes the same decisions in both variants), but it gives callers more information at the boundary:

- **Fail-low** (true score `< alpha`): exposes *how far below* α the position is, not just "≤ α".
- **Fail-high** (true score `> beta`): exposes *how far above* β it is.

That extra information is what makes [§ 12.8 aspiration windows](#128-aspiration-windows--s--2040-elo) cheaper (a tighter re-search range on a fail), and what lets a future transposition table store a sharper upper / lower bound instead of just `≤ alpha` / `≥ beta`.

### What it takes to implement

- Remove the clamping in `window()` — change it to a no-op or delete `window()` and the two `create(...)` overloads that call it. Single change, single file. Compile-clean.
- Audit the consumers of search results to make sure they don't assume `weight ∈ [alpha, beta]`:
  - `alphaBetaSearchI` itself uses `weight >= beta` for cutoff and `weight > bestResult.weight` for best-update — both correct for any `weight`, no change.
  - `calculateNextMove` (root) compares `weight > ILLEGAL_WEIGHT_NEG` to filter illegal moves and picks the maximum — also correct for any `weight`.
  - `quiescenceSearch` (both layers) returns raw weights already; no change.
- The `ILLEGAL_WEIGHT_POS` sentinel pass-through added during the 2026-05-22 illegal-PV fix becomes redundant — the sentinel would naturally survive fail-soft and the special-case can be deleted.

### Why this isn't done today

- No direct Elo. Pure refactor; the value is unlocked only when § 12.1 (TT) or § 12.8 (aspiration) are added.
- The bug fix that motivated this entry already works under fail-hard via the targeted sentinel exception — the structural cleanup can wait.
- Best done **together with TT introduction**, because that's where the additional bound information starts paying off.

Expected performance impact: nil to marginal (one fewer comparison per leaf, but otherwise identical work). Expected stability impact: nil if the consumer audit above holds (move ordering and PV construction don't depend on the window-clamping).

---

## Suggested implementation order

| Step | Item | Combined effort | Cumulative Elo (rough) |
|---|---|---|---|
| 1 | [§ 12.9 UCI minimal](#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) — FEN importer + `UciHandler` + HIARCS/Stockfish baseline gauntlet | M (1–2 days) | — (GUI + baseline measurement) |
| 2 | [§ 12.10 In-process harness](#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — node-count bench + WAC EPD runner (self-play loop optional, covered by cutechess-cli from step 1) | S | — (per-change diagnostics) |
| 3 | [§ 12.1 Transposition table](#121-transposition-table--s--m--150300-elo) + [§ 12.13 fail-soft alpha-beta](#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--s-no-direct-elo-enables-aspiration--tt-tightening) | M | +150 – +300 |
| 4 | [§ 12.3 LMR](#123-late-move-reductions-lmr--s--50100-elo) + [§ 12.5 history](#125-history-heuristic--s--3050-elo) | S | +250 – +450 |
| 5 | [§ 12.2 Null-move pruning](#122-null-move-pruning--s--50100-elo) | S | +300 – +550 |
| 6 | [§ 12.4 Check extensions](#124-check-extensions--s--1530-elo) + [§ 12.8 aspiration](#128-aspiration-windows--s--2040-elo) | S | +340 – +620 |
| 7 | [§ 12.6 SEE](#126-static-exchange-evaluation-see-in-quiescence--m--3050-elo) | M | +370 – +670 |
| 8 | [§ 12.7 Eval upgrades](#127-evaluation-upgrades--m--50100-elo-combined) | M | +420 – +770 |
| 9 | [§ 12.12 Real time management](#1212-real-time-management-heuristics--s--m--3060-elo) | S–M | +450 – +830 |
| 10 | [§ 12.11 Chess960](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant) (optional, opens a new variant) | M | — (on standard chess) |

The order is deliberate: **UCI first**, because (a) it yields an immediately visible GUI, (b) `cutechess-cli` becomes available as the measurement workhorse, and (c) a baseline gauntlet against fixed-depth Stockfish anchors every later improvement against a stable external reference. The in-process harness then adds fast per-change diagnostics. TT is the next biggest single jump, and LMR / null-move / aspiration all assume it exists. The eval upgrades come last because their interactions with the search are the easiest to misjudge without measurement. Chess960 is last of all because it gives zero Elo on standard chess and is best tackled once the core engine is strong.

What is *not* on this list — neural-network evaluation (NNUE), parallel search ("Lazy SMP"), and endgame tablebases — would each be a much larger project than anything above, and would shift the character of the engine away from "hand-written, single-threaded, study-friendly". They are out of scope for the foreseeable future.
