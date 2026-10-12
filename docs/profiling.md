# Profiling 4.8.2: JFR over bench v2 d8, cross-checked against the inlining log (roadmap row 3a)

Run 2026-10-09 06:17–06:20 (runner and raw files in the lab, `myChess-lab/scripts/profiling-4.8.2.sh` and `myChess-lab/results/profiling-4.8.2/`), build `versions/4.8.2`, which has the same engine as master as of `a57a7de`. Both runs reproduce the release signature **169,439,389** nodes, so the profiled search is exactly the release search.

- JFR `settings=profile`, 7841 execution samples (`bench-v2-d8.jfr`). Stacks were exported again with `--stack-depth 64` (`execution-samples-deep.txt`) and aggregated with `myChess-lab/scripts/jfr-hot-methods.py` into `hot-methods.txt`.
- PrintInlining + PrintCompilation in a separate run (`inlining.log`).

This is one run, so each share carries sampling noise of about ±0.7 percentage points at 10 % (binomial, 7841 samples). JFR attributes inlined code to its own Java method, so "self" here means the method's own bytecode whether or not the JIT inlined it.

## Where the time goes

Inclusive time, counting everything the method calls:

| incl % | method |
|---|---|
| 71.0 | `QuiescenceSearch.quiescenceSearch` |
| 43.8 | `QuiescenceSearch.calculatePositionWeight` → `WeightingFunction.calculate` (43.3) |
| 39.7 | `MoveGenerator.calculateMoves` |
| 16.1 | `StaticExchangeEvaluation.see`, via `MoveSorterImpl.captureWeight`, quiescence move ordering only |
| 8.4 | `PositionSearch.nmp` (null-move pruning subtree) |
| 6.4 | `Board.canCaptureOpposingKing` |

Self time, the method's own code:

| self % | method | note |
|---|---|---|
| 14.9 | `WeightingFunction.calculate` | 480 bytes, the evaluation's driver loop |
| 10.2 | `StaticExchangeEvaluation.findFirstNonEmptyField` | 22-byte ray-walk loop, always inlined; the cost is the walking |
| 10.0 | `WeightingFunction.xray` | slider mobility rays, inlined |
| 7.7 | `MoveGenerator.calculateMoves` | |
| 5.5 | `Board.isFieldAttackedBy` | 417 bytes, never inlined |
| 4.6 | `MoveGenerator.move` | |
| 4.2 | `MoveSorterImpl.getSortedMoves` | |
| 3.8 | `WeightingFunction.calculateUndefendedPiecesCount` | |
| **7.2** | `checkWhiteDoublePawn` 3.7 + `checkBlackDoublePawn` 3.5 | see finding 1 |
| 3.5 | `StaticExchangeEvaluation.see` | |
| 3.3 | `Board.makeMove` | |

## Findings

**1. The doubled-pawn check costs 7.2 % of the whole run.** For every pawn, `checkWhite/BlackDoublePawn` walks the rest of its file up to the board border, looking for a second pawn of its own color. That is up to six squares per pawn, sixteen pawns, in every evaluation. The term itself is minor. A per-file pawn count, filled in by the same piece loop that already visits every pawn, answers the question in O(1) per file and would remove most of this 7 %. If the count is exact, the node signature should not change, so a change of this kind can be checked against 169,439,389.

**2. SEE is 16 % of the run, and 10 % of that is plain ray walking.** `findFirstNonEmptyField` is already inlined everywhere it is hot. Its cost is the loop, not a call, so no inlining change can help it. SEE runs for every capture in quiescence move ordering (`MoveSorterImpl.captureWeight`), including captures whose MVV-LVA delta is already ≥ 0. There, SEE cannot turn a kept capture into a pruned one, but it does change the ordering value. So skipping it there changes the search and the signature, and would need a match rather than a bench.

**3. Methods over HotSpot's 325-byte hot-inlining limit** (`FreqInlineSize`), each reported as "hot method too big":

