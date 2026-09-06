# King Safety — Build Plan

> **Status: nothing built, everything measured.** Three hand-crafted attempts were shelved
> net-negative; a fourth is prepared and its numbers now come from data rather than intuition.
> Supersedes nothing in [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo), which stays
> the short entry in the priority list; the reasoning and the steps live here.

> **Priority: this document is the current work, and it stays that way until a king-safety
> variant measures positive Elo.** No search work — LMR, PVS, history heuristic, aspiration
> windows, check extensions — starts before that, and none of it belongs in a recommendation
> arising from this document. The PeSTO ceiling result in
> [roadmap § 12.7.1](roadmap.md) is sometimes quoted as an argument for the opposite; it is
> not one. It bounds *static tables and material*, and says nothing about the dynamic terms
> PeSTO also lacks, of which king safety is one. A closed sub-family below — file danger
> (§ 4.14), pawn shield (§ 12.21) — closes that sub-family and not the topic.

## The short version

| | |
|---|---|
| **Curve to start from** | `0 0 0 13 16 47 47 47 80`, indices 0–8, everything above clamped onto 8 (§ 4.7; supersedes § 4.6's ungated `0 2 2 20 20 20 44 45 85` and § 4.5's `0 0 0 0 0 0 49 40 83`) |
| **Where it comes from** | fitted against Stockfish's *static* evaluation rather than game results, phase-weighted, monotonicity as a constraint of the fit (§ 4.6). The game-result fits of § 4.5 are what it replaces: on a self-play corpus the label carries myChess's own blindness |
| **Why only indices 1–2 are zero** | those two are not separable from zero (p5 = 0.0) and a single minor piece bearing on the king zone is the normal case. Indices 3–5 *were* zeroed by § 4.5 and are not: that was an artifact of the label, and they carry 24.2 % of king samples against 6.2 % for 6–8 (§ 4.6) |
| **Where to build it** | branch `attack-units`, `05f337d` — already ported, never measured, 106 commits behind master with `WeightingFunction.java` byte-identical (§ 1.1) |
| **The one missing change** | multiply by the game phase. Without it the term runs at full strength in the endgame, where the measured sign is the *opposite* one (§ 4.2) |
| **How to implement it** | leave it where it is: branch `attack-units` already calls `increaseAttackUnit` from inside `move(...)`, the walk the evaluation performs anyway. Do **not** refactor it into a separate pass, however much tidier that looks — a standalone scan costs more than the entire evaluation (§ 4.5) |
| **What will kill it** | ~~steering into sacrifices the material-only shortcut then hides (§ 4.4)~~ — measured, and it was not this: the cap held. ~~A diffuse evaluation error plus −21.9 % NPS (§ 4.8, § 4.9)~~ — **also wrong.** Repairing only the speed moved the identical evaluation by ~52 Elo (§ 4.10). What kills this class of term is **cost**, and the evaluation was never the problem |
| **The exchange rate** | the cap at 100 cp is forced by `EVALUATE_MATERIAL_ONLY_THRESHOLD`, so the term's maximum contribution is bounded there while it costs a fifth of the node rate. Any variant must buy a third of a ply's Elo with ≤ 100 cp (§ 4.9) |
| **Expectation** | small. **Six** attempts across two projects measured −14.7, −18.1 and −57.5 in myChess, −46.5, −67.1 and −12.1 in the Audax fork. A **zero** would be progress; § 12.21's headline of 30–60 Elo is not the number to plan for |
| **Measured 2026-08-31** | **−42.9 ± 33.9**, SPRT H0 accepted after 304 games at `tc=40/60`, LOS 0.6 %. See § 4.8 — and read § 4.10 before quoting it |
| **Measured 2026-09-01** | the same evaluation with the § 4.9 repairs: **+9.1 ± 14.5** over the full 1600 games, LOS 89.1 %, no bound reached. Shelved as "not shown to be worth ≥ 15 Elo", which is not the same claim as "neutral" (§ 4.10) |
| **What to build instead** | ~~**file danger**~~ — screened at **2.238 %** explained residual variance against attack units' 1.270 %, which is why it was built (§ 4.11). **That ranking is refuted twice over:** file danger measured ≈ 0 Elo across two runs totalling 5244 games while attack units measured +9.1, so on the only two candidates with both a screen and a match the screen's order is inverted. Stop using it to *rank*; a flat screen is still a stop |
| **Measured 2026-09-03** | file danger with the game-result table: **+0.8 ± 12.3** over 2255 games, stopped for a defect that discounted corner kings. Behaviour confirmed (−34.6 % uncovered king files), cost six times smaller than the bench predicted (−0.041 plies), and the finding that matters: **exposure is a symptom, not a cause** — 70 Elo of association for the baseline, none for the candidate (§ 4.13) |
| **Measured 2026-09-06 — the family is closed** | the repaired build: **−4.9 ± 10.7** over 2989 games, stopped once a clearly positive result became unreachable. The term also turns out not to know about castling — before castling it reads the *central* files — but that costs nothing: priced over 2431 declined central captures it gives up half a centipawn per game **less** than the baseline, so the opening component is zero and the middlegame component is itself the −5 (§ 4.14). The repair works — the corner excess falls from +4.62 pp to +0.10 pp — the target quantity still moves −30.2 %, the cost is 0.04 plies, and the Elo is zero. **Pawn cover beside the king carries no Elo in myChess**, which also retires the shelved shield (−57.5), the king-dependent pawn PSTs (−18.1) and virtual queen mobility. Attacker-based king safety is untouched by this (§ 4.14) |

**Read § 4.1 before using the seven depth-stable cases as a target** — they were the intended
instrument and they are not usable as one.

Several claims in this document were withdrawn as later measurements arrived; each is marked
where it stood rather than deleted, because the reason a number was wrong is usually more useful
than the number.

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
only the danger side. The two also live apart in the test suite: **23 open `king-safety` cases**,
all but one in `BlunderTest`, against **3 open `king-activity` cases** — one in `BlunderTest` and two in
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

### 4.4 What the Audax fork already measured — and the trap it found

`../myChess-Audax` is a fork of myChess **4.3.4** built to play aggressively. It carries a
king-attack term derived from *our* `attack-units` branch, and it has played three matches this
plan did not know about. Its `docs/` are worth reading in full; what follows is what bears on
this section.

| Audax build | change | result vs 4.3.4 |
|---|---|---|
| `0.1.0` | attack term + material threshold 500 | **−46.5 ± 10.2**, 3 000 games (+31.9 at *fixed depth*) |
| `attack-threshold-200` | the same, threshold back at 200 | **−67.1 ± 42.0**, H0 at 173 games, LOS 0.1 % |
| `defend-units` | a defense-only term instead | **−12.1 ± 20.4**, H0 at 660 games |

#### The trap: the attack term and the material-only shortcut fight each other

This is the finding, and no measurement in § 4.2 or § 4.3 could have produced it — those
measure the *signal*, this measures the *implementation inside this engine*.

The shortcut fires once the material swing from the root passes
`EVALUATE_MATERIAL_ONLY_THRESHOLD` = 200 cp. A minor piece is 300, so **every piece sacrifice
is past it**. At the root the engine weighs `−300 for the piece` against `+X for the attack`;
if X is large enough the trade looks good, it plays it — and at the leaves beyond, the shortcut
switches the positional evaluation off. **The attack term is part of that evaluation, so it
goes silent exactly in the positions it steered into.** The engine is left holding a sacrifice
whose compensation it can no longer see.

Either half alone is harmless. Together they are worse than neither: `attack-threshold-200`
measures **worse than having no attack term at all**. Audax's own reading — "with the threshold
at 500 it sees the compensation; without the attack term it never steers there; having one
without the other is the bad combination" — is supported by its draw rate (35.8 %, close to the
aggressive build's 33.4 %): the engine still plays sharply, it just cannot calculate what it
walked into.

#### Why −67.1 is not the verdict on our branch

**Five differences, corrected 2026-08-30.** An earlier version of this section compared the two
curves *at the same table index* and concluded Audax was about three times louder. That was
wrong: the two branches do not share a unit scale, so the same index means a different attack.

| | our `05f337d` | Audax `aa5f574` |
|---|---|---|
| attack units | P1 N2 B2 R3 Q5 | **P3 N4 B4 R6 Q10** |
| defenders | none | **subtracted**: `max(0, attack − defence)` |
| shield pawns | not counted | count as defenders inside the zone |
| ray continuation | stops at the first piece | **through friendly sliders and one enemy piece** |
| king zone, gate, factor | 3×3, `< 2 attackers`, 0.01 | *identical* |
| phase scaling | none | **none either** |

Audax's units are double ours (triple for a pawn), so its index 14 is our index 7. Compared at
the same **physical** attack the gap is far larger than three:

| attack | our units → cp | Audax units → cp | ratio |
|---|---|---|---:|
| queen alone | 5 → 10 | 10 → 150 | **15×** |
| queen + knight | 7 → 15 | 14 → 325 | **22×** |
| queen + rook | 8 → 25 | 16 → 405 | **16×** |
| queen + rook + knight | 10 → 50 | 20 → 496 | 10× |
| two minor pieces | 4 → 10 | 8 → 85 | 8.5× |

**This makes § 4.4's finding stronger, not weaker.** The −67.1 Elo came from a term that awards
an order of magnitude more evaluation for the same threat. Audax anchored the top of its curve
at **one minor piece** so that a sacrifice would look worth calculating — a style goal, and
precisely what arms the trap: at 325 cp for a queen and a knight, the term can justify giving up
a 300 cp piece *on its own*, which is exactly the case the cap in the next subsection exists to
forbid. The fitted curve of § 4.5 gives that same attack 15 cp — the difference between a hint
and an invitation.

The ray continuation compounds it, since batteries and x-rays make the term fire in positions
where ours stays silent. And § 4.2's independent figure of 34–57 cp per attacker supports the
magnitude of our scale, not the reshaped one.

(The README's feature list still mentions an "Audax perspective tilt"; that was removed long
ago and the played builds construct `new WeightingFunction()` with no root color, i.e.
symmetric.)

#### The condition this adds to step 2

> **The term's maximum contribution must stay below the material-only threshold, so that it can
> never on its own make a material investment look profitable.**

A cap under 200 cp, with margin — 150 is the suggestion. This bites on our inherited curve,
which reads 130 at 14 units but 235 at 17 and 400 at 20, so it crosses the threshold from index
17 upward, a range Audax's distribution reaches in under 10 % of games.

The number arrives from two independent directions, which is what makes it credible: § 4.2's
outcome measurement puts three attackers at 100–170 cp, and the shortcut argument says "under
200". They agree, so the condition costs nothing the data wanted anyway. Audax's 325 lies
outside both.

