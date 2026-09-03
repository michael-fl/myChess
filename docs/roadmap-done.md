# 12b. Roadmap — Completed & Investigated

Companion to [the main roadmap](roadmap.md). This file collects items that are **shipped** or **closed after investigation** — including neutral/negative results, which are kept because the reasoning prevents repeating the same experiments. Section numbers (§ 12.x) are **stable IDs** and match every reference across the docs. See the [master index](roadmap.md#roadmap-index) in the main roadmap for the full picture.

---

## 12.1 ~~Transposition table~~ — **DONE (+93 Elo)**

*Implemented and merged June 2026. Two early-stopping self-play SPRTs and one 1600-game fixed-N match against `myChess-3.6.0`, TC 40/60. The precise reference number is the fixed-N match: **+92.7 ± 15.2 Elo** at 1600 games, LOS 100 %. Released as `v4.0.0`.*

The single biggest missing optimization, and the one the README already flagged. The transposition table (TT) caches per-position search results keyed by Zobrist hash, so positions reached through different move orders are evaluated once.

- The hash already existed ([`Board.calculatePositionKey()`](../src/main/java/org/michaelfl/mychess/Board.java), [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding)).
- Each TT entry stores `{key, depth, score, bestMove, bound}` where `bound ∈ {EXACT, LOWER, UPPER}`. Layout evolved across the v4.0.x line: v4.0.0 shipped as a single-slot in-heap `TTEntry[]` array with depth-preferred-EXACT replacement; v4.0.2 refactored the layout into 4-slot buckets keeping the same replacement principle (see the "TT bucket replacement strategies" follow-up below); v4.0.3 rewrote the storage to one off-heap `MemorySegment` per table with a reusable `TTEntryView` over 24-byte records, preserving the 4-slot bucket semantics and delivering **+15.6 ± 9.8 Elo** vs v4.0.2 (3200-game fixed-N, LOS 99.9 %) — attributed to cache-locality gains (~1.5 cache lines per bucket scan vs 5-8 for the reference-indirected in-heap layout) and reduced Serial-GC card-table work.
- The TT also feeds [§ 7.1 best-known-move ordering](search.md#71-best-known-move-pv-ordering): on a TT hit, try the stored `bestMove` first — strictly more informed than the previous-iteration PV alone (see [§ 7.8 Move sorting](search.md#78-move-sorting-sortablemovesbucket) for the `ttMove` integration).
- Wire-in points: [`PositionSearch.alphaBetaSearchPre`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) (probe at node entry, store on exit) and `MoveSorterImpl` (accepts a TT move via the `ttMove` hint).

Caveats handled in the shipped implementation: TT is cleared on `ucinewgame` via [`UciHandler.handleNewGame`](../src/main/java/org/michaelfl/mychess/UciHandler.java); mate-score adjustment by ply on store/probe is encapsulated in [`WeightingFunction.scoreToTT` / `scoreFromTT`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) (the sign-loss bug surfaced through `GameStatusTest.testWhiteCheckmate` during development and is now locked in by `ScoreTTAdjustmentTest`); parallel search remains out of scope. See [§ 7.9 Transposition table](search.md#79-transposition-table) for the full technical reference.

**Latent Zobrist-drift bug fixed in v4.0.7 (post-hoc).** Throughout the entire v4.0.x TT line the incremental position hash could drift from a from-scratch recomputation: only `Board._makeNormalMove` cleared the previous ply's en-passant hash contribution, so a pawn double-step followed by a promotion, castling, or en-passant capture left a stale en-passant random XOR-ed into the hash. Because a drifted hash produces false TT hits (cached scores / best-moves from unrelated positions), the +92.7 / +9.3 / +15.6 Elo measurements for v4.0.0 / v4.0.2 / v4.0.3 were all taken with this noise/bias source active — the true TT contribution may be marginally larger than those numbers. The fix (v4.0.7, `ccfa2ab`) centralizes the clear in a shared helper called from every `make*Move` path; it merged as a correctness fix with no isolated SPRT. Regression coverage: [`PositionHashConsistencyRegressionTest`](../src/test/java/org/michaelfl/mychess/PositionHashConsistencyRegressionTest.java) (perft-style + randomized Chess960 self-play hash-consistency checks) and the canonical [Perft suite](testing.md) (which surfaced the count discrepancy en-passant positions produce). See [version-history § 4.0.4–4.0.7](version-history.md) for the propagation implications.

### What was measured

Three self-play matches against `myChess-3.6.0`, TC 40/60:

| Run | Type | Band (`elo0`..`elo1`) | W-L-D | Games | Elo | LOS | Termination |
|---|---|---|---|---|---|---|---|
| 1 | SPRT | `-3 .. 15` | 117-67-43 | 227 | **+77.8 ± 41.6** | 100% | H1 at ubound (14% budget) |
| 2 | SPRT | `20 .. 80` | 133-81-54 | 268 | **+68.3 ± 37.8** | 100% | H1 at ubound (17% budget) |
| 3 | **Fixed-N** | — | **817-400-383** | **1600** | **+92.7 ± 15.2** | **100%** | full budget — precise reference |

All three estimates are consistent within their CIs, but the **fixed-N match is the reference**: it has the tightest CI (±15.2 vs ±37-42 for the SPRTs), and SPRT point estimates are known to be biased low by the early-stopping mechanism (the test stops as soon as evidence for H1 is sufficient, which can be well before the sample mean has converged on the true value — both SPRT runs here terminated at sample means 15-25 Elo below the eventual fixed-N point).

**White-vs-Black asymmetry.** The early SPRT runs showed near-symmetric W/B splits (~60-62 % both sides). The 1600-game fixed-N match exposes a ~62 Elo gap: kandidat as White wins 66.9 %, as Black 59.1 %. At 800 games per color the per-color CI is ±20-25 Elo, so the gap is ≈2.5σ — suggestive but not airtight. Most plausible reading: the TT amplifies the small first-move advantage that already existed pre-TT (the W>B baseline-bias investigated in [§ 12.14](roadmap-done.md#1214-color-asymmetry-investigate-the-wb-bias-seen-in-cross-version-matches--s-evidence-weakening)) — both sides get a stronger search, but the side that starts from slightly better positions converts that into a slightly larger fraction of wins. Worth re-checking after [§ 12.2 NMP](roadmap-done.md#122-null-move-pruning--done-76-elo) and [§ 12.6 QSearch upgrade](roadmap.md#126-quiescence-search-upgrade) land; if the asymmetry persists at >2σ, it deserves a separate investigation entry.

**Draw ratio** dropped to 19-24 % across the three runs (vs ~30 % in pre-TT baselines) — TT cutoffs decide games earlier.

### What we learned

1. **Magnitude is at the upper end of literature numbers for a first TT.** The pre-implementation estimate was "150–300 Elo" from chess-programming literature, but myChess already had reasonable best-known-move ordering ([§ 7.1](search.md#71-best-known-move-pv-ordering)) and killer-move heuristics ([§ 7.2](search.md#72-killer-moves)). The measured +93 Elo is consistent with the picture: a first TT in an engine with prior PV-ordering captures the *sibling-path* ordering gains plus genuine score cutoffs, but not the additional 60–100 Elo a TT would buy on top of a no-ordering baseline. The pre-test prediction of "+50 with +30-80 range" turned out to be too conservative — the TT/PV-ordering synergy outperformed the rough estimate by ~10-15 Elo.

2. **The two SPRT bugs that surfaced during development are worth remembering.** Both surfaced through *integration* tests, not the TT-specific unit tests written alongside the implementation:
   - The mate-score sign-loss in `scoreFromTT` only fired through `GameStatusTest.testWhiteCheckmate` — a far-from-TT test. Lesson: when a new layer touches scores, the existing end-game suite is the canary.
   - The stale-PV propagation through TT-cached early returns only fired through `EngineSmokeTest.testPosition1` (illegal-PV emission). Lesson: PV-table invariants under early-return code paths need explicit `truncate` / `writeTTCachedPv` helpers, not implicit "the next iteration will overwrite anyway" reasoning.

   Both are now covered by dedicated regression tests (`ScoreTTAdjustmentTest`, `IllegalPvRegressionTest`).

3. **Per-process JVM-wrapper tuning matters under `concurrency=4`.** Fixed `-Xms256m -Xmx256m`, `-XX:+AlwaysPreTouch`, `-XX:+UseSerialGC` on the test-version wrappers stopped heap-resize stalls and GC-thread contention that would otherwise have caused time-loss artifacts in the SPRT. The flags were added to **both** TT-version and baseline-3.6.0 wrappers to keep the comparison fair.

4. **SPRT is a hypothesis test, not an estimation tool.** Both early SPRT runs accepted H1 with point estimates (+78, +68) noticeably below the eventual fixed-N reference (+93). This is *not* a bug in either test — it is the expected behavior: SPRT stops as soon as the data is *sufficient* to reject H0 in favor of H1, which happens long before the sample mean has converged. For a precise Elo number, run a fixed-N match (no `-sprt` block); for a "is it better?" decision, run SPRT. Trying to "tighten" SPRT by shifting the band (e.g. `elo0=20 elo1=80`) does *not* extend the test meaningfully when the true effect is well above the band — the LLR still terminates early. This finding is now reflected in the `reference_sprt_cutechess_template` memory and the `match-<slug>` naming convention for fixed-N runs.

### Why this slot in the roadmap

Closes the largest remaining single-feature item with a clean +93 Elo measurement and unblocks several downstream items that pair with TT — most importantly [§ 12.2 Null-move pruning](roadmap-done.md#122-null-move-pruning--done-76-elo) (whose reduced-depth search now hits a populated TT and can cut off immediately), [§ 12.3 LMR](roadmap.md#123-late-move-reductions-lmr--s--50100-elo) (where the TT-stored bestMove is the first re-search candidate), and [§ 12.8 Aspiration windows](roadmap.md#128-aspiration-windows--s--2040-elo) (where TT bounds become the natural source for the next iteration's window).

### Follow-up: 4× TT default size in v4.0.1 — null effect at TC 40/60

The initial TT shipped with `DEFAULT_SIZE = 2^20` (1 M entries, ~24 MiB of TT record storage after the `MemorySegment` rewrite). Analysis suggested this was being overwritten ~30-60× per game at TC 40/60, with the depth-preferred-EXACT replacement protecting the most valuable entries but the mid-depth signal getting evicted. Predicted gain from quadrupling to `2^22` (4 M entries, ~96 MiB of TT record storage): **+10-15 Elo**.

**Measured: +1.1 ± 14.5 Elo over 1600 games** (SPRT `elo0=-3 elo1=15` ran the full budget without accepting either bound — LLR drifted toward H0 at -1.63 by the end; draw ratio 27.8 %; W/B asymmetry within noise at ±20 Elo per color). The TT enlargement produces **no measurable strength change** at this TC.

**What the null result actually means:**

1. **The depth-preferred-EXACT policy is more effective than the eviction-rate alone suggests.** A 1 M-entry table being overwritten 30-60× per game still preserves the high-depth EXACT entries that matter for cutoffs; the mid-depth entries that get evicted apparently weren't carrying enough Elo to show up in a 1600-game match.
2. **TC 40/60 doesn't produce enough unique meaningful positions to saturate even a 1 M-entry table's *working set*.** At ~750 k nodes per move with ~60 moves per game, the cumulative unique-position count after transposition dedup is ~12-24 M, but the *working set* (positions that get revisited within the same game's search horizon) is much smaller — closer to 1 M.
3. **The 4 M version isn't worse either.** No regression, no GC-pause artifact from the larger heap (verified via per-process RSS during the SPRT). It's purely a no-op at this TC.

**Decision: keep 4 M as the default.** The harmless-at-this-TC result doesn't generalize to longer TCs (where the search tree grows enough to *use* the extra capacity). A ~96 MiB off-heap TT record store is fine on any modern desktop, and still leaves plenty of room below the common 256-512 MB range that tournament-quality engines deploy with. Reverting to 1 M would only matter on memory-constrained setups (mobile, browser), which is not myChess's target.

**Follow-up: UCI `Hash` option** is the proper long-term solution — let the GUI configure TT size per use case. Currently in [§ 12.9.2 `UciHandler` (1 day)](roadmap-backlog.md#1292-ucihandler--1-day), the `setoption` handling is minimal; adding a `Hash` option (parse MB value, close and recreate the singleton TT) is ~10-15 lines plus a small TranspositionTable refactor. This becomes more attractive once we know that for TC 40/60 the natural default differs from TC 40/300+ — instead of guessing, expose the knob.

**Methodological note:** the prediction "+10-15 Elo from 4× TT size" was wrong by an order of magnitude. The Sättigungs-Kurve from chess-programming literature was extrapolated from engines with weaker replacement policies (always-replace) where bigger TTs help more. For myChess with depth-preferred-EXACT, the saturation point at TC 40/60 is already at ~1 M, not at the 4-16 M I had estimated. Future similar predictions for TT-related parameters should account for the replacement-policy interaction explicitly.

### Follow-up: TT bucket replacement strategies — explored, depth-only chosen

Between 2026-06-20 and 2026-07-01, we explored whether splitting each TT hash slot into a 4-entry bucket with a smarter replacement policy would out-perform the v4.0.1 baseline (single-slot, depth-preferred-EXACT). Nine variants were implemented on separate branches and eight were measured; results converge on the finding that **any depth-aware bucket policy is worth ~+9 Elo over single-slot, but additional replacement-policy complexity beyond that does not measurably pay off** at TC 40/60.

**Variants explored** (all `tt-bucket-*` branches in the repo, kept for reproducibility):

| Branch | Replacement strategy in one line |
|---|---|
| `tt-bucket-depth` | 4-slot bucket, evict min-depth (EXACT wins ties) — **simplest** |
| `tt-bucket-depth-generation` | 4-slot, replacementScore = `4·depth + exactBonus − agePenalty` |
| `tt-bucket-depth-generation-lru` | as above, `hitcount` as tie-break |
| `tt-bucket-age` | evict oldest, depth as tie-break |
| `tt-bucket-hitcount` | evict lowest hitCount, depth as tie-break |
| `tt-bucket-two-tier` | 1 recent-slot + 3 protected-slots, `depth > 1 \|\| EXACT` qualifies for protected |
| `tt-bucket-two-tier-admission` | as above + admission gate: new entry must be ≥ replaced protected slot |
| `tt-bucket-two-tier-admission-hitcount` | **8-slot bucket** (4 recent + 4 protected), admission gate + hitcount-based protection promotion — **most complex, and the only variant with BUCKET_SIZE ≠ 4** |
| `tt-bucket-memory-segment` | structural refactor to `MemorySegment` storage — unmeasured, orthogonal to policy question |

**Measurement matrix vs `v4.0.1` baseline** (SPRT `elo0=-3 elo1=10`/`15`, 1600 games, TC 40/60):

| Branch | Δ Elo | CI | LOS | LLR |
|---|---|---|---|---|
| `tt-bucket-depth` | **+9.3** | ±13.9 | 90.5 % | 1.50 |
| `tt-bucket-depth-generation` | +9.3 | ±14.2 | 90.1 % | 1.45 |
| `tt-bucket-depth-generation-lru` | +8.9 | ±14.0 | 89.4 % | 1.38 |
| `tt-bucket-two-tier` | +8.0 | ±13.9 | 87.1 % | 1.16 |
| `tt-bucket-two-tier-admission` | +11.1 | ±14.2 | 93.7 % | 1.88 |
| `tt-bucket-two-tier-admission-hitcount` (run 1) | +11.7 | ±14.3 | 94.6 % | 2.00 |
| `tt-bucket-two-tier-admission-hitcount` (run 2, confirmation) | +6.3 | ±14.0 | 81.0 % | 0.71 |
| **`admission-hitcount` pooled (3200 games)** | **~+9.0** | ~±10 | ~95 % | — |
| `tt-bucket-age` | −0.2 | ±14.5 | 48.8 % | −0.89 |
| `tt-bucket-hitcount` (aborted at 1240) | −0.3 | ±16.0 | 48.6 % | −0.74 |

**Three head-to-head tests** (bucket variant vs bucket variant, not vs baseline):

| Test | Games | Δ Elo | CI | LOS | Verdict |
|---|---|---|---|---|---|
| `admission` vs `two-tier` | 1462 | −9.3 | ±14.6 | 10.7 % | **H0 accepted** — admission gate hurts vs plain two-tier |
| **`admission-hitcount` vs `depth`** | **3200** | **+5.9** | **±9.8** | 87.9 % | **inconclusive (below 95 %)** — nominal lean toward complex variant, not statistically decisive. **Confound:** `admission-hitcount` uses BUCKET_SIZE = 8, `depth` uses BUCKET_SIZE = 4 — the test measures policy *and* bucket geometry together, cannot attribute the +5.9 cleanly to one factor. See Finding 5 for the follow-up disentanglement. |
| **`depth(8)` vs `depth(4)`** | **3200** | **−3.4** | **±9.9** | 25.3 % | **inconclusive, nominally negative** — bucket size 8 does not help at fixed policy (`depth`), if anything slightly hurts. Resolves the bucket-geometry confound in the row above: the +5.9 there cannot come from bucket size and must be a policy effect. See Finding 5. |

**Findings.**

1. **Depth-aware bucketing gives ~+9 Elo over single-slot.** Six independent depth-aware variants cluster in the +8-12 Elo band vs baseline. Six matched positive measurements are not random — this is a real signal from splitting the single-slot bucket into 4 slots with depth-based eviction.

2. **Beyond depth-awareness, additional replacement-policy complexity does not measurably pay off at current sample sizes.** The strongest head-to-head test (simplest `depth` vs most complex `admission-hitcount`) produced +5.9 ± 9.8 Elo — LOS 87.9 %, below the 95 % threshold for a confident positive. The 3200-game trajectory shows the point estimate wandering between +5 and +10 without clean convergence. The true effect is plausibly in the +3 to +8 Elo range, but that's small enough that the code-complexity cost outweighs it at this stage of the engine's development.

3. **Aggressive admission control (without hitcount) is *harmful*.** The `two-tier-admission` vs `two-tier` head-to-head accepted H0 at −9.3 ± 14.6 Elo — a clear regression. The unconditional admission gate discards entries that later turn out to have been useful. The `two-tier-admission-hitcount` variant partially rescues this by only promoting entries with `hitcount > 1` — but the net gain over `depth` is marginal.

4. **Age-only and hitCount-only replacement don't work.** Both variants that dropped `depth` as the primary key landed at ~0 Elo vs baseline. Depth is the load-bearing signal for TT-entry value; other axes (age, hit frequency) are at best tie-breakers.

5. **Bucket size 8 does not help at fixed policy** (`depth(8)` vs `depth(4)` = −3.4 ± 9.9 Elo, LOS 25.3 %, 3200 games — added 2026-07-03). At fixed 2²² total TT capacity, `BUCKET_SIZE = 8` halves the number of buckets, which raises the hash-collision rate; the extra slots per bucket do not compensate. This resolves the confound flagged in Finding 2: the +5.9 Elo of `admission-hitcount(8)` over `depth(4)` cannot come from bucket geometry (which is neutral-to-negative) and must be attributable to the replacement policy. Naive additive decomposition: policy effect at fixed bucket size ≈ +5.9 − (−3.4) = **+9.3 Elo**. The `admission-hitcount` policy is doing more work than the raw head-to-head suggested — the wider bucket was handicapping it. However, both underlying measurements have LOS below 95 %, so the derived +9.3 Elo carries a combined CI of roughly ±14 Elo — informative but still not statistically decisive. This confirms the "depth-only" merge decision *and* strengthens the case for a serious `admission-hitcount` re-evaluation once search-side features change the TT-access profile (see Re-test plan below).

**Decision.** Merged `tt-bucket-depth` as the production TT in `v4.0.2` (2026-07-03, merge commit on master). The simplest depth-aware bucket policy captures essentially all of the measurable Elo benefit at TC 40/60, keeps the replacement logic to ~10 lines, and does not add fields to `TTEntry`. The more complex variants are kept as branches for reproducibility but not merged.

**Re-test plan after search-side features land.** The head-to-head `admission-hitcount` vs `depth` (+5.9 ± 9.8, currently ambiguous) is worth **re-running** once §12.2 NMP, §12.3 LMR, and §12.6 QSearch upgrade are in master. Those three features materially change what the TT sees: NMP adds many reduced-depth searches, LMR adds re-searches at partial depth, and QSearch-all-captures changes the leaf-node profile. The optimal replacement policy in the post-NMP/LMR/QSearch world may differ from what's optimal today. Concretely:

- Baseline for the re-test = post-NMP/LMR/QSearch master (whatever version that is)
- Candidate = `tt-bucket-two-tier-admission-hitcount` rebased onto that baseline
- Same SPRT setup: 3200 games, TC 40/60, `elo0=-3 elo1=10`
- If the re-test lands at a decisive positive (LOS ≥ 95 %, Δ ≥ +10 Elo): merge `admission-hitcount` at that point
- If it lands at neutral or negative: `depth` stays the production TT and `admission-hitcount` is retired
- **Bucket-geometry 2×2 status (updated 2026-07-03):** three of four cells now measured. Bucket size 8 is at best neutral at fixed policy — closes off option (b) from the original plan below.

  |  | BUCKET_SIZE = 4 | BUCKET_SIZE = 8 |
  |---|---|---|
  | **Policy: `depth`** | ✓ measured (+9.3 vs single-slot) | ✓ measured (−3.4 vs `depth(4)`) |
  | **Policy: `admission-hitcount`** | ✗ **still unmeasured** | ✓ measured (+9.0 pooled vs single-slot) |

  The one remaining cell (`admission-hitcount(4)`) is the cleanest way to measure the pure policy effect at aligned bucket size. Absorb it into the post-search-features retest: rebase `tt-bucket-two-tier-admission-hitcount` onto the post-NMP baseline **with `BUCKET_SIZE` reduced to 4**, then run head-to-head against post-NMP `tt-bucket-depth`. Same SPRT setup: 3200 games, TC 40/60, `elo0=-3 elo1=10`.
- Post-retest merge options are now binary:
  - **(a)** if `admission-hitcount(4)` vs `depth(4)` lands at LOS ≥ 95 % with Δ ≥ +10 Elo: merge `admission-hitcount(4)` as the next-generation TT
  - **(b)** if it lands neutral or negative: `depth(4)` stays production and `admission-hitcount` is retired

**Not-yet-measured:** the `tt-bucket-memory-segment` branch is a structural refactor (MemorySegment storage instead of `TTEntry[]`) that is orthogonal to the replacement-policy question. It would need its own SPRT once the policy decision is settled — currently deferred.

**Archived branches.** All eight `tt-bucket-*` branches are kept in the repo (not deleted) as historical datapoints for this investigation. They are not maintained against master and will bit-rot naturally as the codebase evolves; if a future rebased comparison is needed, they can be updated at that time.

### Follow-up: reconstruct the principal variation from TT walks

The initial TT implementation (June 2026) ships with a known side-effect on the principal variation: when an EXACT TT hit serves a node's result, the PV row at that depth is set to `[ttBestMove, 0, 0, ...]` and copied up to the parent (via [`SearchNodeContext.writeTTCachedPv`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java)). The PV therefore terminates one ply after the TT-cached node, even when the actual search depth was full. Concretely, after a depth-8 search the engine sometimes emits a 4–6-ply PV; the *played move* and *score* are unaffected.

The fix used by most engines: after the search finishes, extend the root PV by walking the TT — apply the root's `bestMove`, look up the resulting position's `bestMove`, apply, repeat until either a miss or a repeat. Each step requires only a TT lookup plus a `makeMove`/`revertMove` pair, so the cost is negligible compared to the search itself. Stop conditions:

- Lookup returns `null` (no entry for the resulting position).
- The walked depth reaches the search's `maxDepth`.
- The same position recurs (cycle guard).

The relaxed PV-length check in [`EngineTestBase.testPosition`](../src/test/java/org/michaelfl/mychess/EngineTestBase.java) (June 2026) tolerates the current short-PV behavior; once TT-walk reconstruction is in, that relaxation can stay or tighten — either way it'll keep working.

Now that §12.1's base implementation is measured (+93 Elo, see header), this becomes the natural next refinement on the TT path. The motivation remains purely diagnostic / cosmetic (UCI `info pv` lines), not strength.

## 12.2 ~~Null-move pruning~~ — **DONE (+76 Elo)**

*Implemented and merged as `v4.1.0` (July 2026). A 3200-game fixed-N match vs `v4.0.7`, TC 40/60, measured **+76.0 ± 10.1 Elo, LOS 100 %** (draw ratio 31.5 %) — the second-largest single-feature jump after the TT, squarely inside the 50–100 estimate.*

Pass the turn to the opponent and search the reply with reduced depth. If the result still exceeds beta, the original position is so good for the side to move that a real move can only confirm it — return beta.

- One conditional branch inside the recursive node, plus a `Board.switchTurn()` / restore pair (no piece is moved). The `GameStatus` stack already supports a turn flip via [`GameStatus.switchTurn()`](../src/main/java/org/michaelfl/mychess/GameStatus.java).
- Disable when the side to move is in check or has only pawns + king (avoid zugzwang). The existing `isEndGame()` heuristic is too crude — gate on actual non-pawn material instead.
- Pairs naturally with [§ 12.1 TT](roadmap-done.md#121-transposition-table--done-93-elo): TT cutoffs from the reduced-depth search return immediately.

### What was implemented

`R = 2` fixed; a zugzwang guard on the new `GameStatus.hasNonPawnMaterial()` (incremental per-side non-pawn material was added alongside, replacing the crude `isEndGame()` gate); a `lastMoveWasNull` flag on `SearchNodeContext` forbidding consecutive null moves; and a null-window reduced search (`-β, -β+1`) that only fires when the reduced child still gets ≥ 2 plies (`MIN_CHILD_DEPTH`). Dedicated `makeNullMove()` / `revertNullMove()` on `Board`, covered by `BoardNullMoveTest`. No `isKingChecked()` guard — illegal null-move positions fall out via the `isIllegal` path in the reduced search, which the match showed costs nothing measurable.

### What was measured

3200-game fixed-N match vs `v4.0.7`, TC 40/60, `2moves_v2.pgn` openings:

| Slice | W-L-D | Elo |
|---|---|---|
| Overall | 1441-752-1007 (0.608) | **+76.0 ± 10.1**, LOS 100 % |
| nmp as White | 750-357-493 (0.623) | ~+87 |
| nmp as Black | 691-395-514 (0.593) | ~+65 |

### What we learned

1. **The mechanism is time→depth, verified from the match's own stderr timing logs.** NMP does not search shallower — it makes each iteration cheaper, and the process-static iteration-timing SMA correctly reinvests the saving in depth. Mean reached search depth rose **8.7 → 9.5 ply**; the share of moves reaching depth ≥ 10 went **25 % → 41 %**. (An initial "does nmp search shallower?" worry from eyeballing the skip-depth logs was a distribution artifact — 4.0.7 has a long thin tail of trivial deep endgames that catches the eye, but median *and* mean both move deeper with NMP.)

2. **The gain is W/B-asymmetric (~+87 White vs ~+65 Black).** At 1600 games per color the per-color CI is ±~14, so the ~22 Elo gap is ≈1.5σ — suggestive, not airtight. Same pattern the TT showed: a stronger search on both sides amplifies the small first-move advantage. A data point for the [§ 12.14](roadmap-done.md#1214-color-asymmetry-investigate-the-wb-bias-seen-in-cross-version-matches--s-evidence-weakening) asymmetry investigation, which explicitly flagged re-checking after NMP lands.

**Follow-up tuning (next round).** V1 is deliberately conservative and is now in master; several standard refinements should add more on top, each measured on its own:

- **Static-eval guard before the null move** — only attempt NMP when the node's static eval ≥ β (refined by the TT bound where an entry exists — a bound corrects the raw eval only in its informative direction; see [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding) for TT bound semantics). Skips fruitless probes. Requires computing the static eval at *inner* nodes (currently only at leaves) — a small refactor that also unlocks the next two items.
- **Reverse futility pruning** (a.k.a. static null move pruning) — if the static eval is *far* above β at low remaining depth, cut off with no search at all. Reuses the same inner-node eval. **Estimated ≈ +15–30 Elo** (unmeasured; overlaps with NMP, so the marginal gain on the post-NMP baseline is toward the lower end; margin/depth-cap tuning-sensitive).
- **Adaptive R** — **tried, shelved.** Grew the reduction with remaining depth as `R = 2 + remainingDepth/4` (with a floor keeping NMP firing at remaining depth ≥ 5). Implemented and measured together with the verification search below as one bundle vs 4.1.0: **+4.8 ± 9.7 Elo, LOS 83 %** (3200 games) — a slight positive lean but *not* statistically significant, and Black-side flat-to-negative (candidate scored 0.489 as Black). Diminishing returns: base NMP (+76, already in 4.1.0) captured the value; this refinement does not meaningfully extend it. Not merged; kept on branch `nmp-verification-search` for reference.
- **TT-store the NMP cutoffs** — **tried, shelved (−11 to −20 Elo).** Writing the fail-high as a `LOWER` bound for later reuse looks like a free speedup but is not: it makes an *un-verified* NMP approximation persistent and globally reusable — including by the regular search and in positions the NMP guards (`hasNonPawnMaterial`, `lastMoveWasNull`) would have forbidden, which myChess has no verification search to catch. Full-depth store measured **−19.7 ± 16.2** (LOS 0.9 %, 1220 games) vs 4.1.0; storing at the reduced null-move depth (`remainingDepth − 1 − R`) recovered ~9 Elo but still measured **−11.3 ± 18.2** (LOS 11 %, 920 games) — best case neutral, and no depth setting turns it positive. **Revisit only after the verification search below lands**, so the then-verified cutoffs are safe to persist; the reduced-depth form is the correct one when it returns.
- **Verification search** — **tried, shelved (same bundle as Adaptive R above).** At remaining depth ≥ 7 an NMP cutoff is confirmed by a reduced real-move re-search before acceptance (once per branch), catching the zugzwang cases the material guard misses. It *works* correctly — it fixed a documented tactical blunder (see `BlunderTest.rxd5_atMove39_vsPulse2000_engineAvoidsLosingPawnGrab`) — but bundled with the aggressive `R` it added no significant Elo (+4.8 ± 9.7): the verification cost roughly cancels the aggressive-`R` gain. Revisit only if a future feature makes the extra pruning pay off.
- **Guard tuning** — measure `MIN_CHILD_DEPTH` variants and whether an explicit `isKingChecked()` guard (currently omitted; illegal null-move positions fall out via the `isIllegal` path in the reduced search) is worth its per-node cost.

## 12.13 ~~Switch alpha-beta from fail-hard to fail-soft~~ — **DONE**

*Done — implemented as preparation for the transposition table ([§ 12.1](roadmap-done.md#121-transposition-table--done-93-elo)).*

Both `PositionSearch.alphaBetaSearchI` and `QuiescenceSearch.quiescenceSearch` now return the true unclamped score on beta cutoff and on fail-low. The previous `SearchNodeResult.window(weight, α, β)` helper and the alpha/β-taking factory overloads (`create`, `draw`, `stalemate`, `checkmateSelf`) are gone; terminal-node factories return raw scores. The `ILLEGAL_WEIGHT_POS` sentinel survives trivially since nothing clamps anymore. The `if (alpha >= 0) return alpha` shortcut in `checkmateOrStalemate` is removed — checkmate/stalemate now always return the true terminal score regardless of α.

The alpha-beta search tree is identical to fail-hard (same cutoff conditions, same best-move selection). What changes is the value returned at the boundary: a fail-high node returns *how far above β* it landed, a fail-low node returns *how far below α*. That information is what [§ 12.1 TT](roadmap-done.md#121-transposition-table--done-93-elo) uses to store sharper lower/upper bounds, and what [§ 12.8 aspiration windows](roadmap.md#128-aspiration-windows--s--2040-elo) uses to set a tighter re-search range.

Regression test: [`QuiescenceSearchTest.quiescenceFailSoft_betaCutoffReturnsUnclampedWeight`](../src/test/java/org/michaelfl/mychess/QuiescenceSearchTest.java) constructs a stand-pat position, runs quiescence with both wide and tight β, and asserts the tight call returns the unclamped stand-pat (a fail-hard implementation would clamp to β).

## 12.14 Color asymmetry: investigate the W>B bias seen in cross-version matches — **S, evidence weakening**

> **Update June 2026:** the original "W>B bias is a real engine defect worth 30–50 Elo" hypothesis has lost support after three additional cutechess matches. The cross-version-artifact explanation is now the more plausible reading. See the *updated interpretation* section below.
>
> **Update July 2026 (post-NMP):** the [§ 12.2 NMP](roadmap-done.md#122-null-move-pruning--done-76-elo) match adds a fresh data point. NMP's gain over v4.0.7 was itself W/B-asymmetric — **~+87 Elo as White vs ~+65 as Black** (~22 Elo gap, ≈1.5σ at 1600 games/color). This supports the "stronger search amplifies the first-move advantage" reading over a fixable eval defect: a pure eval bias would not scale with search strength, but a first-move-advantage amplification would. Still sub-2σ, so it does not on its own promote this to a separate investigation — but two independent search features (TT, now NMP) show the same directional effect, which is itself weak evidence for the amplification explanation.

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

`threadWeight[color]` accumulated, during the per-piece eval scan, a small bonus for every potential capture target the side could threaten — roughly `weightOfPiece[capturedPiece]` per pseudo-legal capture, plus `+4` for any move that put the opposing king in check. Multiplied by `threadWeightFactor = 0.02f` in the final sum. Conceptually a coarse approximation of "side-to-move can take stuff," which a textbook quiescence search would cover more precisely. The hypothesis was: with myChess's existing [`QuiescenceSearch`](../src/main/java/org/michaelfl/mychess/QuiescenceSearch.java) in place, `threadWeight` is redundant or actively noise, and removing it should be neutral-to-positive. The hypothesis turned out to be wrong, and the postmortem below identifies why: *at the time*, myChess's QSearch resolved only the *same-square exchange chain*, not all captures, so threats on other squares still needed eval-side compensation. **That structural gap is now closed** — the all-captures QSearch shipped in v4.2.0 ([§ 12.6.1](roadmap.md#1261-enter-at-every-leaf-and-follow-all-captures--done-60-elo), +60 Elo) — so removing `threadWeight` is worth **re-testing** (see [search § 6.4](search.md#64-quiescence-search) for the current QSearch).

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
3. **`threadWeight` and `QSearch` are not fully redundant after all.** If they were, removing `threadWeight` should be exactly neutral. The slight pooled regression hints that `threadWeight` still contributes some useful signal at the leaf — and given that myChess's QSearch covers only the same-square exchange chain (see [search § 6.4](search.md#64-quiescence-search)), the explanation is concrete rather than mysterious: `threadWeight` was filling exactly the gap of "captures available on squares other than the contested one" that QSearch ignores. The proper fix — the all-captures QSearch — shipped in v4.2.0 ([§ 12.6.1](roadmap.md#1261-enter-at-every-leaf-and-follow-all-captures--done-60-elo)); **revisiting the `threadWeight` removal is now unlocked** (the multi-square-threat gap it filled is closed).

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

A small-effect SPRT bench probably belongs in [§ 12.10 (in-process measurement harness)](roadmap-backlog.md#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — fixed seeds, color-balanced pairs, fast turnaround so that the ±15 CI is the default, not the exception.

### Why this slot in the roadmap

Documents the closure so the `threadWeight` removal isn't unwittingly re-attempted. The `no-thread-weight` research branch stays in the repository for reference. The methodology lesson above is the more durable takeaway — it shapes how we should run *any* future investigation in the small-Elo band.

## 12.17 ~~Remove `chessFactor` term from the evaluation function~~ — **investigated, term confirmed productive**

*Investigated June 2026 with a single 1600-game-budget SPRT against `myChess-3.5.2`. Removing the `chessFactor` "can-give-check" bonus produced a clear, statistically significant Elo regression. The term stays in the evaluation. The `no-chess-factor` branch is kept as a research archive.*

### What the term did

`chessCount[color]` was incremented inside `capture()` whenever the per-piece move scan found that the side could "capture" the opposing king — i.e., the side could play a check on the next ply. Multiplied by `chessFactor = 0.25f` in the final eval sum, this was a flat **+0.25 pawn unit bonus per available check** at the eval leaf. The hypothesis — analogous to §12.16 — was that quiescence search already covers forcing moves and the bonus might be redundant or noise. As with §12.16, this hypothesis assumed a *textbook* quiescence search; at the time myChess's QSearch resolved only the same-square exchange chain, and it still does not extend on checks (see [search § 6.4](search.md#64-quiescence-search)) — so the assumption was structurally wrong from the start. (The v4.2.0 all-captures upgrade closed the same-square limitation but *not* the check gap, which is what `chessFactor` addresses.)

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

2. **QSearch and `chessFactor` are complementary, not redundant.** As of v4.2.0 the QSearch follows *all* captures (see [search § 6.4](search.md#64-quiescence-search)) — but it still **does not extend on checks** (no check-extension feature is implemented; see [§ 12.4](roadmap.md#124-check-extensions--s--1530-elo)). So a leaf node where the side *could* give check next ply has no way to surface that to alpha-beta unless the static eval encodes it. `chessFactor = 0.25` is a cheap proxy for the missing check-extension: it nudges the search toward lines with forcing moves available, which often correlate with king-attack themes the rest of the eval doesn't directly capture. **The v4.2.0 all-captures upgrade does *not* unlock this re-test** — `chessFactor` compensates for *checks*, not captures. Revisiting `chessFactor` removal becomes meaningful only once [§ 12.4 check extensions](roadmap.md#124-check-extensions--s--1530-elo) is in place.

3. **Cost/benefit is the inverse of §12.16.** `threadWeight` cost ~10 lines and delivered pooled-neutral Elo (so removal was defensible on simplification grounds, just not necessary). `chessFactor` costs ~5 lines and delivers ~+14 Elo (so removal would be a clear regression, simplification argument loses). The two terms look superficially similar in the code but play very different roles.

4. **Possible follow-up: ~~remove~~ *upgrade* the term.** If `chessFactor` is a poor man's check-extension, then implementing [§ 12.4 (check extensions)](roadmap.md#124-check-extensions--s--1530-elo) properly might subsume the term and possibly add another +5–15 Elo on top. The natural sequence is: keep `chessFactor` for now → implement check extensions → re-run the removal experiment with extensions in place → expect the regression to shrink or vanish (if extensions fully cover the signal). **§ 12.4 was scheduled as step 4 of the [current plan](roadmap.md#current-plan-2026-08-12) on 2026-09-03**, partly for this reason, so the sequence now has a date attached rather than being open-ended.

### Methodology — SPRT self-tunes with adequate budget

This run also confirms the §12.16 takeaway about budget sizing in practice. With a 1600-game-budget SPRT:

- Real effect ≈ −14 Elo (larger than the SPRT's `elo0 = −3` lower threshold by margin) → terminated at 1199 games, 75% of budget.
- A confirmation run would not have changed the verdict — the original run already crossed the bound.
- No "noise floor" misleading us: the LOS of 5.7% with a 16.9-Elo CI is not the "noise" range we saw in §§ 12.15–12.16.

This is the budget-policy this section's table should be read against: 1600 is the **maximum**, not the typical, and real effects come in well before that.

### Why this slot in the roadmap

Documents the closure: `chessFactor` is not a candidate for removal. The `no-chess-factor` branch stays in the repository as a research archive so the same experiment isn't accidentally re-attempted. The more interesting open question — whether implementing [§ 12.4 check extensions](roadmap.md#124-check-extensions--s--1530-elo) would let us *then* drop `chessFactor` for free — is captured as a sequencing note in point 4 above.

## 12.18 ~~Remove `EVALUATE_MATERIAL_ONLY_THRESHOLD` shortcut~~ — **investigated, mechanism strongly confirmed productive**

*Investigated June 2026 with a single 1600-game-budget SPRT against `myChess-3.5.2`. Removing the material-only leaf shortcut produced the **strongest negative result and earliest SPRT termination** of the whole eval-removal series. The shortcut stays in the search. The `no-material-only-treshold` branch is kept as a research archive.*

### What the shortcut did

[`PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD = 200`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) and the matching `materialDelta` running counter (carried through `SearchNodeContext` and the `QuiescenceSearch` recursion) implemented a leaf-eval shortcut: whenever the cumulative material delta since the search root exceeded ±200 centi-pawns, `calculatePositionWeight` returned the raw `materialWeight` and skipped the full positional evaluation (`WeightingFunction.calculate` — PSTs, mobility, threat weight, castling, doubled pawns, etc.).

Conceptually a "you're already up/down a couple of pawns, don't fuss about positional fine print" rule. The removal hypothesis: with the full eval cheap and `QuiescenceSearch` (at the time) resolving only the same-square exchange chain, the shortcut might be redundant or even harmful (positional features could pick the better move within a class of materially-equivalent leaves).

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

3. **The shortcut and QSearch are complementary.** QSearch handles the local tactical horizon; the material-only shortcut handles the global material verdict by suppressing positional noise once material is clearly tilted. They cover different parts of the eval-correctness space. **As of v4.2.0 the QSearch handles a much broader class of tactical positions** (all captures, every leaf — see [search § 6.4](search.md#64-quiescence-search)), so the shortcut may now have shifted from "load-bearing" toward "optional": **this re-test is now unlocked.**

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

## 12.19 ~~Add hanging-pieces penalty to the evaluation function~~ — **DONE (+28 Elo)**

*Implemented and merged June 2026. A 1600-game-budget SPRT against `myChess-3.5.2` terminated at the upper bound (H1 accepted) after 867 games with **+28.1 ± 20.5 Elo, LOS 99.6%**. First successful eval-term addition after the long pawn-structure investigation series (§ 12.15) and the four eval-removal closures (§§ 12.16–12.18). Released as `v3.6.0`.*

### What was added

A new penalty term in `WeightingFunction` that counts pieces that are simultaneously **attacked by an opposing piece AND not defended by any own-color piece** (kings excluded). Each hanging piece costs 0.1 pawn-units in the final-weight formula, applied as `(white_hanging - black_hanging) * undefendedPiecesFactor` with `undefendedPiecesFactor = -0.1f` — mirrors the sign convention of `doublePawnFactor`.

Implementation detail (worth highlighting because it is unusual and clean):

- A copy of the raw board (`tempBoard`) is taken at the start of every `calculate()` call.
- The existing per-piece scan already touches every attacking-relation and every defending-relation. It is extended to *tag* those relations on `tempBoard`:
  - `capture()` ORs `ATTACK_MARK_BIT = 32` onto the attacked square (bit 5, which does not collide with any piece byte `8–21` or with `Board.illegal = 64`).
  - `defend()` wipes the entire square to `Board.empty = 0`, clearing both piece bits and any attack marker.
- A linear scan after the main loop counts squares with `(piece & ATTACK_MARK_BIT) == ATTACK_MARK_BIT && (piece & SIDE_BITS) == SIDE_BITS` (kings excluded via pre-computed `WHITE_KING_ATTACKED` / `BLACK_KING_ATTACKED` constants).
- A defended piece's square ends as `0`, so it never satisfies the "marker bit AND piece bits" predicate regardless of the order in which attack-mark and defend-wipe arrive. Verified by enumerating all four orderings.

The capture-or-defend dispatch for pawn diagonals was folded into a `captureOrDefendWithPawn` helper, replacing four near-identical if-blocks in the white/black pawn paths.

### What was measured

One 1600-game-budget SPRT against `myChess-3.5.2`, TC 40/60, SPRT bounds `elo0=-3, elo1=10, α=β=0.05`:

| Run | W-L-D | Elo | LOS | SPRT |
|---|---|---|---|---|
| 1 | 373-303-191 (867 games, 3 No Result) | **+28.1 ± 20.5** | **99.6%** | **H1 accepted at ubound** (llr +2.95, terminated at 54% budget) |

W/B split:
- White: 183-149-102 → ~+27 Elo
- Black: 190-154-89 → ~+29 Elo
- W/B gap: **~3 Elo** — essentially symmetric

### What we learned

1. **The first H1-accepted result of the entire eval-investigation series.** All earlier work either terminated at H0 (clear regression — §§ 12.17–12.18) or ran to the budget limit (neutral / inconclusive — §§ 12.15–12.16, threadWeight). H1-acceptance with LOS 99.6% is decisive.

2. **W/B-symmetric within ~3 Elo despite only 433–434 games per color.** Every earlier experiment with the same opening set and ~400 games-per-color showed run-to-run W/B-gap variance of 13–38 Elo (§§ 12.15–12.16). The fact that this term works *equally well* for both colors is independent evidence that it captures a real, chess-correct signal — not an opening-book artifact, not a side-specific positional accident.

3. **"Attacked AND undefended" is a meaningfully different signal from "undefended alone."** The first iteration of this term counted *every* own piece that lacked a defender, regardless of whether it was under attack — that was a weaker signal and chess-theoretically diffuse. Restricting to *hanging* pieces (the chess-theoretic concept of a piece that is one move away from being lost for free) was the change that made the term productive.

4. **Magnitude is well-tuned at -0.1.** A typical mid-game position has 1–3 hanging pieces per side, so the term contributes 0–0.3 pawn-units to the eval — large enough to matter, small enough to not dominate. (The first iteration had factor -0.5, which would have been too large; reduced after code review.)

5. **Marker-bit-in-tempBoard is a clean data-encoding pattern.** No additional parallel array needed; one bit in the existing piece byte carries the attack-mark information. The collision-free bit (32) lives in unused space between piece-byte range (8–21) and `illegal` (64). Worth remembering for future eval terms that need per-square tags.

### Why this slot in the roadmap

Documents the first successful eval-term addition since the pre-investigation baseline. Establishes a template that future eval-additions can follow:

- **Start from a chess-theoretic concept that the existing eval misses** (here: tactical fragility within one ply). Reject pure-positional concepts that the engine already approximates (cf. §12.15).
- **Restrict the trigger condition tightly enough to make the signal meaningful** (here: attacked AND undefended, not just undefended).
- **Tune the magnitude conservatively** (-0.1 turned out to be Goldilocks; -0.5 would have failed).
- **Verify on a 1600-game SPRT** with W/B-symmetry as a side-check for opening-book-artifact contamination.

The hanging-pieces term is now part of `master` at `v3.6.0`. The `undefended-pieces-weight` branch may be deleted once any pending follow-up work (e.g. cross-confirming against a stronger opponent than 3.5.2) is done — but the implementation itself is in master and there is no reason to keep the branch indefinitely.

### 12.19.1 Re-validated — removal after the all-captures QSearch, term confirmed still productive (≈ −13 Elo)

*Re-tested July 2026. § 12.18 flagged that the v4.2.0 all-captures QSearch (every leaf, all captures — see [search § 6.4](search.md#64-quiescence-search)) might have rendered some static eval terms redundant. The hanging-pieces penalty was the obvious candidate: if the QSearch resolves every capture at every leaf, does a static "attacked and undefended" penalty still add anything? It does. Removing it cost ≈ −13 Elo. The term stays; `remove-undefended-criteria` (`v4.2.3-remove-undefended-criteria`) is kept as a research archive, not merged.*

#### What was measured

The term and its supporting machinery (the `tempBoard` attack-marker scan, `defend()`, `calculateUndefendedPiecesCount`, the `getHangingPiecesCount` accessor, and the factor's entry in the tunable-factor arrays) were removed and matched against `myChess-4.2.1`, TC 40/1200:

| Run | W-L-D | Elo | LOS |
|---|---|---|---|
| 1 | 359-401-480 (1240 games) | **−11.8 ± 15.1** | 6.4% |

The running estimate held between −11 and −19 Elo across the last ~40 samples without trending back toward zero (LOS ≈ 4–9%, i.e. ~90–95% probability of a regression). The gauntlet was aborted once the direction was stable: the CI still nominally straddled zero, but for the *decision* — "is the term removable?" — the verdict was already clear. Removal would need a neutral-or-positive result; this is firmly negative. Running to significance would only have tightened toward ≈ −13 ± 10, not changed the sign.

#### What we learned — the all-captures QSearch does NOT subsume the term

The intuition "the QSearch grabs every hanging piece, so a static hanging-pieces term is redundant" is wrong, and the reason is structural in how the QSearch treats a leaf:

1. **The QSearch never passes the move.** At each quiescence node it computes a stand-pat (`standPat = calculatePositionWeight(...)`, the full static eval), sets `bestWeight = standPat` as a *floor*, and then searches **only the side-to-move's captures**, which can only *raise* that floor. It never hands the opponent a free move.

2. **Consequence — an asymmetry:**
   - A hanging piece belonging to the side **not** to move *is* resolved: the side to move simply captures it in the QSearch. Here the static term genuinely is redundant.
   - A hanging piece belonging to the side **to** move is **invisible** to the QSearch. Losing it would require the *opponent* to move, but the opponent only gets a turn *after* the side to move makes a capture. With no useful capture available, the QSearch returns the (too-optimistic) stand-pat and silently assumes the loose piece holds.

3. **The static term corrects exactly that optimism.** "Attacked AND undefended" lowers the stand-pat — symmetrically for both colors — encoding the "the opponent grabs it next tempo" loss that a pass-less, captures-only QSearch cannot represent. That is real information the QSearch structurally lacks.

4. **Secondary — search efficiency.** The term is part of the eval used for move ordering and alpha-beta pruning at every interior node, so removing it also degrades nodes-to-depth, compounding the eval loss under a time budget.

This mirrors the § 12.18 finding for the material-only shortcut: the QSearch and the static term are **complementary, not redundant** — the QSearch resolves the concrete tactical horizon, the term keeps the leaf evaluation honest about loose pieces the horizon can't reach.

#### Why this slot in the roadmap

Closes the re-test that § 12.18 unlocked for this term: the hanging-pieces penalty (§ 12.19, +28 Elo at v3.6.0) is **still** productive after the all-captures QSearch, contributing ≈ 13 Elo. It is not a removal candidate. This reinforces the general lesson of the eval-removal series (§§ 12.16–12.18): every term that survived a removal test is load-bearing, and a stronger QSearch does not change that.