| bytes | method | self % |
|---|---|---|
| 480 | `WeightingFunction.calculate` | 14.9, mostly called from `calculatePositionWeight`, so a call either way |
| 437 / 429 | `MoveGenerator.isBlack/WhiteCastlingFieldUnderAttack` | < 0.5 each, not worth it |
| 434 | `PositionSearch.alphaBetaSearchPre` | recursive, the call stays anyway |
| 417 | `Board.isFieldAttackedBy` | 5.5 |
| 410 | `StaticExchangeEvaluation.collectCaptureMovesInto` | 1.6 self, 9.3 incl |
| 379 | `Board.calculateNewCastlingState` | small |

None of them sits just over the limit. The 4.8.1 trap was a method two bytes over, but here the nearest is 54 bytes over, so a cosmetic trim does not bring any of them back under. Only `isFieldAttackedBy` (5.5 %) is hot enough for an inlining fix to be measurable, and the call overhead on a 417-byte body is a small part of its cost.

**4. Small callees: mixed outcomes, one worth a look.** Most refusals in the log are C1 tier-3 decisions ("too big" at 35 bytes, "callee uses too much stack"). They say nothing about the C2 code that runs the bench. The C2 messages are a different matter:

- `MoveGenerator.addMove` (42 bytes; 2.2 % self, 18.7 % incl) is inlined "(hot)" only 11 times. C2 refuses it 29 times with "already compiled into a big/medium method": its compiled body is large because it pulls in `MoveSorterImpl.addMove` → `captureWeight` → SEE. At most of the call sites in the move generator, it therefore stays a real call.
- `increaseAttackUnit` (37 bytes), `capture` (83 bytes) and `knightMove` (40 bytes) are inlined "(hot)" at their hot sites. They get "callee is too large" only at sites C2 rates as cold, where the 35-byte `MaxInlineSize` applies.

The log interleaves several compiler threads, so these counts are indicative, not exact. Whether the `addMove` calls cost anything measurable could only be shown by a paired bench of a variant, not by this log.

## Allocation and GC (same recording, evaluated 2026-10-09)

The search allocates despite the project's no-allocation rule. It allocates about **450 MB/s**: 45 GB sampled over the 101 s run, which matches 663 young collections × ~68 MB. By allocating site:

| share | object | allocated in |
|---|---|---|
| 17.3 % | `GameStatus` | `GameStatus.switchTurn`, one per `makeMove` |
| 16.0 % | `int[]` | `IntArray.<init>`, move lists |
| 15.4 % | `SearchNodeContext` | `PositionSearch.alphaBetaSearchMain` |
| 11.6 % | `SearchNodeContext` | `QuiescenceSearch.quiescenceSearch` |
| 9.1 % + 6.0 % | `SearchNodeResult` | `negate`, `create` |
| 8.1 % + 7.9 % | `int[]` | `GameStatus.<init>`, `Arrays.copyOf` |

**What it costs, as far as this recording can say.** The garbage collector is cheap. Each young collection pauses for about 0.13 ms, so 663 of them add up to roughly 0.09 s, about **0.1 %** of the run. What a profile cannot show is the indirect cost: zeroing new objects, and the cache lines each new object pulls in. That cost lands in the allocating methods' own samples and cannot be separated from their real work. Only a variant that reuses the objects, measured with paired benches, can put a number on it.

**Candidate 5:** reuse `SearchNodeContext` / `SearchNodeResult` per ply, and avoid the per-move `GameStatus` and `int[]` copies. This is a structural change across the search and the board. The gain is unknown and may be small, given the 0.1 % GC share. The signature would stay unchanged if the change is exact.

## Ranking of the candidates for the owner

| | candidate | expected gain | behavior |
|---|---|---|---|
| 1 | per-file pawn count instead of the per-pawn file scan | up to ≈ 7 % | signature unchanged if exact; bench-provable |
| 2 | skip SEE for quiescence captures with MVV-LVA delta ≥ 0 | part of 16 % | ordering changes; needs a match |
| 3 | split `isFieldAttackedBy` under 325 bytes | small part of 5.5 % | signature unchanged |
| 4 | keep `MoveGenerator.addMove` inlinable, e.g. by moving the sorter call out of its compiled body | unknown, needs a paired bench | signature unchanged |
| 5 | reuse per-ply search objects instead of allocating them (≈ 450 MB/s today) | unknown; GC itself is only ≈ 0.1 % | signature unchanged |

