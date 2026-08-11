# 12. Roadmap: Improving Playing Strength

This chapter lists the missing ingredients that would move myChess closest to a competitive classical engine, in rough order of expected return on effort. Each entry quotes a ballpark Elo estimate, an effort tag (S / M / L), and points to the place in the existing code where it would land.

The numbers are *order-of-magnitude*, not measurements — actual gains depend on tuning, the search depth they are measured at, and interaction with other components. They are drawn from the public chess-programming literature ([CPW](https://www.chessprogramming.org/)) and from typical engines of comparable scope. Wherever a feature only helps when paired with another, that pairing is noted.

The README's [§ 1.2 *Scope and status*](../README.md#12-scope-and-status) already names the absent items at the level of "what the engine does not do (yet)". This chapter is its actionable counterpart.

---

## Roadmap index

This roadmap is split across three files. Section numbers (§ 12.x) are **stable IDs** and are referenced throughout the docs regardless of which file a section now lives in.

**Active plan — in this file.** Forward, Elo-driving work (see [Suggested implementation order](#suggested-implementation-order) below for priority):

| § | Item | Effort / Elo |
|---|---|---|
| 12.3 | Late move reductions (LMR) | S, ≈ 50–100 |
| 12.4 | Check extensions | S, ≈ 15–30 |
| 12.5 | History heuristic | S, ≈ 30–50 |
| 12.6 | Quiescence search upgrade (remaining parts) | S, ≈ 5–15 (delta pruning) |
| 12.7 | Evaluation upgrades / tapered PST — *in flight* | M, ≈ 40–80 |
| 12.8 | Aspiration windows | S, ≈ 20–40 |
| 12.12 | Real time management heuristics | S–M, ≈ 30–60 |
| 12.20 | Principal Variation Search (PVS) | S, ≈ 10–25 |
| 12.21 | King safety | M, ≈ 30–60 |
| 12.23 | Repetition draws invisible to the search — *correctness* | S, ≈ 0 in self-play, real half-points vs others |

**[Completed & investigated → `roadmap-done.md`](roadmap-done.md).** Shipped features and closed investigations (kept as knowledge):

| § | Item | Status |
|---|---|---|
| 12.1 | Transposition table | DONE, +93 Elo |
| 12.2 | Null-move pruning | DONE, +76 Elo |
| 12.13 | Fail-soft alpha-beta | DONE |
| 12.14 | Color asymmetry (W>B bias) | investigation, evidence weakening |
| 12.15 | Pawn-structure connection term | investigated, neutral |
| 12.16 | Remove `threadWeight` | investigated, neutral |
| 12.17 | Remove `chessFactor` | investigated, confirmed productive |
| 12.18 | Remove material-only shortcut | investigated, confirmed productive |
| 12.19 | Hanging-pieces penalty | DONE, +28 Elo |

**[Backlog & infrastructure → `roadmap-backlog.md`](roadmap-backlog.md).** Optional / enabling, no direct standard-chess Elo:

| § | Item |
|---|---|
| 12.9 | UCI protocol |
| 12.10 | In-process measurement harness |
| 12.11 | Chess960 support (incl. § 12.11.1 evaluation tuning for 960) |

---

## 12.3 Late move reductions (LMR) — **S, ≈ 50–100 Elo**

After the first few moves at a node (those that have already passed [§ 7.1 PV / 7.2 killer ordering](search.md#71-best-known-move-pv-ordering)), reduce the search depth by 1–2 for quiet moves. If the reduced search beats alpha, re-search at full depth.

- Adds two integer comparisons in the move loop in `calculateNextMoveSub`. Disable on captures, promotions, and check-givers.
- Synergises strongly with TT and a [§ 12.5 history heuristic](roadmap.md#125-history-heuristic--s--3050-elo): both make the *first few* moves much more likely to be best, which is exactly the precondition for LMR's gamble to pay off.
- Best introduced together with [§ 12.20 PVS](roadmap.md#1220-principal-variation-search-pvs--negascout--s--1025-elo): LMR-reduced moves already run on a null window, so the two share the re-search path.

## 12.4 Check extensions — **S, ≈ 15–30 Elo**

When the side to move is in check, increment search depth by 1 instead of decrementing. Cheap, reliable, hard to get wrong.

- Already detectable: [`Board.isKingChecked(moveGenerator)`](../src/main/java/org/michaelfl/mychess/Board.java) returns a boolean.
- Watch the *extension budget* — uncontrolled extensions can blow up depth on long forced lines. A common cap is "total extensions per path ≤ ply at root".

## 12.5 History heuristic — **S, ≈ 30–50 Elo**

A `int[2][64][64]` table indexed by `(color, fromField, toField)` is incremented (typically by `depth²`) whenever a quiet move causes a beta cutoff. The move sorter uses these counts as the weight key for quiet moves in [`bucketRemainingMoves`](../src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java).

- Complements [§ 7.2 killer moves](search.md#72-killer-moves), which only remember two moves per depth — history is dense across all `from→to` pairs.
- Decay (e.g. shift right by 1 every iteration) keeps the table responsive across iterative-deepening iterations and across games.

## 12.6 Quiescence search upgrade

**Status:** §12.6.1 DONE (+60 Elo, v4.2.0); §12.6.2 + §12.6.3 DONE (+40.6 Elo, SEE, v4.2.1); §12.6.4 pending; §12.6.5 tried & shelved.

**§12.6.1 (enter at every leaf + follow all captures) shipped in v4.2.0 — measured +60.4 ± 9.7 Elo** (details below). The remaining sub-items refine that all-captures search. See [search § 6.4](search.md#64-quiescence-search) for the current behavior. In short:

- **What the QSearch does now (v4.2.1):** entered at *every* leaf; stand-pat with fail-soft α/β; follows all legal captures **ordered by SEE, with losing captures (SEE < 0) pruned** (§12.6.2+§12.6.3); a cooperative timeout keeps it inside the move budget; depth-capped at 20.
- **What it still does not do:** delta-prune, extend on checks, or use the transposition table — the remaining §12.6.4–12.6.5 items below.

The original "follow all captures" approach was tried once, long ago, and abandoned as too expensive — before the TT and capture ordering existed. With capture ordering now in `MoveSorterImpl`, the wider search pays off (see 12.6.1). This section splits the upgrade into five independently shippable sub-items.

### 12.6.1 Enter at every leaf and follow all captures — **DONE (+60 Elo)**

Shipped in v4.2.0. Three changes, which had to land together:

- **(a) Entry at every leaf.** The old leaf wrapper entered the real quiescence only after a *capturing* leaf move; a quiet move that left a piece en prise was scored statically. The wrapper now enters quiescence **unconditionally** — the stand-pat cutoff returns immediately when the position really is quiet, so the extra cost is paid only where captures exist.
- **(b) All captures.** The capture-loop condition changed from `capturedOnField == Move.getToField(move)` (same-square only) to `Move.getCapturedPiece(move) != 0` (every legal capture). Both (a) and (b) hinged on the old "last move was a capture" assumption (the search derived `capturedOnField` from it), so neither could move without the other.
- **(c) Timeout guard.** The now much wider search polls a cooperative timeout (`QuiescenceSearch.isTimeout()`); on timeout it returns a dummy score that the leaf converts to `SearchNodeResult.TIMEOUT`. Without it the all-captures tree could overrun the move budget.

**Measured / learned.** **+60.4 ± 9.7 Elo** vs v4.1.1 (3200-game fixed-N, LOS 100 %), **color-robust** (~+70 White / ~+51 Black) — the largest single-feature jump since NMP. Closes the two blind spots the old same-square search had: hanging pieces on any square (forks, discovered attacks) and captures available after a *quiet* leaf move.

**Note — it shipped *alone*.** Contrary to the "staged order" caveat below, 12.6.1 was measured without 12.6.2/12.6.3 and was already strongly positive. The reason: myChess's `MoveSorterImpl` already orders captures (winning captures first), so the wider capture tree got good α/β cutoffs for free — the "all-captures without ordering is a net loss" worry assumed *no* ordering, which is not myChess's situation.

### 12.6.2 Capture ordering in QSearch — **DONE (v4.2.1, shipped as SEE ordering)**

Shipped in v4.2.1 together with §12.6.3 and measured as one bundle (**+40.6 Elo** — see there). Instead of the plain MVV-LVA key originally planned here, the quiescence-configured `MoveSorterImpl` scores each capture by its full **static exchange value** (`StaticExchangeEvaluation.see(move)`) rather than the main search's victim − attacker approximation, then buckets winning (SEE > 0) captures ahead of the rest. SEE ordering is strictly more precise than MVV-LVA — it accounts for the whole exchange sequence, not just the first victim/attacker pair — so the planned MVV-LVA step was subsumed by it.

Without ordering, the all-captures version of 12.6.1 wastes most of its work — α/β cutoffs depend on trying the best capture first; SEE ordering supplies exactly that.

### 12.6.3 SEE pruning of losing captures in QSearch — **DONE (+40.6 Elo)**

The all-captures loop otherwise considers obviously losing captures like `QxP` defended by a pawn. **Static Exchange Evaluation** simulates the exchange sequence purely from the static piece values (no recursive search), returning the net material change; captures with `SEE < 0` are dropped before the search ever makes them.

**Shipped in v4.2.1** as a dedicated `StaticExchangeEvaluation` class (least-valuable-attacker swap-off with X-ray battery reveals in both ray directions and both colors), wired into a quiescence-configured `MoveSorterImpl`: the sorter scores each capture by `see(move)` and uses that both to order captures (§12.6.2) and to skip the ones with `SEE < 0` (the `isQuiescenceSearch && deltaWeight < 0` guard in `addMove`). Covered by `StaticExchangeEvaluationTest` (per-method: the `see()` entry point, exchange folding, revealed attackers per compass direction, container reuse) and `MoveSorterImplTest`. The class turned out larger than the originally sketched ~30-line `Board.see(...)` — the X-ray reveal handling and reusable-container design account for the difference.

**Measured / learned.** **+40.6 ± 9.4 Elo** vs v4.2.0 (3200-game fixed-N, LOS 100 %, draw ratio 39.3 %), **color-robust** — winning with both colors (0.580 as White, 0.537 as Black). The measurement bundles SEE ordering (§12.6.2) and SEE < 0 pruning together — they shipped in one commit and were not A/B-split, so the division between "better ordering" and "fewer wasted nodes" is not separately quantified. Well above the pre-estimate (≈ 10–20 Elo for pruning alone), because it also replaced the capture *ordering* with the more precise SEE key.

SEE is also useful in the **main search** for splitting winning vs. losing captures more precisely than the current `bucketWinningCaptures` / `bucketOtherCaptures` heuristic in [`MoveSorterImpl`](../src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java) (which still uses victim − attacker, a one-ply MVV/LVA approximation, for the main search). Now that `StaticExchangeEvaluation` exists, extending it to the main-search sorter is a concrete follow-up — worth a separate SPRT.

### 12.6.4 Delta pruning in QSearch — **S, ≈ 5–15 Elo**

A second pruning layer that complements SEE: skip captures where `standPat + capturedMaterial + DELTA_MARGIN < α`. The intuition: even if this capture wins its full nominal material, it can't lift the position above α — there is no point in searching it.

Standard `DELTA_MARGIN` ≈ 200 cp (≈ two pawns of safety). Disable in endgames where small material differences dominate the eval surface.

Cheap to implement (~5 lines), small but real Elo gain.

### 12.6.5 TT integration in QSearch — *tried, shelved (−11 Elo, LOS 4 %)*

**Tried** on branch `qsearch-tt` (commit `1a39da5`): a **separate** full-size transposition table for the QSearch (`getDefaultQSearchInstance()`), probed on entry and stored on exit with EXACT/LOWER/UPPER bounds plus a TT move for ordering, keyed by remaining QSearch depth and cleared on `ucinewgame`.

**Measured 4.2.1-qsearch-tt vs 4.2.0: −10.6 ± 11.5 Elo, LOS 3.6 % over ~2040 games** — a real, small regression. The apparent color split (candidate 0.503 as White, 0.467 as Black) is **not** a bug: once the intrinsic first-move advantage (White vs Black 0.518 in the match) is factored out, the deficit is symmetric (−0.015 in both colors), i.e. a uniform ~−11 Elo.

The cause is structural, not a code-cleanup artifact: a *second* full table doubles memory/cache pressure on top of the main TT, and QSearch nodes are individually so cheap that probe+store cost plus TT thrashing outweighs the few saved nodes. This matches the literature — QSearch-TT is a notoriously marginal feature. Strong engines that use it (e.g. Stockfish) **share the single main TT** with a QS depth sentinel (`DEPTH_QS`, a 0/small-constant marker so QSearch entries are always preferred-below main-search entries) rather than a separate table, and draw most of the benefit from the TT move / early cutoffs, not from raw score caching.

If revisited, the only variant worth an SPRT is the shared-TT design: reuse the [§ 12.1 table](roadmap-done.md#121-transposition-table--done-93-elo) with the depth sentinel above — **not** a second table. Otherwise deprioritized behind the remaining QSearch refinement (§12.6.4 delta pruning); §12.6.3 SEE pruning has since shipped (v4.2.1).

### Why the staged order matters — *superseded by the 12.6.1 result*

The original worry here was: **12.6.1 alone is a net loss** without 12.6.2/12.6.3, because an all-captures tree *with no ordering* is too expensive to fit the time budget (lower main-search depth costs more than the wider QSearch gains). **This turned out not to apply to myChess** — 12.6.1 shipped alone and measured +60.4 Elo, because `MoveSorterImpl` already orders captures (winning first), so the wider tree got good cutoffs without a dedicated ordering pass. The premise ("no ordering") was simply false for this engine.

The sub-items were *independent* refinements on top of the shipped all-captures search, each SPRT'd separately. 12.6.2 (SEE ordering) + 12.6.3 (SEE pruning) shipped together in v4.2.1 (**+40.6 Elo**) — the highest-value step, shrinking the now-wider capture tree rather than just reordering it; 12.6.5 (TT-in-QSearch) was tried and shelved (see above). Only 12.6.4 (delta pruning) remains open.

## 12.7 Evaluation upgrades — **M, ≈ 40–80 Elo combined**

[§ 5 *Evaluation Function*](evaluation.md#5-evaluation-function) ends with a list of features deliberately omitted. Adding the cheapest ones individually buys little; bundling them is worthwhile. In rough cost order:

- **Bishop pair** (+30 cp when a side has both bishops). One bit-test added to the material scan.
- **Passed pawns** — bonus scaled by rank. Detection is one row-and-adjacent-file scan per pawn; do it inside the existing `calculateForWhitePawn` / `calculateForBlackPawn` loops to amortise.
- **King safety** — pulled out into its own [§ 12.21](roadmap.md#1221-king-safety--m--3060-elo): it is the single largest eval term missing today and a full complex (attacker weighting, pawn shield, open lines) rather than a one-liner, so it is tracked separately from this bundle.
- **Proper endgame detection** — replace [`GameStatus.isEndGame() { return plyCount > 60; }`](../src/main/java/org/michaelfl/mychess/GameStatus.java) with a material-based criterion (e.g. `total non-pawn material < threshold`). This alone fixes the endgame king-PST cutoff in [§ 5.2](evaluation.md#52-piece-square-tables) and makes [§ 12.2 null-move pruning](roadmap-done.md#122-null-move-pruning--done-76-elo) safer.
- **Tapered evaluation with PeSTO PSTs** — replace the hand-tuned [Simplified PSTs](https://www.chessprogramming.org/Simplified_Evaluation_Function) currently in [`PieceSquareTables`](../src/main/java/org/michaelfl/mychess/PieceSquareTables.java) with the auto-tuned [PeSTO](https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function) tables (separate midgame and endgame tables per piece type), interpolated by remaining non-pawn material. Requires the proper endgame detection above. Two design choices specific to myChess:
  - **Column-symmetrize the tables before use.** PeSTO is trained on standard-chess games where kingside castling dominates, so its tables encode column asymmetries (a-file ≠ h-file) that are statistical artifacts of the training corpus, not chess principles — the knight table has a-rank/h-rank values differing by ~80 cp on the back rank. For Chess960 ([§ 12.11](roadmap-backlog.md#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant)) this asymmetry is actively harmful (no side dominates after castling); for standard chess it likely costs only 5–10 Elo. Mirror-average every column pair (a↔h, b↔g, c↔f, d↔e) at table-load time — one shared symmetric table for both variants is much simpler than maintaining two separate sets, and the Elo trade-off is well worth the reduced complexity.
  - **The existing `invert()` for the black tables stays unchanged.** It only flips ranks (1↔8 etc.) and doesn't touch column ordering, which is exactly what antisymmetry of the eval (`MirrorEvalTest`) requires.
- **Mobility weight retuning** — the six per-piece-type weights in `WeightingFunction.mobilityWeightOfPiece` are hand-tuned heuristics that have never been ELO-validated (see [§ 5.3 tuning observations](evaluation.md#tuning-observations) for the analysis). Pawn = 20 is high (conflates "not blocked" with "well-placed"); rook = 10 is flat across positions where an open-file rook should outscore a back-rank shuffle. A short SPSA-style sweep, or even a handful of candidate-tuple gauntlets, would likely yield 10–30 Elo without any new feature work. **Prioritized alongside king safety (§12.21) as the next evaluation theme, ahead of further search work.**

### 12.7.1 Tapered evaluation — staged rollout strategy

**Status (2026-08-07) — pawn EG (+22.3, v4.3.0), king EG (+7.7, v4.3.1) and the queen material fix 900→1000 (+12.6, v4.3.2) shipped; the joint endgame-PST tune was shelved (neutral — § 12.7.3).** The null test (step 0) came back Elo-neutral (bench-signature-identical after the `byte→short` + int-packed-PST refactor), confirming the plumbing. The first real divergence — a phase-aware, **endgame-only pawn-PST** Texel tune (midgame table held fixed) on the Zurichess set — measured **+22.3 ± 19.5 Elo vs 4.2.3 (LOS 98.7 %, SPRT H1 accepted, 766 games)**, color-robust. Both prerequisites below were indeed the blocker: with the storage widened *and* the phase split live, Texel finally tracks Elo rather than inverting. Tooling for a future Zurichess + self-play hybrid exists (`PgnQuietEpdExtractor`, branch `tuning-dataset-extractor`). The **king endgame table** followed in v4.3.1: a Texel-tuned EG table rewarding centralization (center up, back-rank-center ≈ −135), which also finally retired the crude `plyCount > 60` endgame switch for the king (see the phase-computation note below). Its SPRT vs 4.3.0 gave a small, stable **+7.7 ± 16.9 (LOS 81.5 %, stopped early)** — a real but sub-midpoint gain a −3/15 SPRT cannot resolve quickly, shipped on the sub-95 %-LOS convention. The planned **joint endgame tune** of the remaining pieces then measured **neutral** and was shelved — but its dominant *material* signal led to the **queen material value 900 → 1000** fix (v4.3.2, **+12.6, LOS 96.8 %**); see § 12.7.3. Finally, the **MG+EG full-joint tune shipped as v4.3.4 (+23.0 ± 12.9 Elo, fixed-N vs 4.3.3, LOS 100 %, color-robust)**: all twelve tables retuned together on a Zurichess + myChess Chess960 self-play hybrid (~1.49 M positions, ~4 % self-play, two-thirds of it 960), giving the four mobile pieces their first genuine midgame/endgame split — material fixed, each table re-centered to strip the material leak. The early SPRT read +69 but was winner's-curse-inflated; the unbiased fixed-N is +23. The final piece — the **PeSTO ceiling check** below — then came back **≈ 0 Elo** (−3.1 ± 12.2 over 2000 games): the tapered tables are already at reference level, which closes the tapered-evaluation theme.

**Motivation (empirical).** Offline Texel tuning of the *single-phase* evaluation consistently **lost Elo** even while improving the tuning proxy. `eval-tuning-v1` (Texel-tuned pawn PST + factors, −2.5 % train MSE) measured **−15.6 ± 11.4 Elo** over 2100 games vs 4.2.1 (LOS 0.4 %); the naive all-PST tune was diagnosed as material-leaked with a phase-confounded, endgame-shaped king table and never matched. Root cause: the Zurichess `quiet-labeled` set is phase-mixed, and a single table per piece cannot be right for both the middlegame (king hides, advanced pawns modest) and the endgame (king centralizes, advanced pawns near-decisive) — the tuner fits the average and hurts both. Tapered evaluation removes this confound and is therefore a **prerequisite** before any further eval tuning is worth attempting. The tuning tooling already exists on master ([`TexelTuner`](../src/test/java/org/michaelfl/mychess/tuning/TexelTuner.java) plus the `*TexelData` adapters).

**A second, mechanical cause — byte-storage overflow (found later).** The post-mortem above credited only the phase confound, but a later [`PieceSquareTables`](../src/main/java/org/michaelfl/mychess/PieceSquareTables.java) audit found a compounding bug that likely dominated the *pawn* tune. The tables are stored as `byte[]` — `createBoard` does `(byte) Integer.parseInt(...)`, which **silently wraps** — while the tuner works in unbounded `double`. Scanning the tuned branches: the untouched master tables stay within ±50, but **`eval-tuning-v1` has 10 pawn-PST values outside `[−128, 127]`, up to +300** (the advanced passed-pawn squares). The `(byte)` cast mangled them — `+300 → +44`, `+227 → −29`, `+137 → −119` — several with a **flipped sign**, so the built engine *avoided* exactly the squares the tuner rewarded. The MSE (computed in `double` with +300) improved while the engine ran on −29: the textbook proxy≠Elo signature, now mechanically explained. (`full-pst-tuning-v1` had 2 such wraps, `−147 → +109`; untuned master has none.)

**Consequence — widening the storage is a second prerequisite.** Widening the PST storage from `byte[]` to `short[]` / `int[]` (or the int-packed 16-bit representation from the packed-PST optimization) is a **prerequisite for any PST tuning**, past or future — not merely a perf nicety. Both fixes are needed *together*: the phase split (this section) *and* the wider storage. It is plausible the byte overflow, not the phase confound alone, was the dominant cause of the `eval-tuning-v1` regression — worth re-testing even a single-phase tune once the storage is widened.

**Phase computation — fixed weights, computed in the eval scan.** Derive the phase from constant per-piece weights (PeSTO: N = 1, B = 1, R = 2, Q = 4, so the start position = 24), summed over both colors on the fly inside [`WeightingFunction`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java)'s existing piece loop — no new `GameStatus` field is needed. Do **not** derive the phase from [`GameStatus.nonPawnMaterialWeight`](../src/main/java/org/michaelfl/mychess/GameStatus.java): that quantity is built from the *tunable* `WeightingFunction.weightOfPiece`, so a phase based on it would make the eval a product of two tunable parameters (`MG[s] · phase(material)`), which is non-linear and breaks the `eval = baseEval + features · params` decomposition the whole Texel approach relies on. Fixed phase weights keep it linear. This also finally replaces the crude `GameStatus.isEndGame() { return plyCount > 60; }` binary switch with a smooth blend (the "proper endgame detection" item above).

**Staged rollout — one measurable change per step:**

0. **Null test first.** Build the taper machinery with **MG = EG = the current tables**. Blending two identical tables reproduces today's eval exactly, so this step must be **Elo-neutral by construction** — any regression means a bug in the phase count, the blend, or the feature rebuild. Validate it *before* touching any value.
1. **Seed the tune from our own tables, not PeSTO.** Duplicate the current tables into MG/EG and tune from there. PeSTO is trained for a *PST-only* evaluation and bakes in king safety, pawn structure, etc. that myChess already scores with separate terms (mobility, king-safety, doubled-pawn, undefended-pieces) — dropping it in risks double-counting. Starting from our tables keeps compatibility with those terms, gives cleaner attribution (the measured effect is purely "phase awareness + our tuning" over a known baseline), and avoids integration mismatches (`positionFactor` scaling, PeSTO's own material values). The tuner discovers the MG/EG split itself: endgame positions drive the EG tables because only they carry feature weight at low phase.
2. **Texel-tune the MG/EG tables** (and material as MG/EG pairs). With the phase confound gone, the MSE gain should finally track Elo instead of inverting. Measure against the step-0 null-test baseline via SPRT.

**PeSTO stays a reference, not the seed.** Measure PeSTO-as-is once (its MG/EG tables + material, column-symmetrized as described in the bullet above) against the current single-phase eval — a ceiling check that says whether proven tapered tables beat our eval *independently of our tuner*, which has twice cost Elo. Then compare our tuned tapered result against that reference: if ours ≥ PeSTO, the tuner and our eval design carry; if PeSTO is clearly better, adopt it (or seed a fresh tune from it). This refines the "Tapered evaluation with PeSTO PSTs" bullet above — prefer tuning from our tables with PeSTO as a yardstick over swapping PeSTO in wholesale.

**Result — ceiling reached (2026-08-09).** A pure-PeSTO evaluation (PeSTO tapered material + PeSTO MG/EG tables symmetrized ×2, no other terms) versus the shipped v4.3.4 measured **−3.1 ± 12.2 Elo over 2000 fixed-N games** (pesto POV; LOS 30.7 %, draw ratio 36 %, color-balanced: pesto scored 0.506 as White, 0.484 as Black). The 95 % interval spans zero, so the two are statistically indistinguishable. Reading: our Texel-tuned tapered tables plus the full evaluation are at PeSTO's level, so the **PST / material lever is exhausted** — further linear table or material tuning has low expected value. This bounds only *static tables + material*; it says nothing about the dynamic terms PeSTO also lacks (king safety [§ 12.21](roadmap.md#1221-king-safety--m--3060-elo), mobility weights), which can still add Elo the tables cannot. The **highest-leverage remaining work is therefore in the search, not the evaluation** (LMR / PVS / history / aspiration). A controlled follow-up — PeSTO tables plus *our own* eval terms versus v4.3.4, with only the tables differing, so it isolates "PeSTO-tables minus ours" with everything else held equal — is prepared on branch `hybrid-pesto-tables` but not yet run.

### 12.7.2 Tried — rook-file / battery bonus, shelved (neutral)

**Tried — rook-file and battery bonus, shelved (neutral).** Branch `rook-battery` (commit `a12ba0f`). A rook on an open file scored +20 cp, a half-open file (an opponent pawn but no own pawn) +10 cp, 0 on a file carrying an own pawn; two of a color's rooks connected on the same file (a battery) added a further +30 cp, folded into the evaluation through a new tunable `rookFileFactor`. **Result: −2.0 ± 10.8 Elo vs 4.2.1 (2420 games, LOS 35.8% — neutral).** An early 1100-game snapshot read +13.9 ± 15.9 but regressed cleanly to zero — a textbook reminder not to trust sub-2000-game samples.

**Why neutral — the term duplicates signal the evaluation already has:**

- The open-file bonus overlaps with the existing **file-weighted rook mobility**: [`calculateForRook`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java) already counts a rook's vertical (file) moves at full weight and its rank moves at half, so a rook on an open file is rewarded through mobility before the new term ever fires.
- A rook on the 7th rank is already worth ~+5 cp through the **rook piece-square table** (its rank-7 values of +10 scaled by `positionFactor = 0.5`).
- Only the **battery** bonus is genuinely new signal — but it is too rare and too search-resolvable (the concrete "pigs on the 7th" wins the tactical search already finds) to move the needle.

**Narrower variants considered and not pursued:** open/half-open file bearing on the enemy king (king-safety-redundant, and hand-crafted king safety already measured net-negative — see [§ 12.21](roadmap.md#1221-king-safety--m--3060-elo)); a single rook on the 7th (PST-redundant); a battery with a concrete target (search-redundant).

**Take-away.** For an engine with a strong tactical search, static evaluation terms that duplicate what **mobility, the piece-square tables, or the search itself** already capture tend to measure neutral. If a rook-battery / rook-on-7th bonus is ever revisited, it belongs inside a tapered evaluation ([§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy)) as an **endgame-scaled** term (rooks on the 7th matter most in the endgame), not as a flat standalone. The code is preserved on branch `rook-battery` rather than merged.

### 12.7.3 Tried — joint endgame-PST tune, shelved (neutral) → the queen material fix (v4.3.2)

**Tried — joint knight/bishop/rook/queen endgame-PST tune, shelved (neutral).** Branch `4.3.2-joint-eg-tables`. After the pawn (v4.3.0) and king (v4.3.1) endgame tables, the four remaining pieces' endgame PSTs were Texel-tuned **together** in one 128-parameter run (`JointEndgamePstTaperedTexelData` / `TexelJointEgTaperedTuner`, midgame tables fixed, Zurichess quiet-labeled). The raw tune had a large **material leak** — a uniform per-piece offset (queen ~+49 cp, knight ~−36 cp in the pure endgame) that, with material fixed, the tuner used to express a phase-dependent *material* re-rating a placement table cannot hold. Removing it (anchoring each EG table to its midgame mean) left only positional *shape*. **Result: neutral (~+3 Elo, LOS ~64 %, LLR drifting to H0 at 960 games).** Lesson: the mobile pieces' endgame *placement* is largely **search/mobility-redundant** — the § 12.7.2 pattern again, in contrast to pawn/king, which are static-positional and did gain.

**The signal that mattered — an undervalued queen (→ v4.3.2, +12.6 Elo).** The leak was not noise. Modelled cleanly as **tapered endgame material** (branch `4.3.2-tapered-eg-material`: separate endgame material values per piece, blended by phase, `MaterialEgTaperedTexelData`), the tune wanted the queen **~+72 cp higher in the endgame** (knight −37, rook −17, bishop +4) and measured a small positive on its own (~+11.5). But the queen direction disagreed with PeSTO (which puts the queen *lower* in the endgame) — a red flag. The resolution: the queen was simply **undervalued in the midgame too**. myChess used the classical 900 (queen/rook = 1.8), well below "2R = Q" (2.0) and PeSTO's ~2.15; the endgame-only tuner had pushed the queen up in the only place it could reach. **Raising the midgame queen value 900 → 1000 directly** (queen/rook → 2.0) is the fundamental, larger fix and shipped as **v4.3.2 (+12.6 ± 13.3, LOS 96.8 %)** — the strongest-confirmed gain of the tapered/material series, also resolving the Chess960 passive-rook weakness (`EvalRegressionTest`).

**Shelved.** The joint endgame-PST and tapered-endgame-material branches are kept but not merged — both re-fix the same undervalued queen and are redundant with v4.3.2. If tapered endgame material is revisited, re-tune it **on top of** the corrected queen=1000 baseline; the queen's endgame divergence should then shrink toward PeSTO's direction.

### 12.7.4 Bishop-pair bonus — DONE (+31 Elo, v4.3.3)

**Bishop-pair bonus, shipped in v4.3.3 — +31.3 ± 24.1 Elo (LOS 99.4 %, SPRT H1 accepted at 512 games).** A side holding both bishops scores a fixed +0.4 pawns (~40 cp). myChess had no material-combination terms (knight = bishop = 300 flat), and the bishop pair is the most reliable such bonus in computer chess — two bishops cover both color complexes and dominate open positions. Counted per color in `WeightingFunction.calculateForBishop` (the per-piece handler) and awarded once in `calculatePositionWeight`; wired in as the 8th tunable Texel factor so the 0.4 value can be tuned later (the eval value is unchanged by that wiring). The largest single eval gain of the tapered/material series and its first formal H1 acceptance — a reminder that a missing *well-established* term can outweigh a lot of fine placement tuning (contrast the neutral joint endgame-PST, § 12.7.3). Note: there is deliberately **no** knight-pair bonus — two knights do not complement each other (and KNNvK is a draw).

## 12.8 Aspiration windows — **S, ≈ 20–40 Elo**

At each iterative-deepening iteration, search with a narrow window `[score − 50, score + 50]` around the previous iteration's score. Re-search with the wider window only on a fail-high or fail-low.

- One change in [`PositionSearch.calculateNextMove`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java)'s deepening loop.
- Pairs with [§ 12.1 TT](roadmap-done.md#121-transposition-table--done-93-elo): without it, re-searches are expensive enough that the heuristic can be a net loss.

## 12.12 Real time management heuristics — **S → M, ≈ 30–60 Elo**

myChess currently reads `wtime`/`btime`/`movestogo` from the GUI and converts them into a flat per-move budget (see [§ 12.9.2](roadmap-backlog.md#1292-ucihandler--1-day) / [`UciHandler.computeClockBudgetMillis`](../src/main/java/org/michaelfl/mychess/UciHandler.java)). The engine itself only ever sees `millisPerMove` — it has no notion of a remaining clock that persists across `go` calls.

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

## 12.20 Principal Variation Search (PVS / NegaScout) — **S, ≈ 10–25 Elo**

Search only the *first* move at each node — the PV move, ordered first by [§ 7.1 PV / TT ordering](search.md#71-best-known-move-pv-ordering) — with the full `(alpha, beta)` window. Search every *other* move with a null window `(alpha, alpha+1)`: a cheap proof that it is worse than the PV move. Only when a null-window search unexpectedly fails high (the move is actually better than alpha) re-search that one move with the full window.

- Prerequisites already in master: the TT ([§ 12.1](roadmap-done.md#121-transposition-table--done-93-elo)) and PV/TT-move-first ordering, which make the first move reliably the best often enough that the null-window tests on the remaining moves pay off.
- **Natural partner to [§ 12.3 LMR](roadmap.md#123-late-move-reductions-lmr--s--50100-elo).** LMR-reduced moves are searched with a null window anyway, so PVS and LMR are usually introduced together and share the re-search machinery. The null-window primitive already exists in the codebase — the [§ 12.2 NMP](roadmap-done.md#122-null-move-pruning--done-76-elo) reduced search uses `-β, -β+1`.
- The one subtlety is search instability: a node can be visited with different windows across re-searches, and TT bounds from one window feed the next. Standard and manageable, but worth watching in the node-count diagnostics ([§ 12.10](roadmap-backlog.md#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics)).

*Aside — can a search run on null windows exclusively?* Yes: that is **MTD(f)** (Memory-enhanced Test Driver; `f` = first-guess). It replaces the single full-window root search with a *series* of null-window tests that bracket the true minimax value, driven by a first guess (typically the previous iteration's score). It requires a TT — the repeated root searches share work only through it — and was competitive with NegaScout in the mid-90s (Plaat, Schaeffer, Pijls, de Bruin), but it is sensitive to eval granularity and prone to search-instability oscillation. PVS — full window on the PV line, null window elsewhere — is the robust middle ground modern engines settled on, which is why this item targets PVS rather than MTD(f).

## 12.21 King safety — **M, ≈ 30–60 Elo**

The single largest evaluation term myChess is still missing. Today the only king-safety signal in the eval is the king PST (which just encodes "stay back / castle in the midgame", see [§ 5.2](evaluation.md#52-piece-square-tables)); there is no notion of *how exposed* the king actually is. [§ 5's omissions list](evaluation.md#5-evaluation-function) explicitly flags "king safety beyond castling" as not implemented. For an engine with zero king-danger evaluation this is very likely the most valuable single eval addition — comfortably ahead of bishop-pair or passed-pawn terms, which is why it is tracked here rather than buried in the [§ 12.7](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined) bundle (mirroring how [§ 12.19 hanging pieces](roadmap-done.md#1219-add-hanging-pieces-penalty-to-the-evaluation-function--done-28-elo) earned its own section as a standalone eval term).

**Priority — next evaluation theme (2026-08).** With the tapered piece-square-table / material series wrapping up (v4.3.0–v4.3.4), the offline Texel tuning infrastructure now exists (`TexelTuner` + the `*TexelData` adapters, the self-play → EPD pipeline, and the hybrid dataset) — precisely what the failed hand-crafted attempts below lacked, since all three were *untuned*. King safety (this section) together with the **mobility-weight retune** (§12.7) is therefore slated as the next evaluation work, **ahead of the remaining search items** (the largest being LMR, §12.3). Both are tuner-driven and build directly on the infrastructure from the tapered-PST work.

**What to build.** A king-danger score that grows with the pressure on the king's neighbourhood:

- **Attacker count on the king ring** — count enemy pieces attacking the 3×3 (or wider) square ring around the own king, weighted by attacker type (queen ≫ rook > bishop/knight). The per-piece pseudo-move scan in `WeightingFunction` already enumerates every attack relation (the same scan that feeds [§ 12.19 hanging pieces](roadmap-done.md#1219-add-hanging-pieces-penalty-to-the-evaluation-function--done-28-elo)); add a per-square attacker-count side table alongside the existing attack-mark bit.
- **Non-linear in the attacker count** — the classic king-safety insight: two attackers are far more than twice as dangerous as one. A small lookup table indexed by weighted attacker count (the "attack weight → danger" curve) captures this; linear scaling badly underrates real attacks.
- **Pawn shield** — bonus for own pawns on the two/three files in front of the king on its home rank; penalty for missing or advanced shield pawns.
- **Open / half-open files toward the king** — penalty when an enemy rook or queen bears on a file with no own pawn in front of the king.

**Why it's a section, not a bullet.** Unlike the other §12.7 terms (each a few lines), king safety is a small subsystem with several interacting parts and a non-linear response curve that *must* be tuned — untuned or linearly scaled, it can easily be net-negative (over- or under-valuing attacks). It pairs naturally with an automated eval-tuning setup (Texel/SPSA): the attacker-type weights and the danger curve are exactly the kind of parameters that hand-tuning gets wrong and a tuner gets right. Measure with a king-safety-heavy slice of the [STS suite](https://www.chessprogramming.org/Strategic_Test_Suite) once implemented; the STS "king safety" category gives a direct read on whether the term is pulling its weight.

**Tried — attacker-count term alone, shelved (−14.7 ± 11.5 Elo).** A first cut of the attacker-count idea was built on branch `4.3.0-attack-units`: pieces bearing on the enemy king's 3×3 zone accumulate weighted **attack units** (`ATTACK_UNIT_OF_PIECE`, each attacker counted once via an origin-square dedup) that index a progressive **`KING_ATTACK_PENALTY`** curve, gated on ≥ 2 distinct attackers and scaled by `kingAttackFactor = 0.01`. Measured **without** the pawn shield, as a standalone eval term. **Result: −14.7 ± 11.5 Elo vs v4.2.1** (fixed-N, aborted at 2220 games, LOS 0.6 % — conclusively negative); the deficit is uniform across colors (≈ −0.021 per side once the first-move advantage is factored out), so not an asymmetry artifact. This is exactly the "untuned / linearly-scaled king safety can be net-negative" failure flagged above: the hand-picked unit weights and danger curve over-value speculative attacks (the engine over-commits pieces toward the enemy king and adds static noise to an already strong tactical search). Revisit only with a tuned danger curve + attacker weights (Texel/SPSA), not by hand. The **pawn-shield** component was evaluated separately as its own additive term (below) rather than coupled into this (proven-negative) attacker score.

**Tried — pawn shield alone, shelved (−57.5 ± 17.5 Elo).** The pawn-shield component was then built as its own eval term on branch `4.3.0-pawn-shield`: a per-file penalty for the three shield pawns in front of a king on its castling square, scored by how far each has advanced from its home rank (`0 / −5 / −15 / −30` for home / +1 / +2 / +3-or-missing), summed over the three files, gated to a king on its own rank 1–2 (`isKingNearOwnBackRank`) and scaled by `pawnShieldFactor = 0.01` (max −0.9 pawns for a fully exposed king). Measured standalone, with the attacker-count term above disabled. **Result: −57.5 ± 17.5 Elo vs v4.2.1** (fixed-N, aborted at 920 games, LOS 0.0 % — conclusively and heavily negative); the deficit is uniform across colors (≈ −0.082 per side once the first-move advantage is factored out) and match health was clean (0 time-losses, 0 crashes), so it is a genuine regression, not an artifact. That is roughly **four times worse** than the attacker-count term above. Suspected cause: at up to −0.9 pawns the penalty is far too strong and pushes the engine into passive play — shunning sound shield-pawn advances (kingside pawn storms, and the fianchetto g3/g6, which already costs −5 as "advanced"). Revisit only with a much smaller / capped factor (or via an eval tuner), and probably penalizing only the *missing* shield pawn rather than every advance. (The suspected passivity was investigated on the king-dependent-PST successor below and did **not** materialize.)

**Tried — king-dependent pawn PSTs, shelved (−18.3 ± 11.6 Elo).** Next, the pawn shield was encoded directly into the piece-square tables (branch `king-safety-pst`, commit `e84d0de`): the pawn PST is selected by the own king's zone — queenside / center / kingside / endgame (`FIELD_2_KING_POS`, four tables per color) — instead of a single fixed table, so `getPieceSquareWeight` takes the king field. The king PST itself is dropped once the king may become active (`GameStatus.kingMayBecomeActive`: opponent non-pawn material ≤ 700). **Result: −18.3 ± 11.6 Elo vs v4.2.1** (fixed-N, 2050 games, LOS 0.1 % — conclusively negative), uniform across colors, match health clean; same ballpark as the attacker-count term.

**Passivity check — negative.** Because the standalone shield was suspected of causing passivity, this successor was checked directly: 4.2.1 vs the king-PST build at equal search on thematic positions (Najdorf O-O-O kingside storm, KID own-flank f5-f4 storm, a quiet Ruy). Both play the *same* move in 3 of 4 — including the thematic pawn advances (`Bxf6`, `f5-f4`); the king-PST does **not** suppress aggressive pawn play. So the regression is not passivity — it is diffuse: the four hand-crafted king-zone buckets shift pawn evals by a few cp across many positions, netting slightly worse than the single baseline pawn table (itself validated at +5.6 Elo). Likely architectural culprit: making the *same* pawn structure's value depend on king **position** introduces eval discontinuities (a king move re-buckets and re-values every pawn without any pawn moving) — a search-consistency risk a plain static PST does not have.

**Net take-away from all three attempts.** Hand-crafted king-safety terms — attacker count (−14.7), standalone shield (−57.5), king-dependent PST (−18.3) — all measured net-negative. This confirms the "must be tuned" caveat above: the next serious attempt should run through an automated tuner (Texel/SPSA) rather than hand-picked tables/weights, and should keep the eval a pure function of the position (avoid king-position-dependent piece values).

**Forensic conditions for a tuned retry (branch `4.3.0-attack-units`, `e93fea4`, reviewed 2026-07).** Reading the shelved code alongside the match (`test-results/match-4.3.0-attack-units-stdout.log`: 662–753–819, [0.480] over 2235 games) surfaces *why* a plain Texel pass over the existing term would not be enough — three findings, each pointing at a fix beyond "just tune it":

- **No phase scaling.** The term fires with full magnitude in the endgame, where two pieces bearing on the enemy king's 3×3 zone are usually incidental (e.g. R + K vs K), not a real attack — pure noise. King safety must fade toward the endgame, which is cleanest built on top of the tapered evaluation ([§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy)).
- **Fires on presence, not danger.** Any ≥ 2 distinct attackers on the 3×3 zone trigger the (attacker-side) bonus, so the engine over-commits pieces toward the enemy king on speculative attacks that the already-strong tactical search then refutes — the "static noise to a strong search" failure mode, concretely.
- **What Texel can and cannot tune here.** The progressive `KING_ATTACK_PENALTY` curve is linear per bucket (each position lands in exactly one bucket → its bucket value is Texel-tunable), *but* the Zurichess `quiet-labeled` set under-samples the sharp, high-attack-unit positions the curve exists for, so tuning it on quiet data mostly *shrinks* the curve toward neutral rather than learning real attacking value. The attacker unit-weights (`ATTACK_UNIT_OF_PIECE`) set the table index and are therefore *non-linear* — not Texel-tunable; keep them fixed or SPSA them.

A serious retry therefore needs three things together, not a lone tuner run: (1) **phase-scale** the term (do it with tapered eval); (2) **tune the curve on a dataset that includes real attacks**, not only quiet positions; (3) keep the **weights fixed / SPSA**, with **modest Elo expectations** — for an engine whose search already resolves king attacks tactically, the ceiling of a static king-safety term is likely well below this section's headline estimate. Sequence it after [§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy).

---

## 12.23 Repetition draws are invisible to the search — **S, correctness fix, ≈ 0 in self-play but real half-points against others**

A correctness bug, not an evaluation gap: the search cannot see a threefold repetition coming, so myChess walks into draws from positions it itself considers winning. Full mechanism and the two lichess games that exposed it are in [known-issues.md](known-issues.md); the short version is that two independent facts combine.

`Board.isThreefoldRepetition()` fires on the **third** occurrence, as the game rule requires. Inside a search that is one ply too late: at the second occurrence the check correctly declines, and the position after it already carries a transposition-table entry stored *before* it was a repetition. The entry answers with its old, pre-repetition score and the continuation is cut off, so the third occurrence is never reached — at any depth. A position hash is path-independent; a repetition draw is not.

**Opportunity, measured on 2026-08-11.** The 2000-game hybrid-vs-v4.3.4 fixed-N match (`test-results/match-hybrid-pesto-tables-vs-4.3.4.pgn`) gives the first quantification, because cutechess records each engine's own evaluation per move:

| | count |
|---|---|
| draws, total | 687 of 2000 (34.4 %) |
| … by threefold repetition | 586 — **85 % of all draws**, 29 % of all games |
| … adjudicated as equal | 59 |
| natural draws (non-adjudicated) | 628 |
| **of those, one side reported ≥ +2.00 within the last 12 plies** | **203 (32.3 %)** |
| … at 2–3 pawns / 3–5 pawns / 5–8 pawns | 105 / 87 / 11 |

So one game in ten ended as a draw while an engine believed it was at least two pawns ahead, and 98 of those at three pawns or more.

**Read that with one caveat.** myChess's own evaluation is exactly what the [§ 12.21](roadmap.md#1221-king-safety--m--3060-elo) blunder series shows to be unreliable — in lichess game [1PSnMOBF](https://lichess.org/1PSnMOBF) it reported `+1.43` in a position Stockfish scored as mate against it. Some of these `+2.00` readings are therefore misevaluation rather than a discarded win. The 98 games at three pawns and above are much harder to explain that way.

**Where the cost actually falls.** Both engines in that match carry the bug, so it does not bias the measured Elo difference — it inflates the draw rate symmetrically. The real price is paid **outside** self-play, against opponents who do not make the same mistake: on lichess, game [i1QxWK9L](https://lichess.org/i1QxWK9L) was drawn by repetition with myChess roughly eight pawns up, and the anchor bracket ([§ 12.10.3](roadmap-backlog.md#12103-self-play-tournament--m-1-day)) is where a fix should become visible.

**How to fix it.** Two routes, both making detection path-local instead of table-dependent:

- Treat the **second** occurrence along the current search path as a draw. Standard practice in engines, and it removes the dependence on the table entirely — the repetition is then a property of the path being searched, which is what it actually is.
- Or suppress table cutoffs whenever any position on the current path has occurred before. Narrower, but keeps the table honest in the rare case where the distinction matters.

**How to measure it.** A **fixed-vs-broken SPRT** — unlike the match above, the asymmetry is then the measurand, so self-play measures this fix perfectly well. Expect the gain to show up as draws converting to wins rather than as a change in playing strength per move.

**Already pinned by tests.** `ThreefoldRepetitionTest.secondOccurrenceIsNotYetADraw` guards the game rule (three occurrences) so a fix does not accidentally change it; `engineDoesNotAvoidRepetitionWhenWinning` plus the cold-table / warm-table pair in `BlunderTest` (`repetition_withColdTable_blocksTheCheckAndAvoidsTheDraw`, `repetition_withWarmTable_walksIntoTheDraw`) characterize the defect from both sides — the same position and depth, two table states, two different moves. The warm-table case is marked `TODO` and must start failing when the fix lands.

---

## Search cluster plan — History → PVS → LMR

The three remaining low-effort search items — [§ 12.5 history](roadmap.md#125-history-heuristic--s--3050-elo), [§ 12.20 PVS](roadmap.md#1220-principal-variation-search-pvs--negascout--s--1025-elo), and [§ 12.3 LMR](roadmap.md#123-late-move-reductions-lmr--s--50100-elo) — are the highest-leverage work once the tapered evaluation ([§ 12.7](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined)) has landed. They reinforce each other, so the order and the measurement baselines matter more than the raw Elo estimates. This expands step 4 of the [suggested implementation order](roadmap.md#suggested-implementation-order) below into a concrete build-and-measure plan.

**Baseline.** The tuned tapered build, `V_tapered`, once validated. Every step below builds on it.

**Step 1 — History heuristic, alone.** Standalone, low-risk, and the *ordering foundation* LMR depends on: LMR's "reduce late quiet moves" gamble only pays off when the best move is already searched early, which good quiet-move ordering (TT + killers + history) provides. Doing history first de-risks LMR. Measure by SPRT against `V_tapered`; keep if positive → `V_hist`.

**Step 2 — PVS (NegaScout), alone.** A low-risk search reformulation: search non-first moves on a null window and re-search on a fail-high. Small direct gain, but it provides the null-window / re-search scaffold that LMR plugs into. It must be *result-equivalent* to plain alpha-beta — the same best move and score at a fixed depth, only fewer nodes — so add a regression test; any divergence is a bug. Measure against `V_hist`; the gate is "no regression" plus a hopefully small gain → `V_pvs`.

**Step 3 — LMR, on top of PVS.** The large but high-variance payoff. It needs *both* good ordering (history) and the null-window scaffold (PVS); built on plain alpha-beta it would be less efficient and would rework the same move loop twice. Get the reduction formula and the exclusions right — do not reduce captures, promotions, check-givers, killers, or the TT move — and re-search at full depth on a fail-high, reusing the PVS re-search. Measure against `V_pvs`; this is the payoff measurement — if it comes back flat or negative, retune the reduction rather than discarding it. Guard with the tactical suite (WAC / behavior tests): too-aggressive LMR misses tactics. → `V_cluster`.

**Baselines — decide incrementally, track cumulatively.**

| Step | Change | Decision SPRT against |
|---|---|---|
| 1 | History | `V_tapered` |
| 2 | PVS | `V_hist` |
| 3 | LMR | `V_pvs` |
| — | whole cluster (for the record) | `V_tapered`; plus the fixed anchor (4.2.2 / Pulse) for the common Elo scale |

The keep/discard **decision** is always the incremental SPRT against the immediate predecessor — clean attribution, as with the tapered null test against 4.2.2. The cumulative gain is additionally tracked against the fixed anchor so every version stays on one scale.

**What to bundle.** History always stands alone (low-risk, its own value, and an LMR precondition). PVS and LMR *may* be bundled into one build if saving a measurement round matters — they share the re-search loop — at the cost of slightly muddier attribution; since PVS rarely hurts, a bundle result is almost entirely LMR. The default is to keep them separate: a PVS "no-regression" gate, then the LMR payoff.

**Two constraints.**

- All three are **variant-agnostic** (they help standard chess and Chess960 equally), so measure on the standard harness; an optional single 960 cross-check of the finished cluster is enough.
- **Toggle only one feature per step**, with identical TC and openings, or LMR gets confounded with null-move pruning ([§ 12.2](roadmap-done.md#122-null-move-pruning--done-76-elo)) and evaluation interactions.

---

## Suggested implementation order

| Step | Item | Combined effort | Cumulative Elo (rough) |
|---|---|---|---|
| 1 | [§ 12.9 UCI minimal](roadmap-backlog.md#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement) — FEN importer + `UciHandler` + HIARCS/Stockfish baseline gauntlet | M (1–2 days) | — (GUI + baseline measurement) |
| 2 | [§ 12.10 In-process harness](roadmap-backlog.md#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics) — node-count bench + WAC EPD runner (self-play loop optional, covered by cutechess-cli from step 1) | S | — (per-change diagnostics) |
| 3 | [§ 12.1 Transposition table](roadmap-done.md#121-transposition-table--done-93-elo) (fail-soft alpha-beta is already in place, see [§ 12.13](roadmap-done.md#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--done)) | M | +150 – +300 |
| 4 | [§ 12.3 LMR](roadmap.md#123-late-move-reductions-lmr--s--50100-elo) + [§ 12.20 PVS](roadmap.md#1220-principal-variation-search-pvs--negascout--s--1025-elo) + [§ 12.5 history](roadmap.md#125-history-heuristic--s--3050-elo) | S | +260 – +475 |
| 5 | [§ 12.2 Null-move pruning](roadmap-done.md#122-null-move-pruning--done-76-elo) — **DONE** | S | measured **+76.0 ± 10.1** for NMP itself |
| 6 | [§ 12.4 Check extensions](roadmap.md#124-check-extensions--s--1530-elo) + [§ 12.8 aspiration](roadmap.md#128-aspiration-windows--s--2040-elo) | S | +340 – +620 |
| 7 | [§ 12.6 Quiescence search upgrade](roadmap.md#126-quiescence-search-upgrade) — all-captures + MVV-LVA + SEE pruning + delta pruning + optional TT integration | M | +380 – +700 |
| 8 | [§ 12.7 Eval upgrades](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined) + [§ 12.21 King safety](roadmap.md#1221-king-safety--m--3060-elo) | M | +420 – +770 |
| 9 | [§ 12.12 Real time management](roadmap.md#1212-real-time-management-heuristics--s--m--3060-elo) | S–M | +450 – +830 |
| 10 | [§ 12.11 Chess960](roadmap-backlog.md#1211-chess960-fischer-random-support--m-no-elo-on-standard-chess-but-opens-a-new-variant) (optional, opens a new variant) | M | — (on standard chess) |

The order is deliberate: **UCI first**, because (a) it yields an immediately visible GUI, (b) `cutechess-cli` becomes available as the measurement workhorse, and (c) a baseline gauntlet against fixed-depth Stockfish anchors every later improvement against a stable external reference. The in-process harness then adds fast per-change diagnostics. TT is the next biggest single jump, and LMR / null-move / aspiration all assume it exists. The eval upgrades come last because their interactions with the search are the easiest to misjudge without measurement. Chess960 is last of all because it gives zero Elo on standard chess and is best tackled once the core engine is strong.

What is *not* on this list — neural-network evaluation (NNUE), parallel search ("Lazy SMP"), and endgame tablebases — would each be a much larger project than anything above, and would shift the character of the engine away from "hand-written, single-threaded, study-friendly". They are out of scope for the foreseeable future.