**Note what phase scaling does *not* fix.** F1 and § 4.2 are about the *endgame*, where the sign
inverts. This trap is a *midgame* phenomenon and is untouched by any amount of phase scaling.
A term that fixes only F1 would walk into it exactly as Audax did, just more quietly.

Two checks make the condition testable before an SPRT is spent:

1. **Static** — assert the curve's cap against `EVALUATE_MATERIAL_ONLY_THRESHOLD`, so the test
   goes red if either constant moves toward the other.
2. **Empirical** — Audax's *conviction games* metric, the share of games containing a deliberate
   material investment. The fork went from the parent's 0.3 % to 2.0 %, a factor of 6.7. If our
   sacrifice rate rises materially, the trap is armed whatever the Elo says.

#### What else transfers

- **The defense-only architecture is settled — do not repeat it.** −12.1 Elo, and its draw rate
  rose to 40.8 % against 33.4 %: measurable passivity, which is exactly what § 4.3 predicts from
  a shelter measure whose negative half carries no information.
- **The S-shape is confirmed by a second, independent method.** Audax's Texel fit of the defense
  curve gives 0 → 1 defender = 6 cp and 1 → 2 = **29 cp**; § 4.2's outcome correlation gives
  0 → 1 attacker = 20 cp and 1 → 2 = **53 cp**. Two different methods on two different features
  agree that the *second* unit carries the information. Nothing linear will do.
- **The silent fraction repeats.** Audax's defend index differs by 0 in 30.1 % of positions and
  by 1 in another 44.1 %, so the term acts through a single step in three quarters of all
  positions — the same shape as § 4.1's finding that two of four move-choice cases had zero
  differential on every measure.
- **Tooling worth borrowing:** `KingAttackDiagnostics`, `DefendUnitDiagnostics`,
  `KingDefendTexelData`, `TexelKingDefendTuner`; the **isolating bench** (three builds P/B/A so
  two changes separate cleanly, with the factors multiplying out as a built-in control); and the
  depth-from-PGN analysis together with its own control — run the same parser over a
  *fixed-depth* PGN, where every comment must read the same depth, which is how a silent ply
  parity bug was caught there.

---

### 4.5 The curve, fitted (2026-08-29) — the first table in this project not placed by hand

All three shelved attempts wrote their tables from intuition and measured the whole thing at
once, which is why none of them could say afterwards whether the idea or the numbers were
wrong. `TexelKingAttackTuner` fits the table entries directly against game results, with the
current evaluation as the base, so what is fitted is the **residual** — what king pressure adds
once everything myChess already knows has been accounted for.

The feature is linear, which is what makes this possible at all: the term enters as
`f(unitsWhite) − f(unitsBlack)`, so with the entries as parameters the derivative is a vector of
+1 and −1. Index 0 is pinned at zero, because only the difference reaches the score and adding
a constant everywhere is an exact null direction. The table stops at 8 units: indices 0–8 carry
**99.7 %** of all king samples, everything from 9 upward carries 0.3 %.

#### The dead end that came first

The obvious approach was to read the curve straight out of the data — take positions where the
*opponent* has zero attack units, group by own units, and `f` falls out with no fit at all. The
algebra is right and the answer was nonsense: **a single pawn bearing on the zone measured
+47 cp, a single queen +14**. Selecting on "the opponent has nothing" sorts positions by *which
piece* is attacking, not by how much pressure there is, so the index measured its own selection.
That failure is why the fit exists.

#### Three corpora, three calibrations

> **Superseded twice below — kept for the shape, not the values.** This table is fitted
> *phase-blind* and with the narrow step schedule. The phase weight is applied further down and
> the schedule corrected after that; the master top entry ends at **82.5**, not the +43.5 here.
> What survives is the pattern: `hybrid` negative where the others are positive.

| units | `hybrid` (engine, quiet) | master games | anchor, dense | anchor samples |
|---:|---:|---:|---:|---:|
| 1 | −22.5 | −8.5 | +12.5 | 4 325 |
| 2 | **−13.0** | +3.0 | +14.5 | 15 822 |
| 3 | **−9.0** | +2.0 | +30.5 | 10 390 |
| 4 | **−24.5** | +5.0 | +25.5 | 5 081 |
| 5 | **−5.5** | +5.5 | +23.5 | 13 883 |
| 6 | −4.0 | +20.0 | +49.5 | 3 329 |
| 7 | +16.5 | +29.0 | +73.5 | 4 641 |
| 8 | +27.5 | +43.5 | +73.5 | 3 783 |

**`hybrid` is the outlier and it is negative where it matters.** On 1 487 619 positions the
fitted entry is negative for every index from 1 to 6 — the indices that carry 39 % of all king
samples — and only turns positive at 7, which occurs in 2 %. Read literally: after the current
evaluation has had its say, more pressure on the enemy king predicts a *worse* result. The
figure is not noise; a 40 000-position trial run produced almost the same curve, at a 37× smaller
sample.

**Master games and the anchor corpus both disagree with it**, in the same direction and with the
shape the design assumes: rising, progressive at the top. The anchor — myChess's own positions
against externally rated opponents, the distribution the term must actually work in — runs about
twice the master calibration.

**Which reverses something § 4.3 claimed.** That section reported the attacker signal as robust
across corpora (−34 to −57 everywhere) against a shelter signal that was not. That held for the
*raw* outcome correlation. The **residual** after the engine's own evaluation behaves
differently, and it is the residual that a term is made of. Both halves are corpus-dependent.

#### What is trustworthy here and what is not

The two anchor columns are the same 2 000 games extracted twice — 8 positions per game and 40.
The sparse fit read +28.5 / +46 / +84.5 and saturated at three identical values from index 6, the
signature of a fit that has run out of data. The dense one resolves that and comes back at
roughly **half** the magnitude. So the sparse figures were inflated, and the dense ones are the
ones to quote.

But denser sampling raises the position count without raising **independence**: positions within
a game are correlated, so the effective sample is far smaller than 64 531 and the error bars are
narrower than they look. Two identical entries still sit at the top. The anchor supports a
*direction*; it does not place a curve.

The base error also differs too much between corpora for the error figures to be compared —
0.0705 for `hybrid`, 0.1416 for master games, 0.1551 for the anchor. Denser sampling without an
end-skip keeps more early, undecided positions, whose outcome is simply less predictable. Compare
the shapes, not the errors.

#### Re-fitted with the phase weight — and the low half turns out to be noise

The fit above pooled all phases, which repeats in the tuner the mistake `king-safety-signal.py`
made before it was split: § 4.2 measured the effect *inverting* toward the endgame, so a pooled
fit averages two opposite effects. It is also not what gets implemented — step 2 multiplies the
term by the phase — so a curve fitted phase-blind and then used phase-scaled would be neither
the fitted nor the measured thing.

Re-fitted with each sample weighted by `phase / 24`, which makes endgame positions contribute
almost nothing and yields the **midgame** table directly:

> **Still the narrow step schedule** — the top entries in this table are at the tuner's ceiling.
> Corrected immediately below; read this one for the phase effect, not for the values.

| units | `hybrid` | master | anchor | Chess960 self-play |
|---:|---:|---:|---:|---:|
| 1 | −27.5 | −16.0 | +33.5 | +16.0 |
| 2 | −14.5 | +4.0 | +8.0 | +3.5 |
| 3 | 0.0 | +7.0 | +61.5 | +8.5 |
| 4 | −27.5 | +14.5 | +59.5 | −22.5 |
| 5 | −3.5 | +4.5 | +17.0 | −5.5 |
| **6** | **+10.5** | **+46.0** | **+73.5** | **+27.0** |
| **7** | **+23.5** | **+38.0** | **+73.5** | **+35.5** |
| **8** | **+27.5** | **+51.5** | **+73.5** | **+35.5** |

**The upper half agrees across every corpus; the lower half does not.** At 6, 7 and 8 units all
four fits are clearly positive — including `hybrid`, which was negative at index 6 before the
phase weight and flips sign with it, exactly the correction F1 predicts. Below that the four
disagree in sign and magnitude with no pattern, which is what one expects of entries carrying
little information.

That is a cleaner statement than § 4.5 could make: the term appears to carry information from
about **6 units upward** — a queen and a minor, or a rook and two minors — and the indices below
that are close to noise. Indices 6–8 together are roughly 6 % of king samples.

**Chess960 broadly transfers.** The 960 self-play column tracks the master one at the top
(+27.0 / +35.5 / +35.5 against +46.0 / +38.0 / +51.5). Piece-square tables were known to
transfer to 960 and it was never checked for king safety, which is the term where one would
least expect it — the king starts on a random file with a different pawn structure in front of
it. It is the smallest corpus here (39 619 positions) so this is an indication rather than a
result.

**What this is not an argument for (corrected 2026-09-04).** An earlier version of this
paragraph closed with "it points the right way for an engine whose owner plays mostly 960",
and § 12.21 carried the same premise as a reason to re-measure the shelved king-safety terms
under `-variant fischerandom`. The owner's own games are irrelevant to it: they play well below
the engine's strength, and lichess 960 traffic is thin, so **standard-chess Elo is the target
metric and 960 is not a yardstick for shipping decisions.** The transfer observation keeps its
value in the direction it was made — a curve fitted on standard positions is not invalidated
for 960 — but a 960 measurement cannot justify shipping a term for standard chess, and where
the two regimes disagree, standard decides. A term that only pays in 960 is being tuned for a
regime nobody measures. Note the asymmetry cuts against 960 twice over for *this* family: the
king's file is variable there and natural shelter often absent, so the quantity a file- or
shield-based term reads is far more often non-zero — which makes 960 the regime where such a
term looks most valuable and standard chess the one where it must prove it.

The anchor column still saturates at +73.5 for indices 6–8. Dense sampling was assumed to be the
cause and was not: the next section shows it was the tuner's step schedule, and the anchor's true
top entry is **191**.

#### Error bars, and what they say about the corpus disagreement

24 bootstrap replicates over the master corpus, resampling **blocks of 40 consecutive
positions** rather than individual ones — positions from one game are correlated, so a
position-wise bootstrap reports intervals far too narrow. The extractor averages 24.6 positions
per game against a cap of 40, so a block of 40 is conservative by construction: too large costs
interval width, which is the safe direction.

| units | fitted | p5 | p95 | width |
|---:|---:|---:|---:|---:|
| 1 | −16.0 | −19.5 | −9.0 | 10.5 |
| 2 | +4.0 | +2.5 | +5.0 | **2.5** |
| 3 | +7.0 | +5.0 | +9.0 | 4.0 |
| 4 | +14.5 | +10.5 | +19.5 | 9.0 |
| 5 | +4.5 | +3.5 | +5.5 | **2.0** |
| 6 | +46.0 | +38.0 | +51.5 | 13.5 |
| 7 | +38.0 | +35.0 | +40.5 | 5.5 |
| 8 | +51.5 | +47.5 | +51.5 | 4.0 |

