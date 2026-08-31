# 12c. Roadmap — Backlog & Infrastructure

Companion to [the main roadmap](roadmap.md). This file collects **optional / enabling** items with no direct standard-chess Elo: protocol, tooling, and variant support. Section numbers (§ 12.x) are **stable IDs**. See the [master index](roadmap.md#roadmap-index) in the main roadmap.

---

## 12.9 UCI protocol — **M (1–2 days), no Elo directly but unblocks GUI + measurement**

myChess currently has no programmatic move-by-move interface other than the REPL. A minimal [UCI](https://gist.github.com/DOBRO/2592c6dad754ba67e6dcaec8c90165bf) implementation (≈ 200 lines) gives the engine two huge things at once:

1. **A real GUI.** Once myChess speaks UCI, any UCI-aware GUI on macOS (see below) renders a board, accepts mouse input, manages clocks, exports PGN, and runs analysis — no GUI code needs to be written in myChess.
2. **Measurement against external opponents.** [`cutechess-cli`](https://cutechess.com/) runs automated gauntlets against other UCI engines (myChess-vs-Stockfish, myChess-vs-myChess-old, …), which is exactly the workflow needed to verify the Elo claims in this chapter.

This makes UCI **the recommended very first investment** of the whole roadmap — both because it produces an immediate visible payoff (a playable GUI) and because, once it's in place, [`cutechess-cli`](https://cutechess.com/) subsumes the self-play loop in [§ 12.10.3](roadmap-backlog.md#12103-self-play-tournament--m-1-day) and the rest of the in-process harness becomes a per-change diagnostic rather than the primary measurement tool.

### Minimal viable UCI: the 2-day path to a GUI

The full UCI protocol is large, but the subset needed for **"plays in HIARCS or Cute Chess"** is small. These eight commands are sufficient:

| Command | Direction | What myChess does |
|---|---|---|
| `uci` | GUI → engine | reply `id name myChess`, `id author …`, `uciok` |
| `isready` | GUI → engine | reply `readyok` |
| `ucinewgame` | GUI → engine | reset per-game state (empty handler for now) |
| `position [startpos\|fen …] [moves …]` | GUI → engine | rebuild `Board` from FEN, replay moves |
| `go [movetime N \| wtime N btime N [winc N binc N] \| depth N]` | GUI → engine | start `nextMoveAsync`, write `bestmove` when done |
| `stop` | GUI → engine | `NextMoveTask.cancel()` |
| `bestmove e2e4` | engine → GUI | the result of `go` |
| `quit` | GUI → engine | exit |

Optional `info depth … nodes … pv …` lines during search make the GUI's analysis panel light up but aren't strictly required to play. Now feasible as follow-up work since both prerequisites have landed: `setoption name Hash` (TT is now in master, [§ 12.1](roadmap-done.md#121-transposition-table--done-93-elo)) and `setoption name UCI_Chess960` (Chess960 is in master, [§ 12.11](roadmap-backlog.md#1211-chess960-fischer-random-support--done)). `ponder` remains out of scope for a first version. The `Hash` option specifically is motivated by the v4.0.1 null-effect finding ([§ 12.1 follow-up](roadmap-done.md#follow-up-4-tt-default-size-in-v401--null-effect-at-tc-4060)): exposing the knob lets the user pick a TC-appropriate value instead of relying on a default that may be over- or under-dimensioned for their use case.

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
- Time management: at `go wtime 300000 btime 300000 movestogo 40` allocate roughly `wtime / (movestogo + safety)` for this move. `go movetime 5000` is trivial: that many seconds. *This is a flat per-move budget — no clock-aware time hoarding, panic mode, or complexity-based scaling; see [§ 12.12](roadmap.md#1212-real-time-management-heuristics--s--m--3060-elo).* **As built, it also adds 80 % of `winc`/`binc`** when the GUI sends one, capped by the remaining clock.
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

Once [§ 12.9 UCI](roadmap-backlog.md#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) is in place, [`cutechess-cli`](https://cutechess.com/) already covers the "full-game tournament" measurement. The harnesses below are **complementary**, not replacements — they are much **faster** signals during development (seconds, not hours) and surface *where* a change helped, which a tournament score does not.

In particular: once cutechess-cli is available, the self-play loop in [§ 12.10.3](roadmap-backlog.md#12103-self-play-tournament--m-1-day) becomes optional — keep it only if you want a zero-external-dependency fallback.

### 12.10.1 Node-count benchmark — **S, ½ day**

For every search-side change in this chapter (TT, LMR, null-move, ...) the most direct signal is: at the same depth, on the same position, **how many nodes did we visit?**

- Pick ~20 positions (mix of opening, middlegame, endgame).
- For each: search to a fixed depth, record the best move and `Statistics.getPositionsCount()`.
- Compare against a previously recorded baseline.

[`Statistics`](../src/main/java/org/michaelfl/mychess/Statistics.java) already collects everything needed. New code: ~30 LOC for a `BenchCommand` plus a hard-coded FEN fixture list.

What it doesn't catch: changes that produce a *different* best move (better or worse). For that, the next two harnesses are needed.

#### Concrete design — the `bench` command and its A/B equivalence use *(validated July 2026)*

Preferred form: a Stockfish-style `bench` REPL command (a new `Command` subclass registered in `CommandHandler`) that runs a fixed FEN suite at a fixed depth and prints, per position and in total, the node count (`Statistics.getPositionsCount()`), elapsed time, and NPS. Each build benchmarks *itself* — no external jars, no hard-coded paths. This turns a cross-build comparison into a trivial two-step: run `bench` on build A, run `bench` on build B, diff the output.

- **Node signature = equivalence oracle.** Node counts at a fixed depth are fully deterministic and bit-reproducible. Two builds that visit the identical node count on every position are eval- and search-identical; any divergence localizes a behavioral change. This is the strongest available "is this refactor neutral?" check.
- **Compare nodes, never time.** Wall-clock time is machine- and JVM-warmup-dependent and must never be an assertion — only an eyeballed NPS/overhead hint.
- **Mix quiet *and* sharp positions.** Eval divergences often surface only deep in the tree (e.g. a term gated on low material fires only after a few captures). Quiet positions alone can hide them; include tactical/open positions so any behavioral difference actually reaches a diverging node.
- **Overhead readings need a warm JVM.** Spawning a fresh JVM per search inflates the apparent cost of added per-node work on short searches (interpreter, no JIT); read overhead from the longest searches (or a warmed, in-process loop), not from sub-2-second ones.

Worked example (this technique in practice): the king-dependent pawn-PST refactor was proven eval-neutral by running this node-count comparison of the rebuilt engine against v4.2.1 across quiet + sharp middlegame positions. Once the incidental `isEndGame → kingMayBecomeActive` change was reverted, node counts were bit-identical on every position — while the sharp positions were exactly what had exposed the `kingMayBecomeActive` divergence (−60 and −506 nodes on two of four positions) in the first place (see § 12.21).

Optional companion: a `@Tag("slow")` JUnit test that pins the node signature as a regression tripwire (assert on nodes only; regenerate the baseline on any *intentional* search/eval change).

### 12.10.2 EPD test-suite runner — **S, 1 day** (assuming FEN-import from § 12.9 is in place)

**Status (2026-08-18) — the STS half is shipped; WAC is still open.** The order below was inverted on purpose: STS was built first because the search side is now stable (the PeSTO ceiling result in [roadmap § 12.7.1](roadmap.md) says the remaining lever is search, not tables) and because the immediate consumer is the king-safety defect family, which STS addresses directly through its 70-position *King Activity* theme. WAC measures tactics, which `bench` and the `EngineTest` regressions already cover.

What was built: [`Sts`](../src/test/java/org/michaelfl/mychess/Sts.java) (EPD parsing, search loop, per-theme scoring), [`StsRunner`](../src/test/java/org/michaelfl/mychess/StsRunner.java) plus `tools/run-sts.sh`, and `StsTest`. Both classes live in the **test** sources, matching every other measurement driver in this project — an STS score, unlike `bench`'s node signature, need not be reproducible from a shipped artifact, and this keeps 402 KB of third-party data out of the jar. Results and the measurement policy: [`docs/sts-history.md`](sts-history.md).

Four corrections to the description below, all learned in the build:

- **The tracked asset holds 1188 positions, not 1500.** The LAN v6 form keeps only positions where the best move leads the second by ≥ 10 cp.
- **Scoring is partial credit, not `Solved: 251/300`.** Each position lists up to ten candidate moves (`c9`) with a point value each (`c8`, best = 100); the engine earns the value of what it played. A move worth 46 is a different diagnosis from one worth 1, and binary scoring discards that resolution.
- **Fixed depth, not "allowed N seconds".** A time-based run is not reproducible across machines or under load. The cost is that the number is *not* comparable to published STS ratings, which are measured at fixed time.
- **A WAC runner is not free reuse.** `Sts.parseLine` requires `c8`/`c9`; WAC ships only `bm`, and in SAN rather than from-to notation. That needs a separate SAN comparison path — an extension point, not an inheritance.

[EPD (Extended Position Description)](https://www.chessprogramming.org/Extended_Position_Description) is FEN plus a `bm` ("best move") tag. The engine is given each position, allowed N seconds, and the proposed move is checked against `bm`. Score = % positions solved.

**Recommended starter suite: [WAC ("Win at Chess")](https://www.chessprogramming.org/Win_at_Chess) — 300 tactical positions.** It's the canonical hobby-engine benchmark for three reasons:

1. **Calibrated for myChess's expected strength.** A strong modern engine solves all 300 in seconds. A hobby engine at depth 6–8 typically scores 220–280/300, which is exactly the resolution band needed to see whether an optimization helped.
2. **Small and freely available.** ~25 KB, plain text, no licensing issue. Easy to embed under `src/test/resources/` or `data/`.
3. **Tactical focus matches what myChess will improve first.** The search optimizations in §§ 12.1–12.6 are tactical; WAC measures exactly that. Strategic suites (see below) make more sense after the search is solid.

When the WAC score plateaus, graduate to **[STS ("Strategic Test Suite")](https://www.chessprogramming.org/Strategic_Test_Suite) — 1500 positions in 15 themes** (open files, pawn structure, king safety, ...). STS gives a per-category breakdown, which directly tells you *which* evaluation component (§ 12.7) is weakest. STS is the right benchmark for measuring eval upgrades; it's worth the extra setup once the search side is stable. **Done 2026-08-18** — see the status paragraph above; the tracked LAN v6 asset holds 1188 of those 1500 positions.

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

### 12.10.4 PGN parser: ignore comments, NAGs, and variations — **XS, ~½ day**

myChess's `Pgn` parser currently rejects movetext annotations: a cutechess game like `3. e4 {+0.20/8 1.6s} d5 {book}` fails with `Wrong move notation: {book}`. This blocks reusing real-world PGN corpora (the `test-results/*.pgn` match archives) as position sources for the harnesses above — e.g. mining quiet middlegame FENs for the bench suite (§ 12.10.1) or building an EPD fixture (§ 12.10.2).

Make the SAN tokenizer skip:

- **Comments** — `{ … }` (brace) and `; …` (rest-of-line).
- **NAGs** — `$1`, `$2`, … glyph tokens.
- **Recursive variations** — `( … )` parenthesized sub-lines (nestable).
- The trailing **game-result** token (`1-0` / `0-1` / `1/2-1/2` / `*`).

Small, self-contained change in `Pgn` with unit tests over annotated fixtures; no engine impact. (Interim workaround used when mining bench positions: strip `{…}` with a regex before import.)

### Recommended order

1. **Build § 12.10.1 (node bench) first.** Half a day, immediate feedback on every search change.
2. **Build § 12.10.2 with WAC next**, after FEN-import lands (which § 12.9 needs anyway). Catches search-correctness regressions and tactical eval changes. — *Still open; STS was built first instead, see the status paragraph in § 12.10.2.*
3. **Build § 12.10.3 (self-play loop) third.** Slower per signal but the only of the three that measures end-to-end playing strength.
4. **Add STS later**, once the eval upgrades in § 12.7 begin. — **Done 2026-08-18**, and ahead of WAC: the eval upgrades of § 12.7 are through, so this step's precondition arrived before step 2's consumer did.
5. **§ 12.9 UCI on top of all this** validates the in-process numbers against external opponents — Stockfish at fixed-depth-1 is a well-known hobby-engine yardstick (~1500 Elo).

With (1)+(2)+(3) in place, every roadmap entry can be measured locally before merging. UCI becomes a sanity check, not a prerequisite.

## 12.11 ~~Chess960 (Fischer Random) support~~ — **DONE**

> **Shipped.** myChess plays Chess960: `UCI_Chess960` is handled in `UciHandler`, castling and
> the 960 start positions are implemented in `Board`/`Fen`, and the bot accepts the variant on
> lichess. Covered by `Chess960CastlingTest`, `Chess960StartPositionsTest`, `FenChess960ImportTest`
> and randomized 960 walks in `ZobristHashingTest`. The design record is
> [Chess960-project.md](Chess960-project.md); what remains open there is evaluation *tuning* for
> 960, not support — see § 12.11.1 below.

The entry is kept for the reasoning that shaped the implementation.

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

All three GUIs listed under [§ 12.9](roadmap-backlog.md#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) support Chess960:

- **HIARCS Chess Explorer Free** — full 960 support including setup of any of the 960 starting positions, FEN-based load, manual play.
- **Cute Chess / cutechess-cli** — `-variant fischerandom` for engine matches; setting up a 960 gauntlet is a single command-line flag.
- **Banksia GUI** — includes a one-click random-960-position generator alongside standard play.

### 12.11.1 Evaluation tuning for Chess960 — what transfers, what to re-measure

*Forward-looking. The primary user plays predominantly Chess960 against myChess, so 960 strength is a real target. This records which evaluation parameters transfer from standard chess and which are worth tuning or measuring specifically on 960.*

**PSTs are variant-agnostic — tune on standard, they transfer.** A piece-square table scores *where a piece stands*, not where it started. Center control, open-file and rank bonuses, and the entire endgame (king centralization, passed pawns) are identical in 960; even castling *targets* are the same (960 castling still lands the king on the c-/g-file squares), and the endgame is literally identical to standard chess. So the tapered-PST tuning of [§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy) on the standard Zurichess dataset produces tables that serve 960 as well — **no 960-specific PST dataset is needed.** The only divergent slice is the *middlegame king table* and early-development squares, because a standard dataset over-fits "the king ends on g1."

**Tune on standard, measure on 960.** Because PSTs transfer, keep tuning them on the standard 1.4M-position dataset — but run the *validation* SPRT under 960 conditions (`cutechess-cli -variant fischerandom` with 960 start positions; myChess already speaks `UCI_Chess960`) so the keep/discard decision reflects the variant actually played. This catches a change that helps standard chess but misfires in 960 (e.g. an MG king-table tweak that over-fits standard castling) without needing a 960 tuning dataset. cutechess is the *measuring device* here, not an optimizer — the optimizer is still the offline Texel tuner.

**The marquee 960 item — re-measure the shelved king-safety terms on 960.** Every king-safety attempt so far was killed on *standard* chess ([§ 12.21](roadmap.md#1221-king-safety--m--3060-elo): attacker-units −14.7, standalone pawn-shield −57.5, king-dependent PST −18.1). In standard chess the king is almost always safely castled on g1, so a king-safety term measures almost nothing and reads net-negative. **960 stresses king safety far more** — the king starts on a random file, is often exposed before castling, and there is no opening book to steer it to safety. It is entirely plausible that a term that is neutral-to-negative on standard is *positive* on 960. The term logic already exists in the archived branches; only the weight needs re-fitting. And because it is a handful of scalars — not hundreds of PST cells — this is the one case where **SPSA-style tuning directly against 960 games** is feasible (the games are the signal, so no labeled 960 dataset is required).

**A 960 profile for the few variant-sensitive scalars.** For the small set of genuinely 960-sensitive scalars — king-safety weight, and opening-phase time allocation (which matters more in 960 precisely because there is no book) — it makes sense to carry variant-dependent values switched on `UCI_Chess960`, rather than a second full evaluation. The bulk PSTs stay a single shared set.

**The largest 960 gap is not a tunable parameter at all.** myChess has no opening book for 960 (each start is unique), so it plays on search + evaluation from move 1 — exactly the phase where king safety and development decide games. That is why the king-safety re-test is doubly attractive: it targets the phase where myChess is most on its own, in the variant the user actually plays.

Summary — where each parameter class belongs:

| Parameter | Tune on | Measure on 960 |
|---|---|---|
| Bulk PSTs | standard (Texel, § 12.7.1) — they transfer | optional cross-check |
| MG king PST | standard; a true 960 tune needs a 960 dataset | yes (hand variants) |
| King-safety weights | SPSA-on-960 games (few scalars) or by hand | **yes** |
| Opening time management | SPSA-on-960 games | **yes** |
| Development / tempo term (if added) | standard | yes |

Feasibility note: game-based tuning (SPSA/CLOP) against 960 games is only practical for a few scalars; a labeled 960 dataset for Texel PST tuning would have to be generated via self-play — a separate project, and unnecessary given PST transferability.


## 12.22 Enable the opening book — deferred, with a measured motivation

**Status: deferred by choice.** The book exists at `db/openings.db.disabled`
(31 MB, built by [`OpeningDBImporter`](../src/main/java/org/michaelfl/mychess/openingdb/OpeningDBImporter.java)
from KingBase, `MAX_MOVE_DEPTH = 16` moves) and is deliberately switched off, to
first observe how myChess plays openings on its own. The lichess blitz rating of
**1953** (rd 77, 33 rated games, 2026-08-11) is therefore already the "without
book" datapoint.

### The observation that motivates it

Watching lichess games suggested myChess neglects development. Measured on the
game archive, counting knights and bishops still on their home squares:

| Dataset | after move 10 | after move 12 |
|---|---|---|
| **lichess**, no book (n = 71) | myChess 1.18 vs opponents 0.99 | 0.93 vs 0.70 |
| **cross-engine matches**, 8-ply cutechess book (n = 2371) | myChess 0.92 vs opponents 1.11 | 0.67 vs 0.84 |

The two datasets point in opposite directions, and the difference between them is
the **book**, not the opponents: in the match archive an 8-ply opening book is
imposed on both sides, on lichess myChess improvises from move 1 while its
opponents mostly use books. The cross-engine numbers are highly significant
(±0.04); the lichess ones are directional only (±0.21 at n = 71).

Consistent with that, the "buried piece" pattern appears only without a book: on
lichess **86 %** of myChess's undeveloped minors are blocked by its own pawn
(0.80 of 0.93) versus 73 % for its opponents, while in the book-driven matches it
sits at 68 %, *below* its opponents.

Two impressions were refuted. myChess makes **fewer** queen moves than its
opponents in the first 12 moves (0.80 vs 1.27), and it castles far more often
(95.8 % vs 32.4 %) — the `castlingFactor` is doing its job.

So the evaluation does not neglect development as a property; it improvises badly
when nothing constrains the opening. Enabling the book is therefore the cheap
lever, and adding a development term the expensive one — and § 12.7.2's lesson
(flat standalone terms measure neutral for this engine) argues against the latter.

### Two more opening signals, noted 2026-08-15 and not yet counted

The development metric above asks *whether* the minor pieces come out. A game from
the 4.4.1 anchor bracket suggests a second question worth counting: *where the pawns
go*. myChess as White vs TSCP (round 4 of `test-results/match-4.4.1-vs-tscp.pgn`,
lost) had **no pawn in the c4–f5 band from move 3 until move 14**, while Black built
up to three. Its first central pawn move, `14.d4`, is also the move at which the
position finally collapsed.

What makes it more than an anecdote is where Stockfish disagreed. On moves 7 and 8 it
wanted `d4` both times; myChess played `7.b6+` and `8.d3`, running the b-pawn to win a
pawn on a7. It rated that plan `+0.92 / +0.65 / +0.86` while the position was already
around −1 — roughly **two pawns of misjudgment in favor of the material grab**. The
knight tells the same story: `5.Ng5` where Stockfish wanted `Nd4`, then `11.Nh3` back
to the rim, where `Bxh3 gxh3` wrecked the kingside.

The hypothesis is therefore that **material outweighs placement in the opening**, the
same shape as [`corner-grab`](testing.md) and the material-only shortcut, one phase
earlier — which is notable because central preference is exactly what the PeSTO
tables are supposed to supply, and they were worth +32.6 Elo.

#### A second game, and the split that matters

Round 5 of the same match (myChess as Black, also lost) gives three more disagreements
of the same kind — and taking them apart shows they do **not** share a cause.

| move | Stockfish wants | myChess plays |
|---|---|---|
| 8… | `d5` (centre) | `Bh6` |
| 9… | `Nc6` (development) | `Be3` |
| 11… | `Nc6` (development) | `h6` |

The b8 knight does not move until move 15.

**The bishop excursion is not a preference — the evaluation is simply flat.** Comparing
the same position with the bishop on g7 and on e3 through the engine's own component
dump (`fen <FEN>` then `w` in the REPL):

| term | g7 | e3 |
|---|---:|---:|
| material | +1.00 | +1.00 |
| position (PSTs) | −0.83 | −0.79 |
| mobility | +0.06 | −0.05 |
| **total (White POV)** | **+0.80** | **+0.72** |

Eight hundredths of a pawn between them. The PeSTO tables actually prefer `g7` and are
outvoted by mobility, which is worth 0.11 here — six extra squares at the bishop's
mobility weight of 30, scaled by `mobilityFactor = 0.1`. With the options that close
together the choice falls to whatever the search turns up a few plies out.

**The real gap is one level down.** Stockfish rates that position −1.95 for White while
myChess's static evaluation says +0.80: nearly **three pawns apart before the bishop
moves at all**. The breakdown says why — White is a pawn up from `3.dxc5`, worth a flat
+1.00, and Black's entire compensation (centre, development, the kingside loosened by
`f3`/`g4`) registers as −0.20 across *all* positional terms combined.

**But `11…h6` is a different failure and must not be counted with the others.** It costs
3.9 pawns (+1.40 → −2.48) and looks like the same thing, yet the engine knows better:

| depth | move | score |
|---|---|---|
| 7 (what the game reached) | `h6` | −0.36 |
| 8 | `h6` | −0.82 |
| **10** | **`Nc6`** | −0.59 |

That is a horizon effect, not an evaluation defect — and an expensive one: depth 10 took
40 M nodes and 27 s where the time control allowed about 3 s.

#### What to count, and treat all of it as n = 2 until then

The first four plies of the round-4 game came from `2moves_v2.pgn`, and `2.Qa4` is a move
no engine would choose, so White was already misplaced before deciding anything itself.
Three counts would settle it, all over the existing archives and all far cheaper than an
Elo run:

1. **Central pawn presence by move 10 and 12**, myChess versus opponents, split by
   book / no-book exactly as the development table above is — the split is what showed
   that the development finding was about the book rather than the engine.
2. **Disagreement count**: positions in the first fifteen moves where Stockfish's best
   move is central or developing and myChess plays a flank pawn, a rim knight or a
   bishop excursion. If that rate is no higher than the opponents', it dissolves.
3. **Split every hit by cause** — re-search each disagreement at the game depth and at
   +3 plies. Hits the engine fixes on its own are search, not evaluation, and belong to
   the [search cluster](roadmap.md) rather than here. Without this split the two levers
   get averaged into one number that recommends neither.

### Measurement pitfall: MapDB takes an exclusive file lock

Anyone measuring book-vs-no-book must know this first.
[`OpeningDB`](../src/main/java/org/michaelfl/mychess/openingdb/OpeningDB.java)
opens the file with `DBMaker.fileDB(path).transactionEnable()` — no `readOnly()`,
no `fileLockDisable()` — so **only one process can hold it**. At
`-concurrency 4` cutechess runs four engine processes per side; one wins the lock
and the other three **silently fall back to search-only**, because
`MyChessMain.runUci` tolerates a locked book by design. The measured effect would
be diluted to roughly a quarter and read as "the book does nothing", with no error
message anywhere.

Run such a match at **`-concurrency 1`** (four times the wall-clock), or open the
database read-only first — an engine change, not a test-harness one.

### Suggested sequence when this is picked up

1. **Development metric first, not Elo.** It reaches significance far faster:
   ±0.04 at n = 2371 implies a clear signal at roughly 200 games, about half an
   hour at concurrency 1. It also tests the causal chain directly — if the book
   side develops better and the 86 % buried-piece figure collapses, the mechanism
   above is confirmed.
2. **Then an SPRT**, remembering that self-play *understates* a book: its value
   lies in avoiding known-bad lines against unfamiliar opponents, and the book
   side additionally suffers the "book exit" problem of leaving the book in
   positions it does not understand itself. A near-zero self-play result would
   therefore not mean the book is worthless on lichess.
3. Consider `plies=2` instead of the usual 8 for the external book, so the
   internal one has room to act — at the cost of less game variety.
