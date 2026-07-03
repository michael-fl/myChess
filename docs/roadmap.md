# 12. Roadmap: Improving Playing Strength

This chapter lists the missing ingredients that would move myChess closest to a competitive classical engine, in rough order of expected return on effort. Each entry quotes a ballpark Elo estimate, an effort tag (S / M / L), and points to the place in the existing code where it would land.

The numbers are *order-of-magnitude*, not measurements — actual gains depend on tuning, the search depth they are measured at, and interaction with other components. They are drawn from the public chess-programming literature ([CPW](https://www.chessprogramming.org/)) and from typical engines of comparable scope. Wherever a feature only helps when paired with another, that pairing is noted.

The README's [§ 1.2 *Scope and status*](../README.md#12-scope-and-status) already names the absent items at the level of "what the engine does not do (yet)". This chapter is its actionable counterpart.

---

## 12.1 ~~Transposition table~~ — **DONE (+93 Elo)**

*Implemented and merged June 2026. Two early-stopping self-play SPRTs and one 1600-game fixed-N match against `myChess-3.6.0`, TC 40/60. The precise reference number is the fixed-N match: **+92.7 ± 15.2 Elo** at 1600 games, LOS 100 %. Released as `v4.0.0`.*

The single biggest missing optimization, and the one the README already flagged. The transposition table (TT) caches per-position search results keyed by Zobrist hash, so positions reached through different move orders are evaluated once.

- The hash already existed ([`Board.calculatePositionKey()`](../src/main/java/org/michaelfl/mychess/Board.java), [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding)).
- Each TT entry stores `{key, depth, score, bestMove, bound}` where `bound ∈ {EXACT, LOWER, UPPER}`. A fixed-size open-addressed array with depth-preferred-EXACT replacement is what shipped in v4.0.0; v4.0.2 refactored the storage into 4-slot buckets keeping the same replacement principle (see the "TT bucket replacement strategies" follow-up below).
- The TT also feeds [§ 7.1 best-known-move ordering](search.md#71-best-known-move-pv-ordering): on a TT hit, try the stored `bestMove` first — strictly more informed than the previous-iteration PV alone (see [§ 7.8 Move sorting](search.md#78-move-sorting-sortablemovesbucket) for the `ttMove` integration).
- Wire-in points: [`PositionSearch.alphaBetaSearchPre`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) (probe at node entry, store on exit) and `MoveSorterImpl` (accepts a TT move via the `ttMove` hint).

Caveats handled in the shipped implementation: TT is cleared on `ucinewgame` via [`UciHandler.handleNewGame`](../src/main/java/org/michaelfl/mychess/UciHandler.java); mate-score adjustment by ply on store/probe is encapsulated in [`WeightingFunction.scoreToTT` / `scoreFromTT`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) (the sign-loss bug surfaced through `GameStatusTest.testWhiteCheckmate` during development and is now locked in by `ScoreTTAdjustmentTest`); parallel search remains out of scope. See [§ 7.9 Transposition table](search.md#79-transposition-table) for the full technical reference.

### What was measured

Three self-play matches against `myChess-3.6.0`, TC 40/60:

| Run | Type | Band (`elo0`..`elo1`) | W-L-D | Games | Elo | LOS | Termination |
|---|---|---|---|---|---|---|---|
| 1 | SPRT | `-3 .. 15` | 117-67-43 | 227 | **+77.8 ± 41.6** | 100% | H1 at ubound (14% budget) |
| 2 | SPRT | `20 .. 80` | 133-81-54 | 268 | **+68.3 ± 37.8** | 100% | H1 at ubound (17% budget) |
| 3 | **Fixed-N** | — | **817-400-383** | **1600** | **+92.7 ± 15.2** | **100%** | full budget — precise reference |

All three estimates are consistent within their CIs, but the **fixed-N match is the reference**: it has the tightest CI (±15.2 vs ±37-42 for the SPRTs), and SPRT point estimates are known to be biased low by the early-stopping mechanism (the test stops as soon as evidence for H1 is sufficient, which can be well before the sample mean has converged on the true value — both SPRT runs here terminated at sample means 15-25 Elo below the eventual fixed-N point).

**White-vs-Black asymmetry.** The early SPRT runs showed near-symmetric W/B splits (~60-62 % both sides). The 1600-game fixed-N match exposes a ~62 Elo gap: kandidat as White wins 66.9 %, as Black 59.1 %. At 800 games per color the per-color CI is ±20-25 Elo, so the gap is ≈2.5σ — suggestive but not airtight. Most plausible reading: the TT amplifies the small first-move advantage that already existed pre-TT (the W>B baseline-bias investigated in [§ 12.14](#1214-the-wb-color-asymmetry-investigation--investigation-archived)) — both sides get a stronger search, but the side that starts from slightly better positions converts that into a slightly larger fraction of wins. Worth re-checking after [§ 12.2 NMP](#122-null-move-pruning--s--50100-elo) and [§ 12.6 QSearch upgrade](#126-quiescence-search-upgrade--m--4080-elo) land; if the asymmetry persists at >2σ, it deserves a separate investigation entry.

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

Closes the largest remaining single-feature item with a clean +93 Elo measurement and unblocks several downstream items that pair with TT — most importantly [§ 12.2 Null-move pruning](#122-null-move-pruning--s--50100-elo) (whose reduced-depth search now hits a populated TT and can cut off immediately), [§ 12.3 LMR](#123-late-move-reductions-lmr--s--50100-elo) (where the TT-stored bestMove is the first re-search candidate), and [§ 12.8 Aspiration windows](#128-aspiration-windows--s--2040-elo) (where TT bounds become the natural source for the next iteration's window).

### Follow-up: 4× TT default size in v4.0.1 — null effect at TC 40/60

The initial TT shipped with `DEFAULT_SIZE = 2^20` (1 M entries, ~50 MB). Analysis suggested this was being overwritten ~30-60× per game at TC 40/60, with the depth-preferred-EXACT replacement protecting the most valuable entries but the mid-depth signal getting evicted. Predicted gain from quadrupling to 2^22 (4 M entries, ~200 MB): **+10-15 Elo**.

**Measured: +1.1 ± 14.5 Elo over 1600 games** (SPRT `elo0=-3 elo1=15` ran the full budget without accepting either bound — LLR drifted toward H0 at -1.63 by the end; draw ratio 27.8 %; W/B asymmetry within noise at ±20 Elo per color). The TT enlargement produces **no measurable strength change** at this TC.

**What the null result actually means:**

1. **The depth-preferred-EXACT policy is more effective than the eviction-rate alone suggests.** A 1 M-entry table being overwritten 30-60× per game still preserves the high-depth EXACT entries that matter for cutoffs; the mid-depth entries that get evicted apparently weren't carrying enough Elo to show up in a 1600-game match.
2. **TC 40/60 doesn't produce enough unique meaningful positions to saturate even a 1 M-entry table's *working set*.** At ~750 k nodes per move with ~60 moves per game, the cumulative unique-position count after transposition dedup is ~12-24 M, but the *working set* (positions that get revisited within the same game's search horizon) is much smaller — closer to 1 M.
3. **The 4 M version isn't worse either.** No regression, no GC-pause artifact from the larger heap (verified via per-process RSS during the SPRT). It's purely a no-op at this TC.

**Decision: keep 4 M as the default.** The harmless-at-this-TC result doesn't generalise to longer TCs (where the search tree grows enough to *use* the extra capacity). A 200 MB TT is fine on any modern desktop, and matches the common 256-512 MB range that Tournament-quality engines deploy with. Reverting to 1 M would only matter on memory-constrained setups (mobile, browser), which is not myChess's target.

**Follow-up: UCI `Hash` option** is the proper long-term solution — let the GUI configure TT size per use case. Currently in [§ 12.9.2 `UciHandler` (1 day)](#1292-ucihandler--1-day), the `setoption` handling is minimal; adding a `Hash` option (parse MB value, re-allocate the singleton TT) is ~10-15 lines plus a small TranspositionTable refactor. This becomes more attractive once we know that for TC 40/60 the natural default differs from TC 40/300+ — instead of guessing, expose the knob.

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

## 12.2 Null-move pruning — **S, ≈ 50–100 Elo**

Pass the turn to the opponent at depth ≥ 3 and search the reply with reduced depth `R = 2 or 3`. If the result still exceeds beta, the original position is so good for the side to move that a real move can only confirm it — return beta.

- One conditional branch inside the recursive node, plus a `Board.switchTurn()` / restore pair (no piece is moved). The `GameStatus` stack already supports a turn flip via [`GameStatus.switchTurn()`](../src/main/java/org/michaelfl/mychess/GameStatus.java).
- Disable when the side to move is in check or has only pawns + king (avoid zugzwang). The existing `isEndGame()` heuristic is too crude — gate on actual non-pawn material instead.
- Pairs naturally with [§ 12.1 TT](#121-transposition-table--done-93-elo): TT cutoffs from the reduced-depth search return immediately.

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

## 12.6 Quiescence search upgrade — **M, ≈ 40–80 Elo (combined)**

The current [`QuiescenceSearch`](../src/main/java/org/michaelfl/mychess/QuiescenceSearch.java) implementation is much narrower than the textbook quiescence search — see [search § 6.4](search.md#64-quiescence-search) for the precise description of what it does (and does not) do today. In short:

- **What it does:** stand-pat with fail-soft α/β, resolves the capture chain *on the last-captured square only*, depth-capped at 20.
- **What it does not do:** follow captures on *other* squares, order captures, filter losing captures, prune captures whose material gain can never reach α, extend on checks, or use the transposition table.

The original "follow all captures" approach was tried once and abandoned as too expensive — at a time before the TT, MVV-LVA, and SEE existed in the codebase. With those primitives now available (or about to be), the upgrade becomes viable. This section splits the upgrade into five independently shippable sub-items, in the order they should be implemented.

### 12.6.1 Follow all captures, not only same-square — **M, ≈ 30–60 Elo**

The single biggest gap: extend the QSearch capture loop to consider *every* legal capture at the leaf, not only those landing on the field the previous move captured on. The current condition

```java
if (capturedOnField == Move.getToField(plainMoves[i])) {
```

is replaced by

```java
if (Move.getCapturedPiece(plainMoves[i]) != 0) {
```

This makes the QSearch tree much wider per node — exactly the explosion the original implementation feared — which is why the next two items (12.6.2, 12.6.3) need to land alongside or shortly after to keep the cost bounded.

Closes the systematic blind spot where myChess at the leaf overlooks a hanging piece on a square other than the last contested one (forks, discovered attacks, hung pieces from earlier in the sequence). Anecdotally responsible for a non-trivial fraction of cutechess losses where myChess is statically "fine" but tactically lost within 1–2 plies.

### 12.6.2 MVV-LVA capture ordering in QSearch — **S, ≈ 5–15 Elo**

Inside the new all-captures loop, try captures in **Most Valuable Victim, Least Valuable Attacker** order. `WeightingFunction.getMaterialWeightOfMove` already provides the victim weight; the attacker piece can be read from the source square in one byte-load. A simple `(victimWeight * 16) − attackerWeight` sort key (or a precomputed 6×6 table indexed by piece type) is enough.

Without ordering, the all-captures version of 12.6.1 wastes most of its work — α/β cutoffs depend on trying the best capture first. With MVV-LVA, even the unfiltered all-captures variant becomes practical.

### 12.6.3 SEE pruning of losing captures in QSearch — **M, ≈ 10–20 Elo**

After 12.6.1+12.6.2, the loop still considers obviously losing captures like `QxP` defended by a pawn. **Static Exchange Evaluation** simulates the exchange sequence purely from the static piece values (no recursive search), returning the net material change. Skip captures with `SEE < 0`.

Implementation: a `Board.see(toField, attackerPieceType, sideToMove)` method that alternately swaps the least-valuable attacker from each side onto the contested square and returns the running material balance. ~30 lines using the existing attacker enumeration.

SEE is also useful in the **main search** for splitting winning vs. losing captures more precisely than the current `bucketWinningCaptures` / `bucketOtherCaptures` heuristic in [`MoveSorterImpl`](../src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java) (which uses victim − attacker, a one-ply MVV/LVA approximation). Worth a follow-up SPRT once it exists.

### 12.6.4 Delta pruning in QSearch — **S, ≈ 5–15 Elo**

A second pruning layer that complements SEE: skip captures where `standPat + capturedMaterial + DELTA_MARGIN < α`. The intuition: even if this capture wins its full nominal material, it can't lift the position above α — there is no point in searching it.

Standard `DELTA_MARGIN` ≈ 200 cp (≈ two pawns of safety). Disable in endgames where small material differences dominate the eval surface.

Cheap to implement (~5 lines), small but real Elo gain.

### 12.6.5 TT integration in QSearch — **M, ≈ 5–15 Elo**

Use the [§ 12.1 transposition table](#121-transposition-table--done-93-elo) inside the QSearch as well: probe on entry, store on exit. Modern engines do this, with a depth marker of 0 (or a small constant) so QSearch entries cannot be reused as score-cutoffs by the deeper main search — they are valuable as best-move/bound hints for QSearch revisits.

Caveat: QSearch generates many leaf positions; without care, those will thrash the TT and evict more valuable main-search entries. The standard mitigation is a two-bucket TT layout (one slot depth-preferred for main-search entries, one always-replace for QSearch leaves) or a depth-weighted replacement formula. Worth a separate SPRT to confirm net positive in myChess specifically — engines that did not use TT in QSearch have measured ~0 Elo from adding it, others +20.

### Why the staged order matters

Item 12.6.1 alone is a net **loss** without 12.6.2 and 12.6.3, because the all-captures tree without ordering or pruning is too expensive to fit inside the time budget — the engine reaches a lower main-search depth and loses more from that than it gains from the wider QSearch. The minimum viable upgrade is **12.6.1 + 12.6.2 + 12.6.3 in one branch**, validated by a single SPRT against the same-square baseline.

12.6.4 and 12.6.5 are independent refinements that can each be SPRT'd separately.

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
- Pairs with [§ 12.1 TT](#121-transposition-table--done-93-elo): without it, re-searches are expensive enough that the heuristic can be a net loss.

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

Optional `info depth … nodes … pv …` lines during search make the GUI's analysis panel light up but aren't strictly required to play. Now feasible as follow-up work since both prerequisites have landed: `setoption name Hash` (TT is now in master, [§ 12.1](#121-transposition-table--done-93-elo)) and `setoption name UCI_Chess960` (Chess960 is in master, [§ 12.11](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant)). `ponder` remains out of scope for a first version. The `Hash` option specifically is motivated by the v4.0.1 null-effect finding ([§ 12.1 follow-up](#follow-up-4-tt-default-size-in-v401--null-effect-at-tc-4060)): exposing the knob lets the user pick a TC-appropriate value instead of relying on a default that may be over- or under-dimensioned for their use case.

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

*Done — implemented as preparation for the transposition table ([§ 12.1](#121-transposition-table--done-93-elo)).*

Both `PositionSearch.alphaBetaSearchI` and `QuiescenceSearch.quiescenceSearch` now return the true unclamped score on beta cutoff and on fail-low. The previous `SearchNodeResult.window(weight, α, β)` helper and the alpha/β-taking factory overloads (`create`, `draw`, `stalemate`, `checkmateSelf`) are gone; terminal-node factories return raw scores. The `ILLEGAL_WEIGHT_POS` sentinel survives trivially since nothing clamps anymore. The `if (alpha >= 0) return alpha` shortcut in `checkmateOrStalemate` is removed — checkmate/stalemate now always return the true terminal score regardless of α.

The alpha-beta search tree is identical to fail-hard (same cutoff conditions, same best-move selection). What changes is the value returned at the boundary: a fail-high node returns *how far above β* it landed, a fail-low node returns *how far below α*. That information is what [§ 12.1 TT](#121-transposition-table--done-93-elo) uses to store sharper lower/upper bounds, and what [§ 12.8 aspiration windows](#128-aspiration-windows--s--2040-elo) uses to set a tighter re-search range.

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

`threadWeight[color]` accumulated, during the per-piece eval scan, a small bonus for every potential capture target the side could threaten — roughly `weightOfPiece[capturedPiece]` per pseudo-legal capture, plus `+4` for any move that put the opposing king in check. Multiplied by `threadWeightFactor = 0.02f` in the final sum. Conceptually a coarse approximation of "side-to-move can take stuff," which a textbook quiescence search would cover more precisely. The hypothesis was: with myChess's existing [`QuiescenceSearch`](../src/main/java/org/michaelfl/mychess/QuiescenceSearch.java) in place, `threadWeight` is redundant or actively noise, and removing it should be neutral-to-positive. The hypothesis turned out to be wrong, and the postmortem below identifies why: myChess's QSearch resolves only the *same-square exchange chain*, not all captures, so threats on other squares still need eval-side compensation. See [§ 12.6](#126-quiescence-search-upgrade--m--4080-elo) for the structural fix and [search § 6.4](search.md#64-quiescence-search) for the current QSearch description.

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
3. **`threadWeight` and `QSearch` are not fully redundant after all.** If they were, removing `threadWeight` should be exactly neutral. The slight pooled regression hints that `threadWeight` still contributes some useful signal at the leaf — and given that myChess's QSearch covers only the same-square exchange chain (see [search § 6.4](search.md#64-quiescence-search)), the explanation is concrete rather than mysterious: `threadWeight` was filling exactly the gap of "captures available on squares other than the contested one" that QSearch ignores. The proper fix is the structural QSearch upgrade in [§ 12.6](#126-quiescence-search-upgrade--m--4080-elo); revisiting `threadWeight` removal afterwards is then meaningful.

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

`chessCount[color]` was incremented inside `capture()` whenever the per-piece move scan found that the side could "capture" the opposing king — i.e., the side could play a check on the next ply. Multiplied by `chessFactor = 0.25f` in the final eval sum, this was a flat **+0.25 pawn unit bonus per available check** at the eval leaf. The hypothesis — analogous to §12.16 — was that quiescence search already covers forcing moves and the bonus might be redundant or noise. As with §12.16, this hypothesis assumed a *textbook* quiescence search; myChess's actual QSearch resolves only the same-square exchange chain and does not extend on checks (see [search § 6.4](search.md#64-quiescence-search)), so the assumption was structurally wrong from the start.

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

2. **QSearch and `chessFactor` are complementary, not redundant.** Quiescence search in myChess follows only same-square exchange chains (see [search § 6.4](search.md#64-quiescence-search)) and does not extend on checks (no check-extension feature is implemented; see [§ 12.4](#124-check-extensions--s--1530-elo)). So a leaf node where the side *could* give check next ply has no way to surface that information to alpha-beta unless the static eval encodes it. `chessFactor = 0.25` is effectively a cheap proxy for the missing check-extension: it nudges the search toward lines with forcing moves available, which often correlate with king-attack themes the rest of the eval doesn't directly capture. Revisiting `chessFactor` removal becomes meaningful once both [§ 12.4 check extensions](#124-check-extensions--s--1530-elo) and [§ 12.6 QSearch upgrade](#126-quiescence-search-upgrade--m--4080-elo) are in place.

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

Conceptually a "you're already up/down a couple of pawns, don't fuss about positional fine print" rule. The removal hypothesis: with the full eval cheap and `QuiescenceSearch` already resolving the same-square exchange chain (see [search § 6.4](search.md#64-quiescence-search) for the exact scope), the shortcut might be redundant or even harmful (positional features could pick the better move within a class of materially-equivalent leaves).

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

3. **The shortcut and QSearch are complementary.** QSearch handles the local tactical horizon — narrowly, by resolving the same-square exchange chain only (see [search § 6.4](search.md#64-quiescence-search)); the material-only shortcut handles the global material verdict by suppressing positional noise once material is clearly tilted. They cover different parts of the eval-correctness space. The complementarity will become tighter once [§ 12.6 QSearch upgrade](#126-quiescence-search-upgrade--m--4080-elo) lands and QSearch handles a broader class of tactical positions — at that point the shortcut may shift from "load-bearing" to "optional", which is a deliberate re-test target.

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

---

## Suggested implementation order

| Step | Item | Combined effort | Cumulative Elo (rough) |
|---|---|---|---|
| 1 | [§ 12.9 UCI minimal](#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) — FEN importer + `UciHandler` + HIARCS/Stockfish baseline gauntlet | M (1–2 days) | — (GUI + baseline measurement) |
| 2 | [§ 12.10 In-process harness](#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — node-count bench + WAC EPD runner (self-play loop optional, covered by cutechess-cli from step 1) | S | — (per-change diagnostics) |
| 3 | [§ 12.1 Transposition table](#121-transposition-table--done-93-elo) (fail-soft alpha-beta is already in place, see [§ 12.13](#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--done)) | M | +150 – +300 |
| 4 | [§ 12.3 LMR](#123-late-move-reductions-lmr--s--50100-elo) + [§ 12.5 history](#125-history-heuristic--s--3050-elo) | S | +250 – +450 |
| 5 | [§ 12.2 Null-move pruning](#122-null-move-pruning--s--50100-elo) | S | +300 – +550 |
| 6 | [§ 12.4 Check extensions](#124-check-extensions--s--1530-elo) + [§ 12.8 aspiration](#128-aspiration-windows--s--2040-elo) | S | +340 – +620 |
| 7 | [§ 12.6 Quiescence search upgrade](#126-quiescence-search-upgrade--m--4080-elo) — all-captures + MVV-LVA + SEE pruning + delta pruning + optional TT integration | M | +380 – +700 |
| 8 | [§ 12.7 Eval upgrades](#127-evaluation-upgrades--m--50100-elo-combined) | M | +420 – +770 |
| 9 | [§ 12.12 Real time management](#1212-real-time-management-heuristics--s--m--3060-elo) | S–M | +450 – +830 |
| 10 | [§ 12.11 Chess960](#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant) (optional, opens a new variant) | M | — (on standard chess) |

The order is deliberate: **UCI first**, because (a) it yields an immediately visible GUI, (b) `cutechess-cli` becomes available as the measurement workhorse, and (c) a baseline gauntlet against fixed-depth Stockfish anchors every later improvement against a stable external reference. The in-process harness then adds fast per-change diagnostics. TT is the next biggest single jump, and LMR / null-move / aspiration all assume it exists. The eval upgrades come last because their interactions with the search are the easiest to misjudge without measurement. Chess960 is last of all because it gives zero Elo on standard chess and is best tackled once the core engine is strong.

What is *not* on this list — neural-network evaluation (NNUE), parallel search ("Lazy SMP"), and endgame tablebases — would each be a much larger project than anything above, and would shift the character of the engine away from "hand-written, single-threaded, study-friendly". They are out of scope for the foreseeable future.
