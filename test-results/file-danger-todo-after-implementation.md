# After the file-danger term is implemented — my checklist

Trigger: the user's `WeightingFunction` implementation of the file-danger term stands. Everything
below is assigned to me, including the production edits, which is a deliberate exception to the
usual split (I write tests and reviews; the user writes production code).

Order matters: 1–3 are correctness, 4–5 are measurement, 6 is documentation. Do not start the SPRT
before 3 is green.

---

## 1. Wire the term as the ninth tunable factor — REQUIRED, not polish

Three edits in `WeightingFunction`, all in the **same order**:

| Where | What |
|---|---|
| `TUNABLE_FACTOR_NAMES` (~line 469) | add the factor's name |
| `tunableFactorValues()` (~line 476) | add the factor's value |
| `analyzeFactors`, `features[]` (~line 500) | add `(penalty[0] - penalty[1]) * 100.0` |

The `* 100.0` is required because the factor is applied outside the `/100` normalization, exactly
like `castlingFactor` and `chessFactor`. The feature unit is *centipawns per unit factor*.

The factor stays `private static final`. The Texel tuner runs offline from test sources and only
reports suggested values; a human writes them back.

**Why this is correctness and not tuning.** `analyzeFactors` asserts the evaluation is linear in the
factors. `FactorTexelData:99` relies on it:

```java
baseEval = breakdown.eval() - dot(breakdown.features(), tunableFactorValues());
```

A term that is in `calculatePositionWeight` but not in `features[]` folds into the supposedly
constant base, so every future tuning run tunes the other factors against a base that moves with
king danger. No crash, no warning — just worse factors.

Proof that this is a live failure mode, not a hypothetical: on branch `attack-units` the
attack-unit term was added to `calculatePositionWeight` and not to `analyzeFactors`, and
`FactorTexelDataTest.breakdownReconstructsTheRealEvaluation` fails there today with
`expected: <209.0> but was: <190.9>`. It went unnoticed because the class carries `@Tag("slow")`
and does not run in the fast suite.

## 2. Statistics counter for the material-only shortcut

- `incrMaterialOnlyLeafCount()` plus getter in `Statistics`, in the style of the existing counters.
- Increment where the shortcut fires, `QuiescenceSearch.calculatePositionWeight` (lines 134–141).
- The rate reads against the existing `getQuiescencePositionsCount()`: the check runs exactly once
  per quiescence node entry, so the quotient is the firing rate directly.

Why it is wanted: the shortcut returns raw material and skips the whole positional evaluation
whenever `materialDelta` exceeds ±200 cp since the search root, so a king-safety term is blind in
sacrificial lines. If the SPRT comes out slightly negative, the firing rate is the first number
needed to explain it, and it does not exist today.

**Build the counter, not the repair.** Prior measurements all argue against touching the shortcut:

| variant | result |
|---|---|
| shortcut removed | −34.0 Elo, H0 at 546/1600 (roadmap § 12.18) |
| threshold 300 (fires less) | −18.3 ± 23.7, H0 at 608/1600 |
| threshold 200 (shipped) | baseline |
| threshold 100 (fires more) | −0.7 ± 14.4 over 1600, flat |

## 3. Tests

- **`StatisticsTest`** — does not exist yet; create it. The counter increments, and the existing
  counters keep working.
- **Counter behavior under a real search** — fires in a position with a large material swing,
  stays at zero in a quiet one.
- **The term itself in `WeightingFunction`** — mirror `WeightingFunctionAttackUnitTest`: one case
  per danger level, the phase blend, color symmetry (a mirrored position must give the negated
  evaluation), a missing king, and kings on edge files where the three-file window narrows.
- **`FactorTexelDataTest.breakdownReconstructsTheRealEvaluation` must be green.** This is the net
  that guards the coefficient from item 1. It is `@Tag("slow")` and needs
  `tuning-data/quiet-labeled.epd`, so run it explicitly — it will not run in the fast suite.
- Fast suite green: `mvn test -DexcludedGroups=slow`.

## 4. Cost measurement — separate from the behavior check

Two different expectations, do not conflate them:

- **The counter alone must not change behavior** → the bench node signature must stay
  **336,412,842**. An unchanged signature is the entry ticket to measuring cost, never the verdict
  on it.
- **The file-danger term does change behavior** → the signature will move, and the new one gets
  recorded in `docs/bench-history.md`.

Then measure NPS against master. The attack-unit term cost 22 % of search speed, and that — not its
evaluation content — is what produced the −42.9 Elo. File danger should be far cheaper (three files
of eight squares, no mask to build, nothing to clear per node), but "should be cheap" was predicted
last time too and had to be measured.

## 5. SPRT

- Base is **`4.6.0` from master**, not the attack-units branch. The fitted table is calibrated
  against master's evaluation; on the branch it would double-count.
- Build, copy to `versions/`, use the house template: candidate first, `tc=40/60`,
  `elo0=-3 elo1=15 alpha=0.05 beta=0.05`, `2moves_v2.pgn plies=8`, `-concurrency 4`,
  `-ratinginterval 10`, `-draw movenumber=40 movecount=8 score=40`,
  `-resign movecount=4 score=600`.
- Decision rule as agreed: ship only if clearly positive; neutral or negative shelves it.

## 6. Documentation

- `docs/king-safety.md` — new section for the term: the five levels, the fitted table, the control
  result, and why the six-level variant was dropped.
- `docs/evaluation.md` — the term itself, and the new tunable factor.
- `docs/testing.md` — the family tally and the new test classes.
- `docs/roadmap.md` — the outcome, whichever way it goes.

## Not tied to this trigger, still open

Three corrections owed on `master`, recorded in
`test-results/sprt-attack-units-opt-how-to-read.md`. They need a branch switch and an explicit
instruction:

1. the conviction rate "6 vs 1" is a small-count statistic and does not reproduce (run 2: 7 vs 6);
2. roadmap § 12.21 quotes −42.9 without the early-stop caveat that § 12.23 applies;
3. the "diffuse evaluation error" attribution stands only until the second SPRT ends — which it now
   has, at roughly zero, so that attribution needs rewriting.