**No interval straddles zero.** The small entries are small but real, and index 1 is
significantly *negative*. An earlier reading of the four-corpus table guessed the lower half was
noise and proposed dropping it — `0 0 0 0 0 0 46 38 52`. The bootstrap does not support that.

**What it does establish is sharper.** Within one corpus the entries are resolved to 2–13 cp.
Between corpora they differ by 50 cp and more — index 1 reads −27.5, −16.0 and +33.5 across
`hybrid`, master games and the anchor. **The disagreement between corpora is an order of
magnitude larger than the sampling error inside any of them**, so it is a real difference in
what the corpora contain, not an artifact of sample size. No further scan settles which one
transfers to engine-versus-engine play. That is what the SPRT is for.

#### What the term costs, measured before it is built

On a quiet machine, 50 000 positions, best of nine, spreads 4.2 % and 6.6 %:

| | ns per position |
|---|---:|
| full evaluation | 1 099.9 |
| zone scan, both colors, standalone | **1 302.2** |

**A standalone scan costs more than the entire evaluation** — it would make evaluation 2.18×
more expensive. For comparison, the Audax fork's version, embedded in the evaluation's existing
per-piece walk, cost **1.17× per node** *including* its battery and x-ray widening, and that
alone bought it 0.68 plies less depth and turned +31.9 Elo at fixed depth into −46.5 under a
clock.

**This is not a change to make — it is one to avoid.** Branch `attack-units` already calls
`increaseAttackUnit` from inside `move(byte, int, int, int, int)`, the method the evaluation
invokes for every square a piece reaches anyway, alongside the mobility count and the capture
bookkeeping. The marginal cost there is one comparison (*is this square in the enemy king's
zone?*) and one array write for the dedup mark. No extra ray is walked and no square is visited
twice.

What the 1302 ns measures is `KingAttackUnits`, the standalone class in the test sources. It has
to walk the rays itself because master exposes no attacker set, and it exists to fit the curve,
not to ship.

The instruction, then, is for whoever does the rebase: the temptation will be to pull the unit
accumulation out of `move` into a tidy self-contained method called once per evaluation, because
that reads better. **It would roughly double evaluation cost.** For scale, the Audax fork's
1.17× per node — a fraction of that — already bought it 0.68 plies less depth and turned
+31.9 Elo at fixed depth into −46.5 under a clock.

#### The step schedule was a ceiling, and it invalidated two conclusions

> **Correction, later the same night.** Everything above was fitted with the tuner's default
> step schedule, and that schedule cannot reach large values. Coordinate descent from 0 with
> steps 4 / 2 / 1 / 0.5 and at most 12 rounds each moves a parameter by at most
> `12 × (4+2+1+0.5) = 90`. The top entry sat near that wall in every corpus.

Re-fitted with a schedule reaching ~640 (`initial step 16, 20 rounds`), the top entry climbs in
**all four** corpora:

| units | hybrid | master | anchor | Chess960 |
|---:|---:|---:|---:|---:|
| 6 | +12.5 | +48.5 | +121.5 | +31.0 |
| 7 | +25.5 | +40.0 | +116.5 | +45.0 |
| 8 | **+77.5** | **+82.5** | **+191.0** | **+84.0** |

against +27.5 / +51.5 / +73.5 / +35.5 under the narrow schedule.

**Two things this overturns.**

*The cap claim.* This document twice stated that the largest entry across all calibrations was
+73.5, comfortably under the 150 cp cap of § 4.4, and concluded the constraint costs nothing.
That was an artifact. The anchor calibration reaches **191 cp** and would need capping; the
master calibration reaches 82.5 and still does not. The cap is a real constraint again, not a
formality — which is the safer state for it to be in.

*The bootstrap.* The intervals reported for indices 6–8 — widths of 1.0 to 13.5 — were measuring
the ceiling rather than the parameter. Forty replicates all stopping at 73.5 looks like precision
and is its exact opposite. The bootstrap has been re-run with the wide schedule; the intervals
below index 6 are unaffected, since those values are nowhere near the wall.

**One thing it strengthens.** At the clamped top bucket the three larger corpora now agree
closely — **+77.5, +82.5, +84.0** — despite disagreeing on sign at the low indices. That bucket
absorbs everything from 8 units upward, so it aggregates the whole tail, which is where a king
attack genuinely pays.

Re-bootstrapped with the wide schedule, that agreement holds up as more than an eyeball match:

| units | fitted | p5 | p95 | width |
|---:|---:|---:|---:|---:|
| 1 | −14.5 | −18.0 | −7.5 | 10.5 |
| 2 | +5.0 | +3.5 | +6.0 | 2.5 |
| 3 | +8.5 | +6.5 | +10.5 | 4.0 |
| 4 | +16.0 | +12.5 | +21.0 | 8.5 |
| 5 | +6.0 | +4.5 | +7.0 | 2.5 |
| 6 | +48.5 | +40.5 | +56.5 | 16.0 |
| 7 | +40.0 | +37.0 | +42.5 | 5.5 |
| 8 | **+82.5** | **+79.0** | **+88.0** | 9.0 |

Index 8 now has a width of 9.0 rather than the 4.0 it showed at the wall — a resolved parameter
rather than a stuck one. No entry straddles zero.

**Chess960, bootstrapped separately** (30 replicates, block 8 — that corpus is not shuffled),
does not refute the transfer and cannot confirm it either. At 39 619 positions its intervals run
21–76 cp wide and indices 1, 2, 4 and 5 straddle zero. What it does say is that its own interval
at the top, **[57.0, 114.0]**, contains the master value of 82.5 comfortably. An earlier draft
put this the other way round — "960's +84.0 falls inside the master interval [79.0, 88.0]" —
which is the flattering direction and close to meaningless: with a 57 cp interval of its own, the
point estimate landing 1.5 cp from the master one is substantially luck.

**`hybrid` bootstrapped too** — 20 replicates, block 1, because that corpus is shuffled when it
is built and consecutive lines are therefore independent. Its negative entries are not weakly
determined; they are sharp:

| units | `hybrid` | master | overlap |
|---:|---|---|---|
| 1 | −31.0 [−34.0, −28.5] | −14.5 [−18.0, −7.5] | none |
| 2 | −14.0 **[−15.0, −12.0]** | +5.0 **[+3.5, +6.0]** | **none, opposite signs** |
| 4 | −28.5 **[−30.0, −25.0]** | +16.0 **[+12.5, +21.0]** | **none, opposite signs** |
| 5 | −2.5 [−3.5, −0.5] | +6.0 [+4.5, +7.0] | none, opposite signs |
| 8 | +77.5 [70.5, 79.5] | +82.5 [79.0, 88.0] | 0.5 cp |

So the uncomfortable reading is the correct one. **Two corpora state opposite things at the same
indices, and both state them with conviction** — this is a disagreement about content, not a
resolution problem.

**A claim made earlier in this section is withdrawn.** It said the corpora "converge to within
the error of a single one of them" at the top bucket. They do not: [70.5, 79.5] and [79.0, 88.0]
overlap by half a centipawn. Closer than at the low indices by a wide margin — 5 cp apart
against 20–45 with reversed signs — but not the same number.

What survives is the ordering, and it is still the useful part: **the top of the curve is where
the corpora nearly agree, the bottom is where they contradict each other.** A term built on the
top rests on the firmest ground this investigation found. One built on its bottom is a bet on
which kind of game myChess meets — engine play, where these data say attacking pressure predicts
*worse* results once the evaluation has had its say, or master play, where it predicts better.
No further scan settles that; only the SPRT does, and it will settle it for engine play, which
is where myChess competes.

#### The disagreement is not a composition artifact

One confound remained. The Texel fit applies no material filter — correctly, since the base
evaluation already accounts for material — but that lets corpus *composition* into the fit, and
§ 4.3 measured `hybrid` as structurally unlike the others: **25.1 %** of its positions are
material-balanced against 46–68 % elsewhere. In a corpus full of decided positions, "bears on the
enemy king" might simply mark the side that is behind and has to attack.

Both corpora re-fitted under an identical ±50 cp material window — 390 509 against 1 772 609
positions, everything else equal:

| units | hybrid unfiltered | hybrid ±50 | master unfiltered | master ±50 |
|---:|---:|---:|---:|---:|
| 2 | −14.0 | **−12.5** | +5.0 | **+7.5** |
| 4 | −28.5 | **−18.0** | +16.0 | **+14.5** |
| 6 | +12.5 | +11.5 | +48.5 | +49.5 |
| 8 | +77.5 | **+89.5** | +82.5 | **+82.5** |

**The opposite signs survive.** Composition explains part of the distance — hybrid's index 4
moves from −28.5 to −18.0 — and none of the sign. So the corpora genuinely disagree about what
low-level king pressure predicts, and the disagreement is about content.

**The top bucket moves the other way and closes to 7 cp** (89.5 against 82.5) under matched
conditions. Whatever separates these two corpora at the bottom of the curve does not separate
them at the top.

**But it is three corpora that agree there, not four.** Re-bootstrapped with the wide schedule,
the anchor puts the top bucket at **191.0 [151.0, 234.0]** — disjoint from master's
[79.0, 88.0] by a factor of 2.3, with no overlap at all. `hybrid` (89.5), master (82.5) and
Chess960 (84.0, interval [57, 114]) cluster; the anchor does not.

Two things follow. The recommendation is unaffected: it takes the master value, which agrees
with the two corpora nearest it in size. And the § 4.4 cap is now unambiguously live — the
anchor's *entire* interval sits above 150 cp, so a calibration on that corpus would breach it
throughout rather than marginally.

Which of them is right is not decidable from these data. The anchor is the distribution the term
must work in and by far the smallest — 2 000 games, intervals 35–120 cp wide at the other
indices, its low entries straddling zero. The three that agree are 20 to 75 times larger and
none of them is myChess's own play. That is the same trade this section has run into at every
turn, and the SPRT is where it gets settled.

#### Recommended starting curve

> **Superseded on 2026-08-30 by § 4.6.** Two of its three claims did not survive. The zeroing
> below is now known to be an artifact of the *label*, not a property of the data, at indices 3,
> 4 and 5; and the curve recommended here is not monotone — it falls from 49 to 40 between six
> and seven attack units, which would score more attackers as less danger. The reasoning is left
> standing because the argument it makes about corpus disagreement is sound and is exactly what
> § 4.6 had to work around. Use `0 0 0 13 16 47 47 47 80` (§ 4.7).

**Keep the top, zero the bottom: `0 0 0 0 0 0 49 40 83`.**

The upper three entries are the master-game calibration, phase-weighted, wide step schedule.
Three of the four corpora agree there — `hybrid` puts the top bucket at 89.5 under matched
conditions against master's 82.5, and Chess960's own interval contains both. The anchor is the
exception at 191.0 [151.0, 234.0], and it is the smallest corpus by a factor of 20.