At the roadmap's rule of thumb, 50–70 Elo per doubling, 7 % is worth roughly 5 Elo. These are suggestions only: production changes are the owner's.

## Inside the evaluation: hotspots by term (4.8.3, 2026-10-12)

Requested by the owner on 2026-10-09: the hot spots inside `WeightingFunction.calculate`, down to single terms where measurable. Runner and raw files are in the lab: `myChess-lab/scripts/eval-hotspots-4.8.3.sh`, `eval-hotspots-analyze.py`, `eval-hotspots-terms.py` and `myChess-lab/results/eval-hotspots-4.8.3/`.

**Setup.** Build `versions/4.8.3`, whose evaluation is byte-for-byte the one of 4.8.2: `WeightingFunction` and `PieceSquareTables` are unchanged. Bench v2 d8 ran three times in one JVM, and all three runs reproduce **169,439,389**. JFR `settings=profile` with `jdk.ExecutionSample#period=2ms` gave 126,173 samples on the search thread, 16 times the 2026-10-09 recording. The evaluation is **43.9 % ± 0.1** of them, which matches the 43.3 % measured above.

**Method.** Every sample whose stack contains `calculate` is assigned to exactly one group, by the frames below `calculate` (`eval-hotspots-terms.py` documents the order). So the shares add up to 100 % of the evaluation. ± is one binomial standard error.

| group | % of evaluation | % of run | what it is |
|---|---:|---:|---|
| driver loop | **33.4 ± 0.2** | **14.7** | `calculate`'s own code: the scan over the board, material, PST, phase, and the per-piece dispatch |
| slider rays (`xray`) | **25.4 ± 0.2** | **11.2** | bishop, rook and queen rays: mobility plus the attack units counted along them |
| doubled pawns | **17.8 ± 0.2** | **7.8** | `checkWhite/BlackDoublePawn`, a file walk per pawn |
| undefended pieces | **8.6 ± 0.1** | **3.8** | `calculateUndefendedPiecesCount`, a second pass over the board |
| knight | 5.9 ± 0.1 | 2.6 | `knightMove`, mobility and attack units of knights |
| king | 3.5 ± 0.1 | 1.5 | the king's own moves |
| pawn, other | 2.9 ± 0.1 | 1.3 | en passant, pawn captures and defense |
| slider wrappers | 1.5 ± 0.1 | 0.7 | the per-piece methods around `xray` |
| king-zone extra | 1.0 ± 0.0 | 0.4 | `isKingZoneField`, `increaseAttackUnit` and the attack-unit line in `xray`, where they show up as their own frames |

Castling (`calculateCastlingState`) and the final sum (`calculatePositionWeight`) get no measurable share. The 2026-10-09 recording, regrouped the same way, agrees within its larger noise on every group: doubled pawns 16.5 ± 0.6, slider rays 24.8 ± 0.7, driver loop 34.3 ± 0.8.

**What the numbers can and cannot separate.**
- **Mobility and attack units share one loop.** The attack units are counted inside the same ray walk that computes mobility. Only where the attack-unit code shows up as its own frame (`isKingZoneField`, `increaseAttackUnit`) can it be separated, and that is 1 % of the evaluation. The rest of the 8.41 % cost the second cost ladder measured for the term lives inside the slider-ray and knight groups. Sampling cannot pull it out; only a cost ladder like that one can.
- **Within the driver loop, the line split is unreliable.** JFR attributes 19.7 % of the evaluation to line 527 alone (`pstEndGameWeight[color] += …`), 11.6 % to the dispatch line 531, and 2.1 % to line 515. A single add getting a fifth of the evaluation is the usual skid of samples in compiled code onto a neighboring line. The group total is sound; how it splits between the board scan, the PST lookups and the dispatch is not.

