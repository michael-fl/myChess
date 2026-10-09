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

## Ranking of the candidates for the owner

| | candidate | expected gain | behavior |
|---|---|---|---|
| 1 | per-file pawn count instead of the per-pawn file scan | up to ≈ 7 % | signature unchanged if exact; bench-provable |
| 2 | skip SEE for quiescence captures with MVV-LVA delta ≥ 0 | part of 16 % | ordering changes; needs a match |
| 3 | split `isFieldAttackedBy` under 325 bytes | small part of 5.5 % | signature unchanged |
| 4 | keep `MoveGenerator.addMove` inlinable, e.g. by moving the sorter call out of its compiled body | unknown, needs a paired bench | signature unchanged |

At the roadmap's rule of thumb, 50–70 Elo per doubling, 7 % is worth roughly 5 Elo. These are suggestions only: production changes are the owner's.