The lower entries are set to zero **not because they are insignificant** — the bootstrap shows
each is well determined inside its own corpus — but because their *sign* is disputed between
corpora by more than their intervals allow. Shipping them means betting on which corpus
describes the games myChess will play; shipping zeros declines the bet at a cost of at most
16 cp per entry.

Two side effects, both wanted. A term that is silent below 6 units cannot fire in quiet
positions, so it cannot steer the engine into the material-shortcut trap of § 4.4 — the failure
that cost the Audax fork 67 Elo. And it makes the first SPRT a test of one claim rather than
nine: *does pressure worth 6+ attack units help?* If it measures neutral, the low entries are
the obvious second iteration, and by then there is an Elo baseline to add them to.

It is the most conservative of the two positive fits; it rests on the largest independent
sample by far — 2.37 million training positions from 120 000 games, against the anchor's 2 000
games; and the history of this theme is a history of terms that were too loud. The anchor says
there is room above it. That is the second iteration, if the first measures neutral or better —
not the first.

---

### 4.6 The label was circular — refit against Stockfish, under monotonicity (2026-08-30)

> **The curve below is the *ungated* fit and is not what ships — see § 4.7.** Everything else in
> this section stands: the circular label, the monotonicity constraint, the placebo control and
> the four checks all carry over unchanged. What it missed is that the production code gates the
> term on two attackers while the fit did not, so the two are calibrated on different
> quantities. Refitting under the gate gives `0 0 0 13 16 47 47 47 80`.

**`KING_ATTACK_PENALTY = { 0, 2, 2, 20, 20, 20, 44, 45, 85 }`.** This replaces § 4.5.

Two things forced the refit, and neither was visible while the curve was only ever compared
against other fits of the same kind.

**The Chess960 label is myChess's own play.** Every fit in § 4.5 labels a position with the
result of the game it came from, and for `mychess-selfplay-960.epd` those games are self-play.
If myChess is blind to king attacks then neither side converts one, so the fit learns "attack
units are worthless" from games that the blindness produced. The positions are fine; only the
label is circular. Replacing it with **Stockfish 18's static NNUE evaluation minus myChess's
own** — both static, no search on either side, clipped to ±2000 cp — removes the outcome
entirely. What the regression then reports per index is the evaluation myChess is missing there.

**The unconstrained fit is not monotone, and neither was the recommended curve.** Free, the
Stockfish-labelled fit gives 34.3 / 19.5 / 15.2 at three, four and five units — falling, which
would mean more attackers scoring as less danger. § 4.5's own curve has the same defect at 49 →
40. So monotonicity went into the fit rather than being patched in afterwards: minimize
`||Xb − y||²` subject to `0 ≤ b₁ ≤ … ≤ b₈`, by projected gradient descent with a
pool-adjacent-violators projection onto the isotonic cone.

| units | free | **monotone** | p5 | p95 | |
|---:|---:|---:|---:|---:|---|
| 1 | 10.9 | 2.1 | 0.0 | 4.4 | not separable from zero |
| 2 | 1.6 | 2.1 | 0.0 | 4.7 | not separable from zero |
| 3 | 34.3 | **20.3** | 16.5 | 23.9 | above zero |
| 4 | 19.5 | **20.3** | 16.5 | 23.9 | above zero |
| 5 | 15.2 | **20.3** | 16.5 | 23.9 | above zero |
| 6 | 44.2 | 43.6 | 29.2 | 51.6 | above zero |
| 7 | 45.6 | 45.4 | 37.7 | 54.4 | above zero |
| 8 | 85.5 | 85.2 | 75.1 | 95.6 | above zero |

**The constraint is nearly free, and that is the finding about shape.** The residual rises from
`6.272429e8` to `6.277248e8` — **0.077 %**. A monotone curve fits this data as well as an
unconstrained one does, so the descent from 34.3 to 15.2 was noise rather than structure. Where
the constraint binds, pool-adjacent-violators merges the offending indices into one level, which
is why 3, 4 and 5 share a value: the data say all three are well above zero and do not
distinguish between them.

**Four checks before believing it.** Indices 6–8 come out at 44 / 45 / 85 against § 4.5's
49 / 40 / 83 — two estimators with different labels, agreeing at the top, which is what makes
the disagreement below credible rather than a shifted estimator. A **placebo zone**, same rank
as the enemy king and four files away, sits at −8.6 [−12.3, −3.4] at five units over the same
positions, so the signal is about the king and not about having active pieces deep in enemy
territory. Three bootstrap seeds give the same rounded curve with intervals stable to about
1 cp. And a cold-started solve is bit-identical to the warm-started one, so the point estimate
did not seed its own bootstrap.

**What it changes in practice.** § 4.5 zeroed indices 1–5, and indices 3–5 carry **24.2 %** of
all king samples against 6.2 % for 6–8 — so the shipped curve would have been silent in roughly
four fifths of the positions where it had anything to say. An SPRT on it could not have
separated "the term does not work" from "the term was switched off where it mattered". Indices
1 and 2 keep their zeros in substance: 2.1 cp with a lower bound on zero is not evidence, and a
single minor piece bearing on the king zone is the normal case rather than a danger.

**What it does not change.** § 4.5's cap argument stands untouched — the curve tops out at 85
against the 100 cp limit `KingAttackCurveTest` enforces
(`EVALUATE_MATERIAL_ONLY_THRESHOLD / 2`), so it still cannot pay for a piece sacrifice on its
own. Stockfish's NNUE is itself trained on games and is not ground truth; it is an independent
and far stronger yardstick that myChess's own play cannot have moved, which is a weaker and
sufficient claim. And this is still not the repair: 20 cp stands against the 475 cp gap in
`BlunderTest.staticEval960_afterDxc6`, the position that prompted the whole measurement.

**`MAX_UNITS = 8` stays, and now for a better reason than frequency.** The cap was justified by
indices 0–8 covering 99.7 % of king samples, which is an argument about how often the table is
read and not about whether the clamp costs anything. The right question is whether the term
still *distinguishes between the moves of one position* — a penalty that takes the same value
after every legal move cancels in the search however large it is. Measured over 300 positions
carrying at least three attack units, the spread of the term across their legal moves has a
median of **18.3 cp**, p90 of 63.8, and is effectively constant in **3.3 %** of them. Only
0.72 % of samples exceed eight units at all (highest observed: 14), and 33 positions of 39 619
carry eight or more on both sides. Extending the table would buy resolution in a corner the fit
cannot populate anyway. Numbers in
[`king-attack-move-discrimination.log`](../test-results/king-attack-move-discrimination.log).

**A second reading of the same cap, from live games — preliminary.** The frequencies above count
*samples*: index 8 is 1.9 % of them, which reads as "the top entry is rarely asked for". Per
*game* it is the opposite. `MatchStyleAnalysis` over the first 90 games of the v4.6.0-attack-units
match reports the peak index a side reaches in a game — counting only plies where the gate lets
the term speak — and the **median is 8** for the candidate against 7 for the baseline, with both
maxing out at 8. A game is ~120 plies, so a maximum over it naturally sits high and the two
statistics do not conflict. What it means in practice is that the entry actually steering games is
mostly the clamped 80 at the end of the curve — the one whose interval `[70.9, 90.9]` is the
widest of the table.

That does not reopen `MAX_UNITS`: the question a penalty has to answer is the comparison
*between* the moves of one position, and there the term still varies by a median of 18.3 cp.
It does mean the top entry deserves the most attention at the next re-fit, and that the
candidate/baseline gap of 8 against 7 is the term doing what it is for — seeking the pressure the
baseline only stumbles into. **Read as provisional**: it comes from 90 games of a match that was
still running when this was written.

