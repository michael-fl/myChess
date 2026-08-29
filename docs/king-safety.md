# King Safety — Build Plan

> **Status: not started. Three hand-crafted attempts have been measured and shelved,
> all net-negative.** This document is the plan for a fourth attempt that does not repeat
> them. It supersedes nothing in [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo),
> which stays the short entry in the priority list; this is where the reasoning and the
> concrete steps live.

King safety is step 3 of the [current plan](roadmap.md#current-plan-2026-08-12), ahead of the
search cluster. myChess today has **no notion of how exposed a king is**: the only king-related
signal in the evaluation is the king piece-square table, which encodes "stay back in the
midgame, centralize in the endgame" and nothing about enemy pieces bearing down on it.

### King safety is not king activity

The two are opposite problems and are easy to conflate, so the distinction is worth stating
before anything else in this document depends on it.

| | King **safety** | King **activity** |
|---|---|---|
| Phase | midgame | endgame |
| Question | how exposed is my king? | is my king close enough to the action? |
| Sign | penalty for danger | bonus for centralization |
| Behavior wanted | king stays hidden | king comes out |
| State in myChess | **missing entirely** — this document | **present** — tapered king endgame table, v4.3.1, +7.7 Elo |

The tapered king PST already encodes both directions through the game phase. What is missing is
only the danger side. The two also live apart in the test suite: **20 open `king-safety` cases**,
all in `BlunderTest`, against **3 open `king-activity` cases** — one in `BlunderTest` and two in
`StsDefectTest`. That the activity cases come partly from the STS suite and the safety cases not
at all is itself the point of § 4. Work on one is not work on the other.

This matters concretely in § 4: the obvious-looking metric is named after the wrong one.

---

## 1. What has already been tried, and what it cost

| Attempt | Where it lives | Result vs v4.2.1 | Games |
|---|---|---|---|
| Attacker-count term | branch `4.3.0-king-safety`, `e93fea4` | **−14.7 ± 11.5** | 2220 |
| Pawn shield, standalone | branch `4.3.0-pawn-shield` | **−57.5 ± 17.5** | 920 |
| King-dependent pawn PSTs | branch `king-safety-pst` | **−18.1 ± 11.6** | 2068, LOS 0.1 % |

**Note the branch names**, because § 12.21 and earlier drafts of this document got the first
one wrong: `4.3.0-attack-units` is the *build* name under `versions/`, not a branch. The
measured commit `e93fea4` sits on `4.3.0-king-safety`, where the term was still WIP and gated
off behind `if (false && …)`.

Three independent designs, three conclusive losses. That is the central fact this plan has to
answer for: **adding a king-safety term to myChess has, so far, always made it weaker.**

### 1.1 There is a fourth branch, and it is the starting point

**`attack-units`, tip `05f337d`, 2026-08-08 — an unmeasured port of the attacker term onto
today's evaluation.** Its own commit message says so: *"Known effect, pending self-play
measurement."* It was never played.

What it already carries: the 3×3 king zone, per-piece attack units (P1 N2 B2 R3 Q5, king 0)
deduplicated by origin square, the progressive `KING_ATTACK_PENALTY` curve, the ≥ 2-attacker
gate, and a migrated test class `WeightingFunctionAttackUnitTest` with 12 tests — including
suites that were `@Disabled` on the old branch and are active here. The pawn-shield experiment
was deliberately left behind, which matches § 4.3's conclusion that the two halves should be
measured apart.

**It is genuinely resumable, unlike `king-safety-pst`.** 106 commits behind master rather than
171, and — checked, not assumed — `WeightingFunction.java` is **byte-identical between the
branch point and current master** despite those 106 commits, so a rebase meets no conflict in
the file that matters.

**What it is missing is exactly the one thing § 4.2 measured.** The term enters the sum as
`(penalty[white] − penalty[black]) × kingAttackFactor` with `kingAttackFactor = 0.01` fixed and
**no reference to the game phase**. "Tapered" in the commit subject refers to the surrounding
evaluation it was ported onto, not to the term. So it would run at full strength in the
endgame, where the measured sign is **+12 cp rather than −34** — finding F1, unaddressed.

### 1.2 Forensic state of the shelved branches (surveyed 2026-08-29)

Worth recording before anyone tries to resume one of them.

**`king-safety-pst` is not resumable and its tip is not what was measured.** The branch is
**171 commits behind master** and its tip `defc82f` and the measured commit `e84d0de` are
*siblings* sharing the parent `4a68aaf`, not a chain — `e84d0de` exists only as a loose commit,
on no branch at all. They differ in the endgame gate: `e84d0de` carries
`GameStatus.kingMayBecomeActive` (opponent non-pawn material ≤ 700), `defc82f` reverts to the
crude `plyCount > 60`. All three archived jars under `versions/` contain
`kingMayBecomeActive`, so **no measured build corresponds to the current branch tip**.

Two further runs exist under the name `…-pst-base` that § 12.21 does not mention: −20.9 ± 18.6
over 759 games and −2.5 ± 12.6 over 1702 games (LOS 35 %, i.e. null). What distinguished
"base" cannot be recovered — cutechess-cli does not echo engine command lines, and the logs
carry only the display names.

Independently of all that, the branch patches
`getPieceSquareWeight(piece, field, kingField)`, an API that no longer exists: since v4.4.0 the
tables are PeSTO values with midgame and endgame packed into one `int` behind
`getCombinedWeight(piece, field)`. Its four hand-written single-phase byte tables are
structurally incompatible with the tapered representation.

**Do not rebase or revive it.** § 12.21's own conclusion rules the approach out anyway:

> keep the eval a pure function of the position (**avoid king-position-dependent piece values**)

---

## 2. Why the attempts failed — the three forensic findings

From the review of `e93fea4` (branch `4.3.0-king-safety`) alongside its match log. Each points at a
fix beyond "just run a tuner over it".

**F1 — no phase scaling.** The term fired at full magnitude in the endgame, where two pieces
bearing on the enemy king's 3×3 zone are usually incidental (R + K vs K), not an attack. Pure
noise, added to every endgame position.

**F2 — it fired on presence, not on danger.** Any two distinct attackers on the ring triggered
the attacker-side bonus, so the engine over-committed pieces toward the enemy king on
speculative attacks that its own tactical search then refuted. This is the "static noise added
to a strong search" failure mode, concretely.

**F3 — Texel cannot tune the part that matters most.** The `KING_ATTACK_PENALTY` curve is
linear per bucket, so each bucket value *is* Texel-tunable. But the Zurichess `quiet-labeled`
set under-samples exactly the sharp positions the curve exists for, so tuning on it mostly
**shrinks the curve toward neutral** rather than learning attacking value. And the unit weights
`ATTACK_UNIT_OF_PIECE` select the bucket index, which makes them **non-linear and not
Texel-tunable** at all.

**F4, added here — magnitude discipline.** The shield term was four times worse than the other
two, and the recorded suspicion is that at up to −0.9 pawns it was simply far too strong: it
made the engine passive, shunning sound shield-pawn advances and even the fianchetto. Whatever
is built next starts small and is allowed to grow only against measurement.

---

## 3. What has changed since, and what has not

**Available now that was not available then:**

- **Tapered evaluation is shipped** (v4.3.0 – v4.4.0, ≈ +130 Elo). F1's prerequisite exists:
  `WeightingFunction` already computes a game phase and blends midgame against endgame values.
- **Texel infrastructure is mature** — `TexelTuner`, the `*TexelData` adapters, a self-play →
  EPD pipeline, and `hybrid.epd` (1 487 619 positions, Zurichess plus ~4 % myChess self-play).
- **Twenty isolated king-safety cases** now exist in `BlunderTest`, up from the two that
  originally justified this step, plus five marked `fixed` and one `guard`.
- **Depth-stability is measured** (`test-results/kingsafety-depth-stability-4.4.2.jsonl`): of
  the cases that pin a move, **7 are depth-stable evaluation defects**, 5 oscillate, 6 converge
  with depth, 1 has a questionable premise.

**Not available, and needed:**

- **No SPSA infrastructure.** F3 says the unit weights are not Texel-tunable. Either they stay
  fixed at hand-picked values (acceptable for a first cut) or SPSA has to be built.
- **No dataset with king danger in it.** This is the real gap; see step 1.

---

## 4. The instrument problem — how progress will be measured

**STS is the wrong tool here, despite appearances.** The suite's theme 11 is named *King
Activity* and myChess scores 62.6 % on it at depth 8 — but that theme is about activating the
king as a piece in the endgame, not about king danger in the middlegame. Its own diagnosis in
[`sts-history.md`](sts-history.md) classifies it as **depth**-limited (24 % of its room
recovered by going from d8 to d10), which is the opposite of what a static term addresses. Do
not use theme 11 as the success metric.

**The 7 depth-stable cases were the intended instrument** — the ones that by construction
cannot be fixed by searching deeper. They were measured before anything was built, and the
result is § 4.1: **they are not a usable target set.** Read that before planning around them.

### 4.1 The premise, measured (2026-08-29) — and it does not hold

`tools/king-safety-margins.py` ran the seven cases against the current build at depth 10 and
against Stockfish 18 at depth 24, and computed the quantity none of the three failed attempts
ever measured: **how large a swing a term would have to produce to change myChess's mind.**
Raw data in `test-results/king-safety-margins.jsonl`.

**Finding 1 — three of the seven are not move-choice cases at all.** In `captureOnF6`, `qb4`
and `keBKOXd1` myChess already plays Stockfish's move. Its *score* is catastrophically wrong
(+5.00 while mated in six, in `keBKOXd1`), but no term can improve a choice that is already
correct. These three can only be judged on evaluation accuracy, which matters indirectly — a
wrong score at an interior node misleads the search elsewhere — but is not measurable here.

**Finding 2 — for the four real cases, the required margin is 4 to 182 cp.** `qd2` 182,
`hxg4` 123, `bxd4` 100, `qa3` 4. So the term has to be worth **one to two pawns of
differential** in three of the four. That is already at the magnitude that made the shelved
shield term passive.

**Finding 3, the decisive one — the signal does not discriminate, and sometimes points the
wrong way.** Measuring the exposure of the *endangered* king after myChess's move against
after Stockfish's move:

| Case | shield | open files | defenders | attackers | margin needed |
|---|---:|---:|---:|---:|---:|
| `qd2` | −1 | 0 | 0 | 0 | 182 cp |
| `hxg4` | −1 | +1 | −1 | +1 | 123 cp |
| `bxd4` | **+1** | **−1** | 0 | +1 | 100 cp |
| `qa3` | 0 | 0 | 0 | 0 | 4 cp |

- **`qa3`: all four measures are identical after both moves.** No king-safety term of any of
  these designs can distinguish the two moves. Not a tuning problem — a zero differential.
- **`qd2`: only the shield differs, by one pawn**, which would therefore have to be priced at
  **1.82 pawns** — roughly double the magnitude that cost −57.5 Elo.
- **`bxd4`: shield and open files point the wrong way.** myChess's losing move leaves *more*
  shield and *fewer* open files than Stockfish's. A shield- or line-based term would push the
  engine further toward the move it already wrongly prefers.
- **`hxg4` is the only case where all four axes agree** with the correct move.

**What this explains.** The three historical failures are usually read as "the term was
untuned" or "the magnitude was wrong". This says something sharper: on a meaningful fraction
of positions the king-safety signal is **anti-correlated** with the better move. Tuning cannot
fix a sign error, and a larger term makes an anti-correlated signal worse — which is exactly
the observed ordering, with the largest term (shield, −57.5) doing the most damage.

**Consequence for this plan.** Step 1 is no longer "build a dataset". It is: **measure how
often the signal points the right way**, over a sample large enough to answer it — the anchor
corpus and the lichess blunder corpus both carry positions with a known better move. If the
signal is right in most positions, the term is worth building and this sample of four was
unlucky. If it is anti-correlated in a third of them, a fourth attempt will fail like the
first three and the theme should be closed rather than retried.

Four cases cannot settle that. They are enough to stop the plan that existed before them.

### 4.2 Step 0 executed (2026-08-29) — the signal is real, and it inverts by phase

`tools/king-safety-signal.py` over all 1 487 619 positions of `hybrid.epd`, keeping the
**390 509** whose material is within ±50 cp of equal, grouped by the white-minus-black
differential of each measure, mean White score converted back to centipawns through the Texel
logistic. Data in `test-results/king-safety-signal.json`.

| cp per unit | midgame (236 205) | late midgame (68 846) | endgame (85 458) |
|---|---:|---:|---:|
| shield pawns | **+32** | +9 | **−14** |
| open files | −32 | −9 | +14 |
| ring attackers | **−34** | −20 | **+12** |
| ring defenders | −7 | +6 | +15 |

**The signal exists and it is not small.** In the midgame one shield pawn is worth **32 cp**
and one attacker on the ring **−34 cp**, on columns that are strictly monotone over samples
from 1 000 to 95 000 positions per group. The full midgame shield range runs −64 cp to +130 cp.

**It does not merely fade toward the endgame — it inverts.** Shield goes +32 → +9 → **−14**,
attackers −34 → −20 → **+12**. That is a sharper statement than finding F1, which asked only
for phase *scaling*: a term scaled to zero in the endgame would still be wrong there, because
the correct endgame sign is the opposite one. The king endgame PST already encodes that
direction, which is presumably why the effect is visible in the data at all.

**This re-diagnoses the three failures, and the new diagnosis fits better.** The shield term
applied up to −90 cp for a fully exposed king with no phase scaling. In the midgame that
magnitude is roughly right — three shield pawns measure 3 × 32 ≈ 96 cp. In the endgame the
true value is about **+42**, so the term was wrong by ~130 cp with the wrong sign in every
endgame position it touched. "Too strong" was the standing explanation; "unscaled across a
sign change" fits the evidence better, and it explains the ordering the magnitudes alone do
not: the damage is proportional to the size of the term, so the largest one (shield, −57.5)
was the worst.

**The progressive curve was the right idea.** The attacker column is visibly non-linear: 0 → 1
attacker costs 20 cp, 1 → 2 costs a further 53 cp. The shelved term's danger curve was
modelling something real.

**Two limits on these numbers.** The corpus is *quiet* positions — Zurichess filters checks
and tactical shots out — so sharp attacks are under-sampled and the attacker figure in
particular is a **lower bound**. And a measure this crude (a pawn counts like a queen, shelter
looks only two ranks ahead) can only understate a signal, never invent one.

**What it does not rescue.** § 4.1 stands: the largest differential these measures can produce
is three shield pawns ≈ 96 cp, and the depth-stable cases need 100–182 cp — two of them with
*zero* differential on every measure. The seven cases remain unusable as the target set. What
changed is the verdict on the theme: there is something real to build, it is just not
measurable on those seven positions.

### 4.3 Which corpus — and the one design conclusion the data supports

Five corpora, midgame values, after a controlled experiment that removed the obvious confound:

| corpus | extraction | kept | shield | attackers |
|---|---|---:|---:|---:|
| Zurichess `quiet-labeled` | external pipeline | 358 694 | **+35** | −34 |
| human masters, 120 000 games | ours, 8/game | 580 849 | +9 | −43 |
| human masters, same games | ours, 40/game, no end-skip | **1 772 609** | **+11** | −50 |
| anchor bracket vs. external engines | ours | 7 297 | +16 | −57 |
| myChess self-play, standard | ours | 55 879 | +6 | −33 |

**The extraction settings are not the explanation.** Sampling the *same* 120 000 games 3.5×
more densely, and no longer skipping the final plies, moved the shield value from +9 to +11.
The corpora genuinely differ; Zurichess is the outlier, and an earlier version of this section
blamed self-play for a gap that human master games show just as much. That claim is withdrawn.

**What replaces it is a stronger finding — the shield signal is asymmetric.** In 1.77 million
master positions:

| shield difference | −3 | −2 | −1 | 0 | +1 | +2 | +3 |
|---|---:|---:|---:|---:|---:|---:|---:|
| implied cp | −3 | −7 | −6 | +11 | +34 | +49 | +65 |

**Having fewer shield pawns than the opponent predicts nothing** — −3 to −7 cp, indistinguishable
from noise over groups of 100 000+. **Having more predicts a great deal.** Zurichess's column is
symmetric (−64 … +130); this one is a hockey stick.

The reading that fits: a strong player opens the shelter in front of their own king
*deliberately* — pawn storm, fianchetto, a rook lift — and it does not cost them. A static
penalty for a missing shield pawn punishes exactly those sound decisions. **That is the
passivity the shield term was suspected of and it is now visible in data**: the −57.5 Elo
result was not merely a magnitude error, it was penalizing a feature whose negative half
carries no information.

**Design consequence, and it is concrete.** All three shelved attempts penalized king exposure
symmetrically. The data supports **rewarding shelter, not punishing its absence** — a
one-sided term. And it supports the attacker count without reservation: −34 to −57 across
every corpus and both extraction settings, progressive in shape, the most robust number in
this whole investigation.

**Still open.** Which calibration transfers to engine-versus-engine play, where myChess
actually competes: master games say +11 asymmetric, Zurichess engine games say +35 symmetric.
That question is what an SPRT answers, not another scan.

**Order of evidence:**

1. the 7 depth-stable cases — cheap, deterministic, and diagnostic of *this* term;
2. the remaining 13 open king-safety cases — should not regress;
3. `bench` — the node signature *will* change (this is an eval change); record it, do not
   assert on it;
4. SPRT against the current master — the verdict, and only meaningful after 1 and 2 look right.

Running the SPRT first is how the previous three attempts each burned 900–2200 games to learn
"negative" without learning *why*.

---

## 5. Build plan

### Step 0 — does the signal carry information? — **DONE, and it clears**

Executed 2026-08-29, result in § 4.2: yes, at +32 cp per midgame shield pawn and −34 cp per
midgame ring attacker, on a sign that inverts by the endgame.

Recorded here because the shape of the step is reusable and the first version of it was
wrong. The plan initially proposed measuring over a corpus of *blunders*. That would have
answered the wrong question: blunder corpora select for losses of three pawns or more, i.e.
for tactical accidents, while a shield pawn advanced one square too far costs 0.2–0.5 pawns
immediately and only becomes fatal twenty moves later. The subject would have been filtered
out of the sample. **Prevention shows up as a statistical tendency over ordinary positions,
not as a flip in a handful of sharp ones**, which is what the executed version measured.

The tool then had to be corrected once more, for the same defect the term itself is charged
with in F1: the first run pooled all phases and reported +15 cp per shield pawn — the average
of a +32 midgame effect and a −14 endgame one. A real signal read as half a signal. Splitting
by phase doubled it.

### Step 1 — build the missing dataset

F3 is the finding that killed the last tuning idea, and it needs a sharper answer than
"include sharp positions". **Texel tuning on genuinely tactical positions is unsound**: the
static evaluation of a position whose tactics are unresolved does not predict the game result,
so such positions add noise, not signal. That is precisely why `quiet-labeled` is quiet.

The resolution is a third category: **quiet but king-exposed**. Positions with no check, no
winning capture available (SEE ≤ 0 on every capture), and therefore a meaningful static
evaluation — but with an exposed king: enemy pieces bearing on the king ring, missing shield
pawns, or an open file toward the king.

- Source PGNs: the anchor bracket (2000 games against five rated opponents) and the lichess
  corpus. **Not self-play** — § 4.3 measures the shield signal at +6 cp there against +35 cp
  on Zurichess, because neither side in a self-play game punishes an exposed king.
- Tooling: `PgnQuietEpdExtractor` already implements the quiet filter; this needs a sibling
  filter that *additionally* requires king exposure, not one that relaxes quietness.
- Deliverable: an EPD in the same `c9 "result"` format, plus a line in
  [`tuning-data/README.md`](../tuning-data/README.md), which currently documents
  `quiet-labeled` but **not** `hybrid.epd` — a gap worth closing in the same pass.
- Gate: the set has to be large enough to tune a bucket curve. If it is not, say so and stop —
  a curve tuned on a few thousand positions is a hand-picked curve with extra steps.

### Step 2 — resume `attack-units` and add the one missing thing

The term does not have to be written; § 1.1 has it ported and tested on branch `attack-units`
(`05f337d`), never measured. The work is therefore short and each item is checkable:

1. **Rebase `attack-units` onto master.** `WeightingFunction.java` is byte-identical between
   the branch point and master, so the file that matters carries no conflict.
2. **Multiply the penalty by the game phase.** One expression. Today it is
   `(penalty[white] − penalty[black]) × kingAttackFactor` with a fixed factor and no phase
   term, which is finding F1 and, per § 4.2, means the wrong *sign* in the endgame rather than
   merely the wrong size. Scaling to zero at the endgame end is the safe form; whether the
   measured +12 cp there is real king-safety information or just the king activity the tapered
   king PST already encodes is not something the data separates, and double-counting it would
   be worse than ignoring it.
3. **Re-calibrate the curve against § 4.2.** One attacker measures 34–57 cp in the midgame.
   The inherited table gives two light pieces (4 units) only 15 cp, which is far too little,
   and reaches 800 cp at 20 units, which never occurs. The shape — progressive — is confirmed
   by the data; the values are not.
4. **Replace the `≥ 2 attackers` gate** (F2). It asks whether enough pieces are present, not
   whether the position is dangerous.
5. **Settle the two deferred test expectations.** `WeightingFunctionTest.testPosition34/35`
   read higher on the branch because the term fires; the commit left them unadjusted on
   purpose, pending a decision on whether the term stays.

Keep the shelter half out of this measurement entirely. § 4.3 leaves it with two conflicting
calibrations, and combining them would repeat the mistake all three earlier attempts made —
a negative result that does not say which half caused it.

### Step 3 — tune what can be tuned, fix the rest

- Bucket values: Texel, on the step-1 dataset.
- Unit weights: **fixed** for the first cut. SPSA only if the fixed version measures
  promising; building SPSA to tune a term that has never once measured positive is
  premature.

### Step 4 — measure in the order of § 4

Stop at the first stage that says no. Each of the three previous attempts would have been
stopped by stage 1 at a fraction of the cost.

---

## 6. Expectations, stated up front

§ 12.21 carries a headline of ≈ 30–60 Elo. **Treat that as an upper bound and probably an
over-estimate.** The section's own forensic note says so:

> for an engine whose search already resolves king attacks tactically, the ceiling of a static
> king-safety term is likely well below this section's headline estimate

Three attempts have measured −14.7, −18.1 and −57.5. A fourth landing at **0** would already be
an improvement over the state of the art here, and a small positive would justify the theme.
Nothing about the history supports planning for +40.

**Abort conditions, agreed in advance:** the 7 depth-stable cases do not move; or the step-1
dataset is too small to tune on; or the first SPRT reads negative with the term at its capped
magnitude. In each case, record the number and shelve — the value of this document is that the
next person reads four dated failures instead of repeating a fifth.

---

## 7. Related

- [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo) — the priority entry and the
  original attempt log.
- [`testing.md` § 11.3](testing.md) — how a lost game becomes a case; the anchor corpus that
  step 1 would mine.
- [`tapered-evaluation.md`](tapered-evaluation.md) — the phase machinery step 2 builds on.
- [`evaluation.md` § 5](evaluation.md#5-evaluation-function) — the omissions list that flags
  "king safety beyond castling" as not implemented.
