# Tapered Evaluation (Phase-Dependent Piece-Square Tables) — Design Notes

Conceptual design notes for introducing a *tapered* (phase-interpolated)
evaluation into myChess. This document explains the **idea and the decisions**
in more depth than [roadmap § 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy);
it deliberately contains **no concrete code** — the implementation is the
author's to write.

Related material: [§ 5 Evaluation Function](evaluation.md#5-evaluation-function),
[roadmap § 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy)
(staged rollout), [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo)
(king safety, which depends on this).

---

## 1. Why — the problem tapered evaluation solves

A single piece-square table per piece has to be a compromise between two phases
of the game whose demands are **opposite**:

- **King:** in the middlegame it wants to hide (castle, stay off the center); in
  the endgame it wants to be active in the center. One table cannot express
  both.
- **Advanced pawns:** near-decisive in the endgame (they promote), only mildly
  useful in the middlegame.
- **Minor/major piece placement:** the ideal square shifts as the board empties.

Tuning a single-phase table on a **phase-mixed** dataset makes this worse, not
better: the tuner fits the *average* of the two regimes and ends up wrong in
both. This is not hypothetical for myChess — it is the confirmed cause of the
offline-tuning failures recorded in
[§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy):
`eval-tuning-v1` lost **−15.6 Elo** despite a better tuning proxy, and the naive
all-piece tune produced an endgame-shaped king table that would be suicidal in
the opening. **Tapered evaluation removes the confound and is the prerequisite
for any further evaluation tuning to pay off.**

---

## 2. The core idea

Keep **two** values for every piece-square (and every material value): a
**midgame (MG)** value and an **endgame (EG)** value. Compute a single
**game-phase** scalar from the material left on the board, and blend:

```
eval_pst = ( mg_component · phase  +  eg_component · (PHASE_MAX − phase) ) / PHASE_MAX
```

When lots of material is on the board the blend is almost pure MG; as the board
empties it slides smoothly toward pure EG. There is no branch, no cutoff — one
evaluation, both regimes, continuously interpolated.

This is the well-known **PeSTO**-style evaluation ("Piece-Square Tables Only"):
a tapered eval with MG/EG tables per piece type. myChess adopts the *mechanism*;
whether it adopts PeSTO's *values* is a separate question (see § 6).

---

## 3. Game phase

The phase is a smooth scalar in `[0, PHASE_MAX]` derived from **fixed** per-piece
weights summed over **all** pieces on the board (both colors):

| Piece | Phase weight |
|---|---|
| Knight | 1 |
| Bishop | 1 |
| Rook | 2 |
| Queen | 4 |
| Pawn, King | 0 |

The start position sums to `PHASE_MAX = 24` (4·1 + 4·1 + 4·2 + 2·4); bare kings
(plus pawns) sum to 0. Design points:

- **Color- and balance-blind.** The phase measures the *type* of position (how
  much material is still flying around), not who is ahead. A lopsided position
  with lots of material is still tactically a middlegame; a material-poor one is
  an endgame even if one side is winning. Whose piece it is does not matter — the
  advantage is expressed entirely in the evaluation *difference*, never in the
  phase.
- **Fixed weights, not `nonPawnMaterial`.** The existing
  `GameStatus.nonPawnMaterialWeight` is built from the *tunable*
  `WeightingFunction.weightOfPiece`. Deriving the phase from it would make the
  evaluation a product of two tunable parameters (`mg_value · phase(material)`),
  which is non-linear and breaks the linear model the tuner depends on (§ 5).
  The phase must use **constant** weights.
- **Computed in the existing piece scan.** The evaluation already visits every
  piece once; accumulate the phase there. No new stored state and no incremental
  bookkeeping on the `GameStatus` stack are required.
- **Clamp to `[0, PHASE_MAX]`.** Under-promotion to a third queen can push the
  raw sum above `PHASE_MAX`; clamp so the blend weight stays in `[0, 1]`.
- **Replaces the binary endgame flag.** This supersedes
  `GameStatus.isEndGame() { return plyCount > 60; }` and the king-PST endgame
  cutoff in [§ 5.2](evaluation.md#52-piece-square-tables). Remove the binary
  switch so there is only one notion of phase.

---

## 4. Fitting into myChess's evaluation

myChess is **not** a PST-only engine: it also scores mobility, threats, checks,
castling, doubled pawns, and undefended pieces, and will grow a king-safety term
([§ 12.21](roadmap.md#1221-king-safety--m--3060-elo)). Two consequences:

- **Scope for the first version: taper material and the PSTs only.** Leave the
  other terms single-valued for now. A natural follow-up is to phase-scale the
  terms that are themselves phase-dependent — king safety most of all (it should
  fade to nothing in the endgame) — but that is a later step, not part of the
  first cut.
- **Beware double-counting if importing PeSTO values.** PeSTO's tables were
  trained *alone* and bake in king safety and pawn structure that myChess
  already scores separately. This is a key reason the rollout (§ 6) seeds tuning
  from our own tables rather than PeSTO.
  > **Outcome (2026-08-11) — this concern did not materialize.** PeSTO's tables
  > were eventually imported wholesale *while keeping* every myChess term, and the
  > feared double-counting cost nothing: the build measured **+32.6 ± 12.4 Elo**
  > over v4.3.4 and shipped as v4.4.0. See
  > [roadmap § 12.7.5](roadmap.md#1275-pesto-piece-square-tables--done-326-elo-v440).
  > A plausible reading is that the wiki's tables are pure *placement* offsets with
  > material held in a separate `mg_value` / `eg_value` array, so the overlap with
  > myChess's own terms is smaller than "trained alone" suggests.

Integration details to keep consistent with the current evaluation:

- **Scaling convention.** The current PSTs feed `positionWeight`, which is scaled
  by `positionFactor` and expressed in centipawns. The tapered blend must slot
  into that same accumulation and scaling so the units are unchanged.
- **Black tables via `invert()`, per phase.** The rank-flip that derives the
  black tables from the white tables applies **independently to the MG and the
  EG table**. Both phase tables must be inverted; the column ordering is left
  untouched, exactly as the antisymmetry checked by `MirrorEvalTest` requires.
- **Material-only shortcut.** The `EVALUATE_MATERIAL_ONLY_THRESHOLD` path returns
  material only and skips the positional evaluation, so the phase only needs to
  be computed on the positional path.

---

## 5. Why this unlocks Texel tuning

The tuner works on a linear model, `eval = baseEval + features · params`. Under
tapering, every tunable value becomes an **MG/EG pair**, and each position's
feature is split by the (constant) phase:

```
feature_for_MG = placement · phase / PHASE_MAX
feature_for_EG = placement · (PHASE_MAX − phase) / PHASE_MAX
```

Because the phase is a fixed constant per position, both features are known
coefficients and the model stays linear. The important consequence: the
**phase-mixed dataset — previously the problem — becomes the asset.** A
middlegame position (`phase ≈ PHASE_MAX`) puts almost all of its weight on the
MG parameters, so it "teaches" the MG tables; an endgame position (`phase ≈ 0`)
teaches the EG tables. A correlation that is causal only in the endgame (e.g. a
centralized king) is now applied only where the phase is low — exactly where it
holds. This is why the fixed-weight phase in § 3 is load-bearing, not incidental.

---

## 6. Staged rollout

One measurable change per step (mirrors and expands
[§ 12.7.1](roadmap.md#1271-tapered-evaluation--staged-rollout-strategy)):

0. **Null test.** Build the taper machinery with **MG = EG = the current
   tables**. Blending two identical tables reproduces today's evaluation
   exactly, so this must be **Elo-neutral by construction**. Any regression means
   a bug in the phase count, the blend, or the table plumbing — catch it here,
   before any value changes.
1. **Seed tuning from our own tables, not PeSTO.** Duplicate the current tables
   into MG/EG and tune from there. This keeps compatibility with myChess's extra
   evaluation terms (§ 4), gives clean attribution (the measured effect is purely
   "phase awareness + our tuning" over a known baseline), and avoids integration
   mismatches. The tuner discovers the MG/EG split itself.
2. **Texel-tune the MG/EG tables** (and material as MG/EG pairs). With the phase
   confound gone, the proxy improvement should finally track Elo. Measure against
   the step-0 baseline by SPRT.

> **Superseded (2026-08-11).** The plan below — tune from our own tables, keep
> PeSTO only as a yardstick — was followed and then overturned by its own
> measurement: PeSTO's tables replaced ours outright in v4.4.0 (**+32.6 Elo**,
> [roadmap § 12.7.5](roadmap.md#1275-pesto-piece-square-tables--done-326-elo-v440)).
> Kept here for the reasoning, which is still sound as a *method* — it was the
> ceiling test's ambiguity, not the plan, that misled: swapping tables and terms
> at once produced a null result that said nothing about either.

**PeSTO as an independent reference, not the seed.** Measure PeSTO-as-is once (its
MG/EG tables and material, column-symmetrized) against the current single-phase
evaluation — a ceiling check on whether proven tapered tables beat our eval,
independent of our tuner. Then compare our tuned result against that reference.

---

## 7. Design decisions and gotchas (a checklist for implementation)

- **Rounding.** The evaluation is integer centipawns; the interpolation divides
  by `PHASE_MAX`, so pick a consistent rounding and keep it symmetric between
  colors (do not let rounding introduce a side bias — `MirrorEvalTest` will
  flag it).
- **Allocation-free hot path.** Follow the project convention: no per-node
  allocation. The phase is one accumulation in the existing piece loop; the
  blend is a couple of multiplies per evaluation.
- **File symmetry.** Keep both phase tables left/right symmetric (a/h, b/g, c/f,
  d/e), consistent with the tuning adapters and the PeSTO note in
  [§ 12.7](roadmap.md#127-evaluation-upgrades--m--4080-elo-combined).
- **Remove the binary king cutoff** so there is a single phase mechanism (§ 3).
- **Tests to add:**
  - antisymmetry (`MirrorEvalTest`) must still hold for **both** phase tables;
  - a phase unit test: start position = `PHASE_MAX`, bare kings = 0, monotonic
    non-increasing as material is removed, clamped;
  - the step-0 equivalence: with MG = EG, the tapered evaluation equals the
    pre-taper evaluation for a spread of positions across all phases.

---

## 8. Open questions to settle while implementing

- **Taper material too?** Yes — the MG/EG split is most of the point for the
  king and pawns, but material values are also mildly phase-dependent (e.g. a
  rook is worth a touch more in the endgame) and are cheap to include as MG/EG
  pairs.
- **Which pieces get a split?** All six. Even where MG ≈ EG (e.g. knights), a
  negligible split costs nothing and keeps the model uniform.
- **Phase granularity.** Integer `0..24` is standard and sufficient.
- **Phase-scaling the non-PST terms** (king safety especially) — deferred to a
  follow-up, but worth keeping in mind so the phase scalar is exposed in a way
  those terms can reuse.

---

## 9. Success criteria

- **Step 0** measures Elo-neutral (plumbing correct).
- **Step 1/2** measure non-negative-to-positive by SPRT against the step-0
  baseline — and, unlike the single-phase attempts, the tuning proxy should now
  move *with* Elo rather than against it, which is itself the signal that the
  phase confound is gone.
- Longer term, the king-safety category of the
  [STS suite](https://www.chessprogramming.org/Strategic_Test_Suite) gives a
  direct read once phase-scaled king safety ([§ 12.21](roadmap.md#1221-king-safety--m--3060-elo))
  is layered on top.