**One thing no calibration of this curve repairs.** From lichess
[SINwv7q4](https://lichess.org/SINwv7q4), myChess as white, `13.Qxd7` turns −1.47 into −5.22
(Stockfish 18, depth 22; `13.f3` holds at −1.39). Black holds ten attack units on the white king and white none, so this looks
like the family's own territory — and applying the curve to all 36 legal moves leaves `Qxd7`
ranked **1 of 36**, exactly where the static evaluation already had it and where Stockfish puts
it 15th at depth 18. The term is not silent here; it points the wrong way. `Qxd7` draws the *best* term of
any move, because the queen on d7 bears along the seventh rank onto g7 and therefore counts as
five attack units against the *black* king. "Piece bears on the king zone ⇒ attacker" does not
ask whether that piece is needed elsewhere, and here it is precisely the defender white is
sending away. Worth knowing before the SPRT: a neutral result will contain cases of this shape,
and they are not evidence that the calibration is wrong.

### 4.7 The gate was in the code but not in the fit (2026-08-30)

**`KING_ATTACK_PENALTY = { 0, 0, 0, 13, 16, 47, 47, 47, 80 }`.** This is what ships.

§ 4.6 fitted the curve over `KingAttackUnits`, which sums attack units unconditionally.
The production term does not: it scores zero unless at least two distinct pieces bear on the
zone. So the table and the quantity indexing it were calibrated on different things, and the
error is not small — **the gate suppresses 41.4 % of the term's total mass.**

| attackers | share of king samples | |
|---:|---:|---|
| 0 | 49.9 % | term is zero anyway |
| **1** | **33.5 %** | **suppressed by the gate** |
| ≥ 2 | 16.6 % | term applies |

Of the single-attacker samples, 21.1 % carry three units (a lone rook) and 23.8 % carry five (a
lone queen) — 20 cp each under § 4.6's curve, zero behind the gate.

**Refitting with the gate applied changes the middle of the curve, not its ends.**

| units | § 4.6, ungated | **gated** | |
|---:|---:|---:|---|
| 1–2 | 2 | **0** | suppressed by the gate regardless |
| 3 | 20 | **13** | [0.0, 21.1] — touches zero |
| 4 | 20 | **16** | [8.1, 26.0] |
| 5 | 20 | **47** | [40.3, 53.2] |
| 6–7 | 44 / 45 | **47** | [40.3, 53.3] |
| 8 | 85 | **80** | [70.9, 90.9] |

**Index 5 more than doubles, and that is the argument for keeping the gate.** Ungated, that
index mixes a lone queen with a rook and a knight; the two are not the same thing, and pooling
them halves the estimate. With the gate the index means one thing — at least two pieces,
together weighing five — and is worth 47 cp.

On fit quality alone the two are a tie: residual `6.275496e8` gated against `6.277248e8`
ungated, `6.358003e8` for no term at all. The gated model is better by 0.028 %, which decides
nothing. What decides it is coherence of the index, and that the gate is already in the code:
shipping the ungated curve would have meant reading a table calibrated for a quantity the
engine does not compute.

**This reverses step 4 of the build plan**, which said to replace the gate because "it asks
whether enough pieces are present, not whether the position is dangerous". That was a design
intuition, and the measurement contradicts it.

**One entry stays disputable, and the shipped value is not quite the optimum.** Index 3 fits at
13.1 with a lower bound of exactly 0.0 — the constraint set has zero on its boundary, so by the
rule this document uses elsewhere (only `p5 > 0` is evidence) that entry is not separated from
zero. It ships at **13**, which is what the first solve returned.

Re-running the same objective through the ported
[`tools/king-attack-vs-stockfish.py`](../tools/king-attack-vs-stockfish.py) gives **11** there,
with a *lower* residual — `6.275280e8` against `6.275496e8` — so the tool converges further than
the run that produced the shipped table. Every other entry is identical, and the ungated variant
reproduces to every digit. Both values sit inside index 3's own interval `[0.0, 21.1]`.

That is 2 cp in a bucket carrying 9.2 % of samples, well below anything an SPRT resolves, so the
running match was not restarted for it. Recorded because the shipped number is not exactly the
minimum of the objective this section states, and finding that out twice would be worse than
writing it down once. Fold it in at the next re-fit.

**And a number worth carrying into the SPRT.** Against "no term at all", the fitted term
explains **1.3 %** of the residual variance between myChess's evaluation and Stockfish's. That
is the size of what is being added.

Numbers in
[`king-attack-isotonic-960.log`](../test-results/king-attack-isotonic-960.log) (the ungated fit
and the solver checks), [`king-attack-gate-refit.log`](../test-results/king-attack-gate-refit.log)
(the gate) and
[`king-attack-move-discrimination.log`](../test-results/king-attack-move-discrimination.log).

---

**Reproducing it.** Numbers in
[`test-results/king-attack-isotonic-960.log`](../test-results/king-attack-isotonic-960.log) and
[`king-attack-vs-stockfish-960.log`](../test-results/king-attack-vs-stockfish-960.log). The
driver is `tools/king-attack-vs-stockfish.py`, ported 2026-08-30 **onto branch
`attack-units`, where it has to stay** — with `KingAttackProbe` and the `ofZone` /
`placeboCenter` / `attackersOf` additions to `KingAttackUnits` behind it. `KingAttackUnits` reads
the six unit weights from `WeightingFunction` rather than repeating them, which is the right
trade (the two cannot drift apart) and has the consequence that it no longer compiles on master:
master's evaluation has no attack-unit constants at all. Reviving the fit means reviving the
branch, or restoring local constants in that one class. **Read its warning before re-running it:** the target is
`stockfish − myChess`, so fitting against a build that already applies `KING_ATTACK_PENALTY`
measures the residual *after* the term. On branch `attack-units` the same corpus that gives
1.30 % explained variance against master's evaluation gives **0.000 %** and an all-zero gated
curve — the right answer to a different question, and indistinguishable from "no signal".

### 4.8 What the match said (2026-08-31)

**−42.9 ± 33.9 Elo, SPRT H0 accepted after 304 games**, LOS 0.6 %, score 0.439 (92–129–80). The
interval runs −76.8 to −9.0 and excludes zero. Full numbers in
[`sprt-attack-units-analysis.log`](../test-results/sprt-attack-units-analysis.log); the roadmap
entry [§ 12.21](roadmap.md#1221-king-safety--m--3060-elo) carries the same result in its attempt
log.

**The cap worked, and that is worth separating from the loss.** § 4.4 argued the term's real
danger was steering into sacrifices the material-only shortcut then hides — the combination that
cost the Audax fork 67 Elo — and set the cap against it. The match says that is not what
happened. The style analysis finds the candidate in a "down 300 cp and still confident" state in
**6 of 301 games** against the baseline's 1, all seven drawn and none won, with the deepest
deficit held at 800 cp against 400. Real, and in the predicted direction; far too rare to explain
a win rate of 30.6 % against 42.9 %.

**So the loss is diffuse, which is the failure this document expected and the one it could not
design around.** More checks per game (4.9 against 4.0) and a peak attack index a full step
higher (median 8 against 7) say the term does change the play in the intended direction. Its own
mean score fell from +10 cp to −5: it rates its positions slightly worse and plays them a great
deal worse. That is static noise added to a search which already resolves king attacks
tactically, measured for the fourth time.

**What did not get tested.** The three boundary cases recorded in § 4.7 and in `BlunderTest` are
untouched by this result, by construction — a term that measures −42.9 has still not been tried
against a position where it scores zero (`castling960_atMove5`), where it contributes 20 cp
against a 475 cp gap (`staticEval960_afterDxc6`), or where it ranks the blunder first of 36
(`qxd7_vsStudylovers`). They were written down before the match for exactly this moment.

**And the yardstick, on the terms agreed in advance.** § 12.21 committed to treating a *neutral*
standard result as inconclusive and running Chess960 before shelving anything. This result is not
neutral, so by that rule standard chess is settled without the second run. Chess960 is a separate
question and the whole apparatus survives to answer it: the curve, the fitted tooling in
[`tools/king-attack-vs-stockfish.py`](../tools/king-attack-vs-stockfish.py), and a prepared
invocation. Whether it is worth 17 more hours of machine time is a judgement about expected
value, not about this measurement.

**A second, independent reason to expect this result: the term is not nearly free.** NPS falls
**21.9 %** and the wall clock to depth 8 rises **31.5 %** on the 53 realistic bench positions,
while the tree is unchanged within 3 %. Under a clock that is depth given away, on top of an
evaluation term that is independently wrong. § 4.9 has the measurement, the reason § 4.5 predicted
otherwise, and a repair. (The total signature drops 65 %, which is one artificial position and
means nothing.)

**Do not quote −42.9 as "king safety does not work."** It is one calibration of one static term
on one variant.

---

### 4.9 The term is not nearly free — and § 4.5 got that wrong for a nameable reason

**−21.9 % NPS, +31.5 % wall clock to depth 8**, measured on the 53 realistic bench positions
against the same baseline re-measured on the same machine. Nodes are unchanged within 3 %, so
this is not the search exploring more — it is each node costing more. Full table in
[`bench-history.md`](bench-history.md#measured-but-not-a-release--460-attack-units-2026-08-31).

**§ 4.5 predicted the opposite, and the mistake is instructive.** It measured a *standalone* zone
scan at +118 % of an evaluation, concluded that a separate pass was unaffordable, and inferred
that hanging the accumulation on the existing per-piece walk would make it "nearly free". The
first half is right and is why the build plan forbids refactoring it into a second pass. The
second half compared the wrong two things: it costed the *scan* and assumed integration meant
zero *setup*. The setup is where the cost is.

Per call to `WeightingFunction.calculate`, the term adds:

| | per evaluation |
|---|---|
| `Arrays.fill(isKingZoneField[0], false)` | 144 boolean writes |
| `Arrays.fill(isKingZoneField[1], false)` | 144 boolean writes |
| `Arrays.fill(isKingAttackerCounted, false)` | 144 boolean writes |
| extra scan a1…h8 to locate the two kings | 92 reads, 184 comparisons |

That is **432 writes and a second board traversal before the evaluation's own work begins**, at
every leaf. The term's actual logic — `increaseAttackUnit`, two array accesses inside a loop that
runs anyway — is as cheap as § 4.5 assumed. What was never counted is the bookkeeping that makes
the mask usable.

**The repair is small and does not touch the accumulation.** Three changes, none of them the
forbidden second pass — and note that (1) and (2) together delete both helper arrays rather than
making them cheaper:

1. **Drop the zone mask.** Membership in a 3×3 zone is arithmetic on the 12-wide board: with
   `a = |toField − kingField|`, the square is in the zone exactly when `a ≤ 1 || (11 ≤ a ≤ 13)`.
   A two-file gap on one rank gives 2 and is excluded; one rank and two files gives 10 or 14, also
   excluded; two ranks give 23–25. `a == 0` is the king's own square, which belongs to the zone.
   That removes 288 writes per evaluation and both `Arrays.fill` calls, at the price of an
   absolute value and three comparisons per candidate square.
2. **Drop the dedup array too — the scan already serializes it.** The outer loop of
   `calculate` runs over *squares*, and for each square all of that piece's moves are generated
   inside it (`for (int to = field + 1; move(myPiece, field, to, color); to++)` and its siblings),
   with `field` passed as every call's `fromField`. So all reports for one piece arrive
   consecutively, and the guard only has to remember whether the piece **currently being
   scanned** was already counted — one `int lastCountedAttacker`, compared against `fromField`.
   No array, no clearing, one comparison. Together with (1) that removes all 432 writes per
   evaluation and both helper arrays.

   *This paragraph first proposed a generation stamp* — an `int[]` compared against a counter
   incremented once per evaluation, so stale entries fail the comparison and no clearing is
   needed. That works and is the general technique when a guard genuinely needs random access,
   but it is unnecessary here and carries a trap worth naming: the counter overflows. At ~1.4 M
   evaluations per second, 2³¹ is reached after some 25 minutes of continuous search, so a
   correct implementation needs a wrap guard that clears once — cheap when amortised, and a bug
   that surfaces only after half an hour if forgotten. The simpler form above has no counter to
   overflow.

   **What it does have is a hidden coupling, and that is why it needs a test rather than a
   comment.** It holds only while the scan reports each piece contiguously. Moving the
   accumulation into a second pass, or reordering the loops, would make the guard silently
   undercount where an array simply keeps working. § 5 forbids the second pass already, but a
   prohibition in a document is not a test.
   `WeightingFunctionAttackUnitTest.countsASingleThreateningPieceOnce` — a knight bearing on two
   zone squares, asserted as one attacker — is the case that would catch it, and it exists.
3. **Stop searching for the kings.** The two king squares are the only thing the arithmetic test
   needs. If `Board` carried them — it currently rescans in `isFieldAttackedBy` as well — the
   extra traversal disappears for this term and for that method at once. Otherwise keep the loop:
   it is the cheap part.

**Why this matters beyond tidiness.** § 4.4 caps the curve at 100 cp, and that cap is not a
choice — it is forced by `EVALUATE_MATERIAL_ONLY_THRESHOLD`. So the term's maximum contribution is
bounded at 100 cp while its cost is a fifth of the node rate. **Any variant of this term has to
buy more than a third of a ply's worth of Elo with at most 100 cp of evaluation**, and the shipped
calibration tops out at 80. That exchange rate, not the −42.9 of one match, is the argument that
the approach is finished.

**Re-measure before drawing conclusions from a further match.** A 960 run against the term as it
stands would measure the handicap as much as the idea.

### 4.10 The repairs, measured: the cost *was* the failure

The § 4.9 repairs went in — arithmetic zone test instead of 432 boolean writes per evaluation, a
single-`int` guard instead of the second board traversal — and the identical curve was re-measured
against 4.6.0 over the full budget.

| | attempt four | attempt five |
|---|---:|---:|
| evaluation | the § 4.7 curve | **identical** |
| implementation | § 4.8 | § 4.9 repairs applied |
| result | −42.9 ± 33.9, H0 at 304 games | **+9.1 ± 14.5**, 1600 games, no bound |
| score | 0.439 (92–129–80) | 0.513 (602–560–438) |
| LOS | 0.6 % | 89.1 % |

**Two readings in § 4.8 do not survive this**, and both were written with more confidence than the
evidence carried:

*The diffuse-evaluation-error conclusion is refuted.* § 4.8 ruled out the § 4.4 trap, found the
damage spread across the whole match rather than concentrated in sacrifices, and concluded "static
noise added to a search that already resolves king attacks tactically". The same static noise, given
its plies back, measures positive. The general failure mode is real and this document records it
three times over; attempt four was not an instance of it.

*The conviction-game row was never evidence.* 6 games against 1 became **7 against 6** on the second
run of the same baseline binary. Two counts near zero differ by chance, and a 2.0 %-against-0.3 %
line reads like a finding. It is retained in § 4.8 only so the mistake stays visible.

A caveat is owed in the other direction too: −42.9 came from an SPRT stopped at 304 games, so
[roadmap § 12.23](roadmap.md)'s winner's-curse correction applies. The true cost was less extreme,
and 52 Elo is therefore an **upper bound** on what the repairs bought.

**Why it is still shelved.** +9.1 with an interval from −5.4 to +23.6 is not "clearly positive",
which was the rule agreed before the run. Resolving +9 as distinct from zero wants the interval
below 9 — about 4 150 games, 47 hours — and that number expires the moment a different king-safety
term ships. The honest record is "not shown to be worth ≥ 15 Elo at 1600 games".

---

### 4.11 File danger — the candidate that outranks everything measured here

The lesson of §§ 4.1–4.10 is not about attack units, it is about **cost of discovery**: four
attempts, a fitted curve, an NPS regression and two matches over three weeks to learn what one term
is worth. That is now an hour's work before a line of production code exists. Regress
`Stockfish static NNUE − myChess static` on a candidate over the 39,619-position 960 self-play
corpus and read how much of the gap it accounts for
([`tools/king-safety-screen.py`](../tools/king-safety-screen.py); full record in
[`test-results/king-safety-feature-screen.log`](../test-results/king-safety-feature-screen.log)).

| candidate | top cp | explained | control |
|---|---:|---:|---:|
| **file danger** | 177 | **2.238 %** | **0.000 %** |
| attack units (§§ 4.5–4.10) | 85 | 1.270 % | 0.061 % |
| virtual queen mobility | 245 | 0.813 % | 0.000 % |
| enemy pawn storm, dense | 29 | 0.282 % | — |

**The feature.** Each of the three files at and beside the king is classified on an ordered scale,
and the three are summed (raw 0–12):

| the file | level |
|---|---:|
| own pawn on it | 0 |
| half-open, enemy pawn still on its own half | 1 |
| half-open, enemy pawn past the middle | 2 |
| open | 3 |
| open, with an enemy rook or queen on it | 4 |

**The table**, fitted with twelve free parameters so the top is not a blend of five levels:

```
danger:  0    1    2    3    4    5    6    7    8    9   10   11   12
cp:      0   21   42   42   77   91   95  134  138  223  223  223  223
```

Equal neighbors are the fit saying it cannot separate those levels. Indices 10–12 carry index 9's
value deliberately: their own fitted values rest on 0.56 % of samples between them, and index 12's
bootstrap interval collapses onto its point estimate — the same signature that made the first
pawn-storm encoding's 141.5 cp worthless.

**Why the control matters more than the coefficient.** Open files near a king also mean the position
is open, and a regression cannot separate "this king is exposed" from "there is little left on the
board". The identical classification read from a three-file window four files away returns
**0.000 % explained, flat zero at every index**, at a *higher* occupancy than the real window
(56.8 % against 47.7 %). Without that number the 2.238 % would be unusable. It was very nearly not
measured — the mobility control had already returned zero and the first draft of the log carried
"that control carries over" as an inference. A control that transfers by resemblance is not a
control.

**Two relationships, both measured** (`tools/king-safety-orthogonalize.py`):

- **File danger subsumes virtual queen mobility.** Mobility keeps 24 % of its solo figure once file
  danger is fitted first. The two must never be added — and the § 4-era recommendation of mobility
  as the best next candidate is superseded.
- **File danger and attack units are complementary.** 63 % and 78 % survive the other, so this
  document's term is outranked rather than replaced, and a later combined term would not
  double-count.

**Built as `4.6.0-king-line`, commit `4306161` on master (2026-09-02).** The notes below are what
the build followed; each is now history rather than instruction.

- Built on **master**, not on `attack-units`, because the table is calibrated against master's
  evaluation and on the branch the attack-unit term already fills part of the gap.
- **Wired as the ninth tunable factor** — `TUNABLE_FACTOR_NAMES`, `tunableFactorValues()`, and the
  `features[]` entry `(penalty[0] - penalty[1]) * 100.0`. Correctness, not tuning: `analyzeFactors`
  asserts the evaluation is linear in the factors and `FactorTexelData` computes
  `baseEval = eval − dot(features, factors)`, so an unlisted term folds into the supposedly constant
  base and every later tuning run tunes the other factors against something that moves with king
  danger. Branch `attack-units` is the worked example of the failure — it adds its term to
  `calculatePositionWeight` and not to `analyzeFactors`, so
  `FactorTexelDataTest.breakdownReconstructsTheRealEvaluation` fails there (209.0 against 190.9)
  and passes on master.
- **The material-only shortcut was left alone**, so the term is silent in sacrificial lines. A
  `Statistics.getMaterialOnlyLeafCount()` counter was added alongside the term to make that reach
  observable; it lives on branch `king-line-tuned` with the rest and is **not on the mainline**. The blind spot stays because repairing it is what measured badly: removing the shortcut
  −34 Elo, threshold 300 −18.3, threshold 100 flat at −0.7.
- **One deliberate divergence from the fitted definition.** An enemy rook or queen met *before* the
  own shield pawn scores level 4, where the fit stopped at the nearest own pawn and never looked for
  majors — scoring the same file 0. The implementation's reading is the better one and the
  divergence is below any resolvable level: 0.0697 % of king files in the calibration corpus, 163 of
  233,799, in 161 of 39,619 positions. Same order as the overtaken-shield-pawn case (0.073 %) that
  prompted the ordered shelter rule.

**Cost, measured rather than predicted.** −5.55 % NPS on a bit-identical tree, and the tree at
depth 8 shrinks 56 % (572,148,460 nodes against 1,300,002,835). Neutrality of the new code paths is
proven, not argued: with the factor at 0 the signature comes back bit-identical to 4.6.0's. Details
in [bench-history](bench-history.md); the contrast is § 4.9's −21.9 % on an *unchanged* tree.

**What the characterization suites said before the SPRT.** `BlunderTest` 13 of 63 and
`StsDefectTest` 6 of 19 turned red, which for a suite of pinned *bad* play is where the evidence
lives:

- **Five now play exactly the move their own text names as the one that holds** — `Rxf8` in
  `rd3_atMove35`, `Be1` in `rxa5`, `Nb4` in `nxd4`, `Qe5+` in `qxb7`, and the `h4!` undermining
  thrust in `undermine098`. The first two and the last are king-safety or kingside cases, so the
  term fired in its own domain.
- **Two stop opening the file at their own king** without finding the named move (`f3_atMove33`
  plays Rd2 where Stockfish wants Rd1; `h3_atMove20` plays Rf4 where the test names Rd2).
- **Nine are changes of unverified merit** — a different move, neither the pinned blunder nor the
  named rescue.
- **Three are regressions of previously-fixed cases, and two of those are king-safety**:
  `h3_atMove12_engineNoLongerPushesTheUndefendedPawn` pushes h3 again, and
  `nxe2_atMove19_engineMissesTheExchangeWinningSacrifice` misses the sacrifice again.

That last line is the one to keep in view. The term demonstrably repairs positions of its own kind
and breaks two others of the same kind. Neither set decides anything — 22 positions cannot — but a
term whose regressions land in its own domain is not the clean picture the screen suggested.

**And the standing caveat, unchanged.** A flat screen result is a reliable stop signal; a strong one
is not a promise. Attack units screened at 1.270 % and produced §§ 4.8 and 4.10.

**SPRT: H0 accepted after 437 games** (2026-09-03): **147–183–104, score 0.459,
−28.9 ± 28.6 Elo, LOS 2.4 %**, llr −2.97 against the −2.94 bound, 5 h 03 at `tc=40/60`,
concurrency 4. Clean match health. Early-stopped, so the true cost is less extreme than −28.9
(§ 12.23).

**Shelved**, by the rule agreed before the run: ship only if clearly positive.

The uncomfortable part is how good the prerequisites were. Screened at 2.238 % with a control at
exactly 0.000 %, cost −5.55 % NPS against § 4.9's −21.9 %, behavioural neutrality proven by a
bit-identical bench signature, five characterizations flipping to the exact rescuing move their own
text names. Every gate this document put up before the attempt, this attempt passed — and it lost
29 Elo anyway. That is the strongest evidence in this file for the failure mode it has been naming
since § 1: **a static evaluation term added to a search that already resolves king attacks
tactically tends to cost more than it carries**, and no amount of pre-measurement of the *signal*
predicts that.

The one variant with a mechanism rather than a hope: the table reaches 223 cp from index 9 up, on
2.4 % of samples. A static term louder than a pawn steers alpha-beta hard, which fits the depth-8
tree shrinking 56 %. Capping it far lower is testable; anything else here is out of ideas.

---

---

### 4.12 Attempt seven — the table re-fitted against game results

**The capping idea above was measured and is not the explanation.** A one-dimensional sweep of
`kingLinePenaltyFactor` over two corpora and ~1.34 M labeled positions put the optimum at
**−0.008** against the shipped **−0.010** — a 0.07 % difference in mean squared error, on a basin
flat from −0.005 to −0.012 (`test-results/king-line-factor-sweep.log`). For scale, moving from
*no term at all* to the optimum is eight times the improvement of moving from −0.010 to −0.008. The
term was near-optimally scaled and still lost 29 Elo, so the error was never the overall volume.

**But the term does do what it was built to do**, which had never been checked.
`KingShelterAnalysis` replays all 434 SPRT games and counts, per engine, pawn moves on the three
files at or beside its own king after which that king's danger rose — an action, not the
evaluation's own quantity:

| | king-line | base |
|---|---:|---:|
| shelter-opening pawn moves per 100 own moves | **1.12** | 1.38 |
| any move raising own danger, per 100 | **5.94** | 7.45 |
| own moves with danger ≥ 4 | 22.3 % | 40.5 % |
| mean own king danger, 0–12 | 1.85 | 3.23 |

Only the first row is evidence; the rest is the quantity the search minimises and falls by
construction. So myChess opens its own king's shelter **about a fifth less often** with the term.
That rules out the kinder of the two diagnoses: the term did not fail by doing nothing.

**What was wrong is where the numbers came from.** The table was fitted for agreement with
Stockfish's *static* evaluation. Re-fitted against game results — the objective that decides
matches — the shape changes substantially and the proxy gain rises by half, from 0.00036 to
**0.00053** mean squared error against having no term at all:

```
index      1   2   3   4   5   6   7   8    9   10   11   12
vs SF     21  42  42  77  91  95 134 138  223  223  223  223
vs games  26  32  66  68  73  91  91  91  125  153  153  153
```

The top halves; the middle rises. Indices 6–8 are pooled at 91 by pool-adjacent-violators over the
occupancy weights, because the free fit dips at 7 and 8 under *every* scaling — a question the
first fit could not raise, having imposed monotonicity as a constraint. Indices 11–12 carry index
10's value: 0.02 % and 0.00 % occupancy leave them unconstrained.

**The scaling question, and why it cannot be answered here.** Scaling by the *opponent's* heavy
material rather than by the game phase is the better model — the standard phase counts both sides'
non-pawn material, so a side with a queen and two rooks facing an opponent with none is still
penalised at full strength for files nobody can use. Tuned over the whole corpus the three
scalings sit within 4 % of each other. Bucketed by how far they diverge
(`test-results/king-line-scaling-subset.log`) the reason appears:

| divergence | share | MSE with no term | gain: phase / opp-heavy / opp-non-pawn |
|---|---:|---:|---|
| 0.00–0.05 | 23.7 % | 0.0871 | 0.000388 / **0.000401** / 0.000307 |
| 0.05–0.15 | 33.1 % | 0.0834 | **0.000696** / 0.000631 / 0.000676 |
| 0.15–0.30 | 33.0 % | 0.0632 | 0.000616 / 0.000642 / **0.000651** |
| 0.30–0.50 | 8.6 % | 0.0165 | **0.000196** / 0.000168 / 0.000174 |
| 0.50–1.01 | 1.7 % | 0.0020 | 0 / 0 / 0 |

Read the third column. Where the scalings diverge most, one side is up a queen or more, the
sigmoid is saturated, and no evaluation change moves the prediction at all — in the top bucket
every column agrees to eight decimals. **The objective is blind exactly where the modelling error
lives**, and where it does resolve, the three sit 1–3 % apart with no consistent winner. The phase
scaling therefore stays, not because it is right but because nothing measurable argues for
changing it. The argument for opponent material is recorded, unpriced.

**Expectation, stated before the match, and it is not good.** Only 4 of the 27 evaluation windows
in the test suite had to move for the new table, so in ordinary positions the evaluation barely
changes. The proxy gain rose by half — and the old table also had a positive proxy value and lost
29 Elo.

Then the bench came back and made it worse. The re-fitted table **grows** the depth-8 tree by
18.2 % and needs 27.6 % more wall clock than having no term at all, where the old table *shrank*
the tree by 56 % ([bench-history](bench-history.md)). The loud old table cut whole variations; the
quiet new one merely reorders moves, and worse than no term does. **That is the axis attempt four
died on** — the attack-unit term lost 42.9 Elo to −21.9 % NPS — and this candidate stands worse on
it than the table that already lost 29 Elo.

`BlunderTest` says the same thing more concretely: 8 red instead of 13, with **the same two
king-safety regressions unrepaired** (`h3_atMove12` pushes the pawn in front of its own king again,
`nxe2_atMove19` misses the sacrifice again) and two of the old table's repairs lost — `rxa5` no
longer finds `Be1`, `rd3_atMove35` no longer finds `Rxf8`, both cases of high line danger that the
halved top no longer punishes. One new repair appears in `StsDefectTest`
(`centerControl071`, the `h3` push that allowed the `f3` break), which is the classic shelter case.

So: better numbers on the objective that decides matches, worse numbers on the cost axis that
killed the last attempt, and a behavioural picture that is different rather than better. The match
is worth running because these pull in opposite directions and nothing short of a match resolves
that — not because the odds look good.

---

### 4.13 What the match said, and the defect it exposed (2026-09-03/04)

**Stopped at 2255 of 6000 games: 821–815–619, score 0.501, +0.8 ± 12.3 Elo.** Stopped rather than
finished, because the scan carries a defect (below) that makes the figure the term *minus a
discount* rather than the term. Artifacts kept at
[`match-king-line-tuned.pgn`](../test-results/match-king-line-tuned.pgn) and its stdout log: the
analyses below stand independently of the Elo number, and one of them is how the defect surfaced.

**Two of § 4.12's expectations were wrong, and in opposite directions.**

*The cost prediction was six times too pessimistic.* § 4.12 read the bench's +18.2 % tree and
+27.6 % wall clock as "worse on the cost axis that killed the last attempt". Under a clock the
paired depth difference is **−0.0411 plies** (95 % −0.051 to −0.031, t = −7.96 over 1098 rounds).
At an effective branching factor near 2.7, +27.6 % per node predicts roughly 0.25 plies; it costs
0.04, and there were no time forfeits in 2255 games. So the bench's fixed-depth cost is a poor
predictor of what iterative deepening actually gives up — worth remembering the next time a bench
delta is read as a verdict.

*The behavior change is much larger than the shelter count suggested.* § 4.12's action metric put
it at "about a fifth less often". Measured on the quantity the term is indexed by — the share of
the three king files carrying no own pawn — over 260 066 positions per side:

| | uncovered king files |
|---|---:|
| `king-line-tuned` | **24.19 %** |
| `4.6.0` | 36.97 % |

**−34.6 % relative**, paired per round −12.82 pp ± 0.43 (SE), t = −30.2. Strongest in moves 16–50,
where the phase scaling gives the term weight.

**And a third finding, which is the sharpest thing in this document.** Exposure *is* punished in
this environment — measured on the **baseline**, which has no term at all and is therefore
untouched by the defect. In games balanced at move 20 (`|eval| ≤ 0.5`), bucketing that side's own
exposure over moves 18–28:

| exposure | n | score |
|---|---:|---:|
| low (0.000) | 336 | **0.5699** |
| middle | 336 | 0.4896 |
| high (0.333–1.000) | 337 | **0.4688** |

A gap of 0.101, about **70 Elo**, monotone. So the correlated-blind-spot objection — "self-play
does not punish what the term defends against" — does not hold here. Yet for the *candidate* the
two-thirds of games with zero exposure scored **0.491** against **0.490** for the rest: the same
categories that separate 70 Elo for the baseline separate nothing for it. Read together, the most
consistent reading is that **exposure is largely a symptom of a worse position rather than its
cause** — the correlation is real, the term removes it, and the result does not move. That reading
is interpretation; the three tables are measurement. It bears on the whole family, because a term
that suppresses a symptom cannot buy the Elo the symptom is correlated with.

Two further notes from the same corpus. The *pawn-move* proxy points the other way — the candidate
plays **12.7 % more** home-rank pawn moves beside its own king — which is coherent once the term is
read precisely: it prices file *vacancy*, and `h2-h3` does not leave the h-file. "Pawn moves near
the king" is the wrong proxy for it. And a paired analysis over the 1097 complete color pairs
reproduces cutechess almost exactly (score 0.50091 ± 0.01730, ≈ +0.6 Elo) at an interval no
narrower than the unpaired one — so opening variance is not the dominant noise source in this
setup and pairing buys nothing here.

#### The defect: a corner king was counted on two files

The window was the king's file plus both neighbors, unconditionally. For a king on the a- or h-file
one neighbor is off the board, and `calculateKingLineDanger` returned 0 for such a square. The
danger index therefore had a **ceiling of 8 at the edge against 12 everywhere else**, and the same
real exposure mapped to a lower penalty in the corner — a standing discount for standing on the
edge. It never threw, never produced a wrong-looking number, and the fitted table absorbed it. The
production comment named the behavior and treated it as harmless.

The match shows it was not. The candidate's king sat on the **h-file in 10.28 %** of positions
against 5.66 % for the baseline and on the g-file 48.36 % against 42.43 %, and by move 51 it stood
**0.30 squares further from the center** on average — against a king endgame table worth +7.7 Elo
for centralizing it (v4.3.1). The user spotted the corner drift from the analysis and asked whether
the scan was to blame before the mechanism was found; it was.

An obvious follow-up hypothesis did **not** confirm: candidate score by game-length tercile is
0.511 ± 0.031 / 0.483 ± 0.032 / 0.510 ± 0.026, so the corner king is not visibly paid for in long
games. Real and measurable, and without measurable cost of its own.

**Repaired** by `KING_LINE_OFFSETS` in `WeightingFunction`, indexed by the king's column: 0 on the
a-file, −2 on the h-file, −1 in between, so h1 reads f, g, h and a1 reads a, b, c. The
`Board.illegal` guard inside the classifier went with it, since the caller can no longer pass an
off-board square — note the exchange, because it makes a precondition load-bearing: a wrong offset
would now walk the border column, find neither pawn nor major piece and fall through to "open
file", **inventing** three points of danger rather than omitting them.
`WeightingFunctionKingLineTest` grew to 29 cases; the pre-existing
`anOffBoardFileContributesNothing` asserted the old behavior, which is to say it pinned the defect,
and is replaced by two tests that assert the window stays on the board for all 64 king squares
*and* that the offsets pick the three nearest files.

#### The factor, re-swept on the corrected scan — and the overshoot reading dies

[`king-line-factor-sweep-corrected.log`](../test-results/king-line-factor-sweep-corrected.log),
same corpus as § 4.12 (1 338 857 training / 148 762 validation from `tuning-data/hybrid.epd`):

| factor | with the defect | corrected |
|---|---:|---:|
| −0.0000 | 0.07046911 | 0.07046837 |
| −0.0080 | **0.07005673** ← min | 0.06998560 |
| −0.0100 | 0.07010550 | **0.06997137** ← min |
| −0.0120 | 0.07021272 | 0.06999892 |

**The optimum moves from −0.008 to −0.0100, the shipped value, in both columns** (fixed and
refitted k). § 4.12's "the term was near-optimally scaled" survives; its −0.008 figure was an
artifact of the defect. The corrected term also explains **20.5 % more** — the MSE reduction
against a disabled term rises from 0.00041238 to 0.00049700. The factor-0 rows differ by 7.4·10⁻⁷,
which they must, since the term is switched off there; that they do is the check that the two runs
saw comparable data rather than differing by accident.

The proxy is still a proxy, and this series has already falsified it once as an Elo predictor: file
danger screened at 1.8× attack units and delivered ≈ 0 against +9.1.

#### The pending run, and what is deliberately unchanged

`versions/4.6.0-king-line-corner` against `4.6.0`, 6000 fixed-N games at `tc=40/60`, started
2026-09-04. **Exactly one change from the build measured above**, which is why neither the factor
nor the table was touched: the sweep says the factor is already optimal, and refitting the table
would make the run measure two changes. The table's top entries (11, 12) are still the ones pooled
down to index 10's value under 0.02 % and 0.00 % occupancy — occupancy the repair now makes
reachable — so **a table refit is the designated next lever if this run comes back neutral.**

### 4.14 The repaired term measured, and the family closed (2026-09-06)

**Stopped at 2989 of 6000 games: 1075–1115–799, score 0.493, −4.9 ± 10.7 Elo, LOS 18.4 %.**
Stopped rather than finished, and for a reason that was arithmetic rather than impatience: the
rule agreed before the run was "ship only if clearly positive", and at an interval upper bound of
**+5.8** a clearly positive result was no longer reachable. The remaining 3000 games would have
narrowed the interval to about ±7.6 — still spanning zero, still neither a keep nor a proven
loss, since resolving −5 as distinct from zero wants roughly 14 000 games. The machine's next 38
hours are worth more on [§ 4.10](king-safety.md)'s open **+9.1**, which is the only positive
measurement this document holds.

**The repair did exactly what it was built to do, and that is measured in games rather than in
arithmetic.** The corner discount is gone:

| h-file, share of own king positions | candidate | baseline | difference |
|---|---:|---:|---:|
| § 4.13's run, defect present | 10.28 % | 5.66 % | **+4.62 pp** |
| this run, defect repaired | **7.32 %** | 7.22 % | **+0.10 pp** |

Before, the candidate sat on the h-file nearly twice as often as the baseline; now the two agree
to a tenth of a percentage point. The term still hits its target quantity —
**25.15 % against 36.04 %** of king files without an own pawn, −30.2 %, matching § 4.13's −34.6 %
— and the king still stands further back (+0.21 squares from the centre by move 51 against § 4.13's
+0.30), which is the g-file rising from 41.91 % to 50.83 % and is the intended behaviour rather
than the defect.

**So six things are true at once and the sixth is the one that decides.**

| | |
|---|---|
| the term changes its target quantity | −30.2 % uncovered king files |
| the defect is gone | corner excess +4.62 pp → +0.10 pp |
| it costs almost nothing | −0.041 plies, no time forfeits |
| the proxy improved | +20.5 % explanatory power |
| the table and factor are calibrated for it | Texel optimum at the shipped −0.0100 |
| **Elo** | **−4.9 ± 10.7 over 2989 games** |

**This closes the file-danger family, and not with "we built it wrong".** Every excuse the earlier
attempts could claim has been spent: the attack-unit term's −42.9 was cost ([§ 4.10](king-safety.md));
the first table was fitted against the wrong objective ([§ 4.12](king-safety.md)); this table is
fitted against game results, the geometry is correct, the factor is at its measured optimum and the
cost is four hundredths of a ply. Read together with § 4.13's finding that **exposure is a symptom
rather than a cause** — 70 Elo of association for the baseline, none for the candidate — the
conclusion is about the quantity and not about the implementation: *how much pawn cover stands
beside the king carries no Elo in myChess.*

That verdict extends to the shelved standalone shield (−57.5) and the king-dependent pawn PSTs
(−18.1), which measure the same quantity in different packaging, and to virtual queen mobility,
which § 4.11 records as subsumed by file danger. It does **not** extend to attacker-based king
safety: that measures a different quantity, and it is the one still holding a positive number.

The table refit § 4.13 named as "the designated next lever if this run comes back neutral" is
therefore **not** taken. It would sharpen a table whose input the games have just priced at zero.

#### Where the zero comes from: the term does not know about castling

**The user's hypothesis, and the games confirm it.** The window is the three files at the king, so
before castling it reads **d, e and f** — and a term that wants those closed has an opinion about
the opening that nobody intended it to have. The measurement below is the first in this document
to name a *mechanism* for how a term that moves its target quantity by a third still arrives at
zero: it pays in the opening what it earns in the middlegame.

**The metric had to be fixed first, and the correction is the same one this file makes about the
term itself.** The obvious measurement — "does the candidate advance its central pawns less
often?" — asks the wrong question, because `e4` leaves the pawn on the e-file and the file stays
covered. Only a move that takes the pawn *off* the file opens it, which for a pawn means a
capture. The first run of this analysis counted d/e advances from the home square, found +19.4 %
for the candidate, and measured nothing at all.

**The right measurement is the opportunity rate**, which separates a decision from a
circumstance. Window: after the eight book plies, before the side's own castling, 2989 games.

| | candidate | baseline |
|---|---:|---:|
| a capture taking an own pawn off a king file **was available** | 1710 | 2288 |
| **and was played** | **35.20 %** | **42.18 %** |
| own pawn left a king file, per own move | 3.043 % | 3.803 % |

**−16.5 % relative on the opportunity rate.** The denominator is the opportunity and the numerator
is the choice, so this is not an artefact of the candidate having fewer chances — it declines
central pawn captures while its king is still on e1, in otherwise equal situations, one time in six
more often than the baseline.

Three consequences line up with it:

- **The state it buys.** Uncovered king files, restricted to *uncastled* positions in moves 5–15:
  **10.08 % against 16.07 %**, −6.0 pp. The d-, e- and f-files really do stay shut longer.
- **The opponent gets through less often.** An enemy capture that takes a pawn off the candidate's
  king file lands in 32.69 % of games against 41.05 % for the baseline.
- **The king leaves earlier.** Castling at move **9.00** against 9.36 on average, and in **96.02 %**
  of games against 93.24 % — 119 games never castled against 202. Consistent with the incentive:
  while the king sits on e1 every open central file costs, so it goes.

**This looked like the first finding pointing at a repair rather than at a wall** — the term would
have to know whether the king has castled and do nothing, or much less, before it has. Three
designs were on the table: gate on castling rights, exclude the d- and e-files outright, or weight
the files continuously with the centre counting least. The last is the technically cleanest, since
a boolean gate only moves the artefact: the move that forfeits the second rook's right would
switch the whole penalty on, and the engine would start avoiding *that*.

#### The opening cost was priced, and it is not there

**None of the three designs is worth building, because the thing they remove costs nothing.** Every
one of the 2431 declined opportunities was priced with Stockfish at depth 16: the position after the
move actually played against the position after the best available capture, from the mover's side.

**The measurement had to be corrected twice more before it said anything.** Counting every legal
capture as an "opportunity" makes the mean meaningless — most such captures are simply bad and both
engines rightly decline them, and mate scores substituted as ±30000 then dominate the average
(candidate −339 cp, baseline −473 cp, both artefacts). What carries information is the subset where
the capture really *was* better:

| after the book, uncastled | candidate | baseline |
|---|---:|---:|
| declines where the capture was actually better | 336 of 1108 (**30.3 %**) | 334 of 1323 (25.2 %) |
| median value given up | 29 cp | 38 cp |
| **centipawns given up per game** | **5.25** | **5.77** |
| gross misses (> 100 cp) | 0.013 / game | 0.015 / game |

**The candidate declines 16.5 % more often and gives up half a centipawn per game *less*.** The
captures it turns down are ones that mostly should be turned down; only a third of its declines
were mistakes at all, against a quarter for the baseline, and its mistakes are individually
cheaper. Gross misses are equally rare on both sides. Running the difference through the calibrated
sigmoid yields −1.76 Elo, which should not be quoted: at half a centipawn per game it is
indistinguishable from zero, and the statement is the magnitude — **under one centipawn per game.**

A second route agrees by failing to disagree. Grouping the 2989 games by who declined and comparing
the decliner's score — candidate 0.5067 over 300 games against baseline 0.5564 over 408 — gives
−0.0497 ± 0.0660, t = −1.48, not separable from zero. It also carries its own confound, and one
worth recording: **the decliner scores above 0.5 in both cases**, because these opportunities arise
in positions where one has choices, and having choices correlates with standing well. Same shape as
§ 4.13's exposure finding — the quantity is symptom and cause at once. The control passes: the
candidate's overall score in that pass is 0.4933, −4.6 Elo against the match's −4.9 ± 10.7.

**So the arithmetic that motivated the repair does not hold.** −4.9 is not "middlegame gain minus
opening cost" with a recoverable second term; the opening component is zero, which makes the
middlegame component itself about −5. The user's hypothesis was correctly posed and the check was
necessary — it closed a plausible rescue route instead of leaving it open — but the answer is that
the behavioural difference is real, measurable, and harmless. Which is exactly what this document
warns about elsewhere: **a measured change in behaviour is not a measured cost.**

Two residuals are left unpriced. The term makes the king castle earlier (move 9.00 against 9.36,
96.02 % of games against 93.24 %), and that is not evaluated here; it could cut either way. And
depth 16 is the resolution of the pricing.

All five scripts are kept, including the three that measured the wrong thing, because the errors
have one shape and it is worth recognising: counting advances instead of captures away, taking
frequency instead of the opportunity rate as the denominator, and admitting every capture instead
of the good ones.

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
3. **Replace the curve with the fitted one** (§ 4.7): `0 0 0 13 16 47 47 47 80`, indices 0–8,
   everything above clamped onto index 8. Measured, not chosen, and monotone by construction.
   The inherited table on `05f337d` reads
   `0 0 5 5 10 10 15 15 25 35 50 65 85 105 130 160 195 235 285 340 400` — its upper half covers
   indices real play reaches in 0.3 % of positions, and across the fitted range it carries
   25 % to 50 % of what the fit puts there — 5 against 20 at three units, 25 against 85 at
   eight.

   The top entry is 83 cp against the § 4.4 cap of 150, so this calibration clears it — but only
   by a factor of 1.8, and the anchor calibration would *not* clear it at 191. The test that
   compares the two constants is therefore load-bearing, not decoration.
4. ~~**Replace the `≥ 2 attackers` gate** (F2). It asks whether enough pieces are present, not
   whether the position is dangerous.~~ **Reversed by measurement on 2026-08-30 — keep the
   gate.** The piece-count question turned out to be doing real work: it is what makes an index
   mean one thing. See § 4.7.
5. **Cap the curve below `EVALUATE_MATERIAL_ONLY_THRESHOLD`** (§ 4.4). The inherited table
   crosses 200 cp at index 17; a cap at 150 keeps the term from ever making a piece sacrifice
   look profitable on its own, which is the trap that cost the Audax fork 67 Elo. Guard it with
   a test that compares the two constants, and watch the sacrifice rate in the match.
6. **Settle the two deferred test expectations.** `WeightingFunctionTest.testPosition34/35`
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