**Findings, ranked by share of the run:**
1. **The driver loop, 14.7 % of the run.** It visits every square from a1 to h8 of the 12×12 array, border columns included, about 92 squares for at most 32 pieces. It also calls each piece through `calculationFunctions[piece].calculate(...)`. That call site has seven implementations, so the JIT cannot inline it. Both are structural costs that do not depend on what the evaluation computes. A piece list instead of the scan, or a `switch` on the piece kind instead of the interface call, attacks them. Which of the two pays more is not something this profile can decide, given the skid; a paired bench of each variant can, and both keep the signature unchanged.
2. **The slider rays, 11.2 %.** This is the core of mobility and of the attack units. It was already made cheaper in 4.8.2, and it is the work the terms exist for.
3. **Doubled pawns, 7.8 %.** This is finding 1 above, confirmed with 16 times the samples: a file walk per pawn for a minor term. A per-file pawn count filled by the driver loop removes it and keeps the signature unchanged.
4. **Undefended pieces, 3.8 %.** A second full pass over the board, only to count marked squares. The marks are complete only after the main loop, so the count cannot move into it as it stands, but the pass could visit the pieces instead of all squares.
5. **Knights, 2.6 %.** The unrolled knight moves of 4.8.2; nothing obvious left.

All candidates are for the owner, who writes production code. Each one should keep bench v2 at 169,439,389, and a paired bench decides whether it pays.

## JIT cold start: what a fresh JVM loses in the first moves (4.8.3, 2026-10-12)

Requested by the owner on 2026-10-10. lichess-bot starts a new engine process for every game, so every game begins on a JVM whose JIT has compiled nothing yet. Runner and data are in the lab: `myChess-lab/scripts/jit-cold-start.py`, `jit-cold-start-analyze.py` and `myChess-lab/results/jit-cold-start/`.

**Setup.** Build `versions/4.8.3` through its UCI wrapper, so the JVM flags are the production ones. Five of the bot's Lichess games, and in each the first ten positions with myChess to move. A fresh JVM plays them in order with `go movetime T`. Then the same JVM receives `ucinewgame`, which clears the TT and the iteration timings, and plays them again, so the second pass differs from the first only in the JIT state. Each game is repeated five times with a fresh JVM, at T = 1000 ms and T = 250 ms, for 50 JVM runs in total. The measure is the node count and depth of the last completed iteration, cold against warm. The interval is 95 % over the 25 runs per cell.

| move | 1000 ms: cold/warm nodes | depth cold / warm | 250 ms: cold/warm nodes | depth cold / warm |
|---:|---:|---:|---:|---:|
| 1 | 1.00 [1.00, 1.00] | 7.80 / 7.80 | **0.38 [0.22, 0.53]** | **5.44 / 6.48** |
| 2 | 0.94 [0.84, 1.05] | 7.60 / 7.72 | **0.59 [0.42, 0.77]** | **5.32 / 6.00** |
| 3 | 0.99 [0.98, 1.00] | 7.00 / 7.00 | 0.91 [0.75, 1.07] | 5.72 / 6.00 |
| 4 | 0.93 [0.86, 1.00] | 7.20 / 7.32 | 1.01 [0.74, 1.28] | 5.68 / 5.88 |
| 5 | 0.98 [0.93, 1.03] | 7.40 / 7.40 | 0.79 [0.49, 1.09] | 5.56 / 5.84 |
| 6–10 | 0.89 to 1.82, intervals cover 1 | ±0.2 | 0.88 to 1.44, intervals mostly cover 1 | ±0.2 |

**Reading.**
- **At one second per move, there is no cold-start loss.** The first move reaches the same depth with the same node count cold and warm. No later move differs by more than 0.12 ply on average.
- **At 250 ms, the first two moves lose about a ply.** Move 1 searches 38 % of the warm node count and reaches 5.4 instead of 6.5 plies; move 2 is at 59 % and 0.7 ply less. From move 3 on, the differences are within the noise.
- The ratios above 1 in later moves (up to 1.82 at move 9 at 1000 ms) are boundary effects of the time control. Whether one more iteration starts depends on an estimate that differs slightly between the two passes. They do not mean a cold JVM searches more.

**Consequence.** The bot's games give one to two seconds per move in the opening even in bullet: 1+0 gives about 1.9 s at move 1 under the default 30 moves to go. That is the 1000 ms case, where nothing is lost. The loss appears only at a quarter second per move, and then only for two moves. **Not material**, so the warm-up options are not pursued: a warm-up search before `uciok`, a Java 25 AOT cache, or keeping the engine process alive.
