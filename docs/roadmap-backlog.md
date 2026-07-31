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
| `go [movetime N \| wtime N btime N \| depth N]` | GUI → engine | start `nextMoveAsync`, write `bestmove` when done |
| `stop` | GUI → engine | `NextMoveTask.cancel()` |
| `bestmove e2e4` | engine → GUI | the result of `go` |
| `quit` | GUI → engine | exit |

Optional `info depth … nodes … pv …` lines during search make the GUI's analysis panel light up but aren't strictly required to play. Now feasible as follow-up work since both prerequisites have landed: `setoption name Hash` (TT is now in master, [§ 12.1](roadmap-done.md#121-transposition-table--done-93-elo)) and `setoption name UCI_Chess960` (Chess960 is in master, [§ 12.11](roadmap-backlog.md#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant)). `ponder` remains out of scope for a first version. The `Hash` option specifically is motivated by the v4.0.1 null-effect finding ([§ 12.1 follow-up](roadmap-done.md#follow-up-4-tt-default-size-in-v401--null-effect-at-tc-4060)): exposing the knob lets the user pick a TC-appropriate value instead of relying on a default that may be over- or under-dimensioned for their use case.

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
- Time management: at `go wtime 300000 btime 300000 movestogo 40` allocate roughly `wtime / (movestogo + safety)` for this move. `go movetime 5000` is trivial: that many seconds. *This is a flat per-move budget — no clock-aware time hoarding, panic mode, or complexity-based scaling; see [§ 12.12](roadmap.md#1212-real-time-management-heuristics--s--m--3060-elo).*
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

All three GUIs listed under [§ 12.9](roadmap-backlog.md#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) support Chess960:

- **HIARCS Chess Explorer Free** — full 960 support including setup of any of the 960 starting positions, FEN-based load, manual play.
- **Cute Chess / cutechess-cli** — `-variant fischerandom` for engine matches; setting up a 960 gauntlet is a single command-line flag.
- **Banksia GUI** — includes a one-click random-960-position generator alongside standard play.

### 12.11.1 Evaluation tuning for Chess960 — what transfers, what to re-measure

*Forward-looking. The primary user plays predominantly Chess960 against myChess, so 960 strength is a real target. This records which evaluation parameters transfer from standard chess and which are worth tuning or measuring specifically on 960.*

**PSTs are variant-agnostic — tune on standard, they transfer.** A piece-square table scores *where a piece stands*, not where it started. Center control, open-file and rank bonuses, and the entire endgame (king centralization, passed pawns) are identical in 960; even castling *targets* are the same (960 castling still lands the king on the c-/g-file squares), and the endgame is literally identical to standard chess. So the tapered-PST tuning of [§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy) on the standard Zurichess dataset produces tables that serve 960 as well — **no 960-specific PST dataset is needed.** The only divergent slice is the *middlegame king table* and early-development squares, because a standard dataset over-fits "the king ends on g1."

**Tune on standard, measure on 960.** Because PSTs transfer, keep tuning them on the standard 1.4M-position dataset — but run the *validation* SPRT under 960 conditions (`cutechess-cli -variant fischerandom` with 960 start positions; myChess already speaks `UCI_Chess960`) so the keep/discard decision reflects the variant actually played. This catches a change that helps standard chess but misfires in 960 (e.g. an MG king-table tweak that over-fits standard castling) without needing a 960 tuning dataset. cutechess is the *measuring device* here, not an optimizer — the optimizer is still the offline Texel tuner.

**The marquee 960 item — re-measure the shelved king-safety terms on 960.** Every king-safety attempt so far was killed on *standard* chess ([§ 12.21](roadmap.md#1221-king-safety--m--3060-elo): attacker-units −14.7, standalone pawn-shield −57.5, king-dependent PST −18.3). In standard chess the king is almost always safely castled on g1, so a king-safety term measures almost nothing and reads net-negative. **960 stresses king safety far more** — the king starts on a random file, is often exposed before castling, and there is no opening book to steer it to safety. It is entirely plausible that a term that is neutral-to-negative on standard is *positive* on 960. The term logic already exists in the archived branches; only the weight needs re-fitting. And because it is a handful of scalars — not hundreds of PST cells — this is the one case where **SPSA-style tuning directly against 960 games** is feasible (the games are the signal, so no labeled 960 dataset is required).

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

