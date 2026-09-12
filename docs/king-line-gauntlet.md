# The three-arm anchor gauntlet — measuring the king-line term against foreign opponents

Every match the king-line term has faced was **self-play**. That is the constellation in which a
defensive term shows least: its opponent is exactly as good at exploiting king exposure as it is,
because it *is* the same engine. The target metric, though, is standard-chess Elo against foreign
opponents ([`roadmap.md`](roadmap.md) and the anchor bracket), and against those the term has never
been measured by a match at all — only by corpus screens
([`king-safety.md`](king-safety.md) § 4.13, § 4.15), which came out negative but can only stop,
never license.

This document describes the setup that closes that gap. **It has not been run yet.**

## The three arms, and why the middle one exists

| arm | king-line term | evaluation | cost |
|---|---|---|---|
| **base** | absent | — | — |
| **placebo** | computed, factor `0` | identical to base | identical to candidate |
| **king-line-v2** | computed, factor `-0.01` | the term applies | identical to placebo |

The placebo is the point of the design. It decomposes what a two-engine match cannot separate:

- **placebo against base** — the **cost** of computing the term, priced in Elo under the clock at
  the time control that is actually played, rather than in NPS.
- **king-line-v2 against placebo** — the **effect** of applying the penalty, with the cost held
  constant.

A single candidate-against-baseline match only ever reports the sum of the two, and a term whose
effect is +8 and whose cost is −8 is indistinguishable there from a term that does nothing.

**Verified at the evaluation level** on `r3k2r/pppppppp/8/8/8/8/PPPPPP1P/RNBQ1RK1 w kq - 0 20`
(white settled on g1 with a half-open g-file, black still holding both rights, so the term applies
to white only):

| arm | `weight` command output | total |
|---|---|---:|
| base | no `kingLinePenalty` line at all | 14.02 |
| king-line-v2 | `w=15, b=0, delta=15, weight=-0.15` | 13.87 |
| placebo | `w=15, b=0, delta=15, weight=0.0` | **14.02** |

The placebo computes the danger — `w=15` is there — applies nothing, and lands on exactly base's
total. That equality is the arm's entry ticket.

## Build provenance

| directory | branch | commit |
|---|---|---|
| `versions/4.6.1-gauntlet-base` | `master` | `4e80a21` |
| `versions/4.6.1-gauntlet-king-line-v2` | `king-line-v2` | `07c1d16` |
| `versions/4.6.1-gauntlet-placebo` | `king-line-v2-placebo` | `6fdf469` |

Each carries a `BUILT-FROM.txt` with the commit and the jar's SHA-256, per
[`bench-history.md`](bench-history.md) § 6 rule 7. All three wrappers are generated from one
template, so the JVM flags are identical across the arms and match every 4.2.x–4.6.x build:
256 MB heap, `AlwaysPreTouch`, `SerialGC`.

**Two build traps were hit while producing these, both of which would have invalidated the whole
run silently.** They are recorded because neither announces itself:

1. **`mvn package` without `clean` shipped three identical jars.** The compiler recompiled after
   every branch switch — `target/classes` held the third arm's class — but the jar plugin left the
   archive from the *first* build in place. All three copies carried the baseline's
   `WeightingFunction.class`, verified by extracting and hashing it. The gauntlet would have run
   three identical engines against each other. Use `mvn clean package`.
2. **The jar name follows the pom version, and the experiment branches bump it.** `master` builds
   `my-chess-4.6.1.jar`, both king-line branches build `my-chess-4.6.1-king-line-v2.jar`. A
   hard-coded source name made the copy step fail for two of three arms — loudly, in that case,
   but only because the file was missing rather than wrong. The build helper now globs
   `target/my-chess-*.jar` and refuses unless exactly one exists.

A hash comparison catches (1); it does not catch a build that is *different but not what you
think*. That is what the `weight`-command probe above is for, and it should be repeated whenever an
arm is rebuilt.

## Tournament shape: gauntlet with three seeds, not a round robin

The obvious form — all eight engines against all eight — wastes more than a third of the budget:

| pairings | count | |
|---|---:|---|
| variant against anchor | 15 | the question |
| variant against variant | 3 | self-play again |
| **anchor against anchor** | **10** | **36 % of the budget, answers nothing** |

The anchors' relative strength is already known from `test-results/bracket-4.4.1.pgn`, 2000 games.
`cutechess-cli` can drop those pairings outright:

```
-tournament gauntlet -seeds 3
```

`GauntletTournament::gamesPerCycle()` is `(playerCount - seedCount) * seedCount`, so with the three
variants first and the five anchors after it plays exactly 3 × 5 = 15 pairings and **no
anchor-against-anchor games**. The seeds do not meet each other either, which is deliberate: a game
between two myChess variants is self-play, the regime this tournament exists to escape.

**And the openings are paired across the arms:**

```
-openings ... policy=round
```

`policy=round` "shifts only for a new round", so within a round every pairing uses the same opening
— all three arms face each anchor from the same position. That turns three independent samples into
a paired comparison and cuts the variance of the *difference* without a single extra game.

## What it can and cannot resolve

Throughput from the bracket: about 2:44 per game, so at concurrency 4 roughly **87 games/hour,
2100 per day**. Two days over 15 pairings gives 280 games per pairing, i.e. **1400 games per arm
against the anchors**:

| | 95 % interval on the difference between two arms |
|---|---|
| unpaired openings | ±3.3 pp ≈ **±23 Elo** |
| with `policy=round` | ≈ ±2.4 pp ≈ **±17 Elo** |

Coarser than the 6000-game self-play run's ±7.5, and unavoidably so — three arms third the budget.
**What it can do** is find a *large* effect against foreign opponents, from roughly 25–30 Elo up.
**What it cannot do** is separate +2 from 0.

That is the right trade here, because the hypothesis under test is not "the term is worth 3 Elo"
but "against opponents that do not defend their kings, it does something substantial". If the
answer is 5 Elo, no affordable experiment will show it and the merge stays a judgment call.

**Read points, not Elo.** All three arms face the same five opponents equally often, so the raw
point totals are a sufficient statistic for the difference and need no rating model on top.

**One asymmetry to keep in mind when reading the cost arm.** The gauntlet runs at `tc=40/120`, the
anchor bracket's time control, so its games are comparable to `bracket-4.4.1.pgn`. The self-play
run of the same term runs at `tc=40/60`. A computation cost weighs *more* the less time there is,
so the placebo-against-base figure measured here is the cost at the **slower** of the two controls
— a lower bound on what the term costs at 40/60, and further still from what it costs at the 3+2
the engine plays on lichess. A cost that is invisible here is not thereby harmless there.

## Mandatory pre-flight

1. **Read all three `BUILT-FROM.txt`** and re-run the `weight`-command probe (table above). Rule 7
   exists because this project once measured against a baseline jar that predated three
   behavior-relevant commits.
2. **Signature and NPS of base against placebo.** The signature must be **identical** — that
   proves the placebo's evaluation is base's and the arm is a pure cost arm. The NPS must be
   **measurably lower** — that proves the walk is really executed.

   Equal NPS is the alarm: it would mean the optimizer folded the multiplication by zero and the
   placebo arm is worthless. The construction was measured once before and was *not* folded
   (`4.6.0-king-line, factor 0`: signature bit-identical at 1,300,002,835, NPS 1,181,726 against
   1,251,215, so −5.55 %), plausibly because the walk stores into `kingLineDanger[color]`, a field
   the breakdown output and `getKingLineDanger()` also read. If it ever does get folded, the fix is
   to consume the result observably — a `Statistics` counter, as the material-only counter does —
   **not** to perturb the factor to `0.000001f`, which would cost the bit-identical signature the
   cost measurement depends on.

   Use `bench` over the standard suite for this, not `benchv2`: 49 of its 55 positions carry no
   castling rights, which means both sides count as *settled*, the gate stands open and the walk
   runs almost everywhere — exactly what a cost measurement wants. (`benchv2` is the right suite
   for the opposite question, whether gating changes *behavior*, and it only exists in the base
   build anyway, since the branches predate it.)
3. **Wait for a free machine** — `--wait-for-cores` in `tools/run-anchor-bracket.sh` is the
   precedent. At `tc=40/60` the result depends on how many cores the engines actually get.

## The result (2026-09-12)

**Stopped at 2886 of 4200 games, 960 per arm**, because the machine had to be taken off the
network. The stopping time was fixed the evening before, without reference to the standing, so this
is an unbiased fixed-N result over the games that were played — see the note on optional stopping
below.

| arm | score | games | Ordo |
|---|---:|---:|---:|
| base | 55.9 % | 961 | 1945.9 ± 17.8 |
| **king-line-v2** | **55.9 %** | 960 | **1945.9 ± 17.6** |
| placebo | 54.6 % | 960 | 1935.0 ± 18.0 |

Anchors in the same run: Kojiro 2018.5, BBC 2019 and Princhess 1985 fixed, ZetaDva 1801, TSCP 1609.

**The decomposition the middle arm exists for:**

| | in points | in Elo | t |
|---|---:|---:|---:|
| **effect** — candidate vs placebo | +1.3 pp | +10.9 | 0.63 |
| **cost** — placebo vs base | −1.3 pp | −10.9 | −0.63 |
| **net** — candidate vs base | **+0.0 pp** | **0.0** | 0.00 |

The net is zero to the decimal, and that is the number the merge decision turns on. But it is a
**different kind of zero than the family has produced before**, and the difference is the whole
point of having built a cost arm: this is not "the term does nothing". It is "the term is worth
about as much as it costs, and the two cancel".

Neither half is established on its own — ±2.07 pp on a difference is a t of 0.63 either way, and a
run of this length cannot separate +11 Elo from zero. What raises the cost figure above a guess is
that it agrees with an independent measurement made a different way: the nine-run Latin square in
[`bench-history.md`](bench-history.md) puts the walk at **+7.75 % of wall clock**, 0.126 plies, and
the candidate at +2.40 % net. An 11-Elo price for that is the right order of magnitude. Two
instruments, one number.

### The term does what it was built to do — against foreign opponents

Measured from the PGNs as the count of unsheltered files at and beside the arm's own king
(`myChess-lab/scripts/king-exposure-overall.py`), one observation per game:

| arm | unsheltered king files | vs base | t |
|---|---:|---:|---:|
| base | 0.7919 | — | — |
| placebo | 0.7927 | +0.1 % | **0.03** |
| king-line-v2 | 0.6719 | **−15.2 %** | **−5.05** |

**The placebo row is what makes this readable.** It carries base's evaluation exactly — pre-flight 3
proved it by an identical bench signature — so its difference from base estimates zero by
construction. It comes out at t = 0.03. That is the instrument's own noise floor, measured rather
than assumed, and the candidate's −15.2 % stands five standard errors clear of it.

Split by the gate, the term's on/off switch:

| | base | king-line-v2 | t |
|---|---:|---:|---:|
| castling settled — term **on** | 0.8991 | 0.7483 (−16.8 %) | **−5.40** |
| castling still open — term **off** | 0.2168 | 0.2358 (+8.8 %) | 1.25 |

An effect where the term acts, none where it is gated off. The gate works. The candidate also
castles **0.33 moves later** (9.42 against 9.09) at an unchanged castling rate, which is the same
fingerprint the 6000-game self-play run produced (0.49 moves later, −31.8 % settled). **The
behavioral effect transfers from self-play to foreign opposition** — which is what this tournament
was built to find out, and it is the one question the screens of § 4.13 and § 4.15 could not answer.

### What this leaves the decision

The rule stated before the run was: neutral or better against the anchors → merge, the insurance is
free. This is neutral, so the rule says merge.

What the rule did not anticipate is a zero *composed of* a real benefit and a real cost. That
composition points somewhere the old reading did not: **the walk is the problem, not the term.** If
the +7.75 % of wall clock came down, the +10.9 would not have to pay for itself. That is an
optimization question with a measurable target, and it is a better position than the family has been
in since it started.

Against that stands what [`king-line-v2-merge-checklist.md`](king-line-v2-merge-checklist.md)
already says about merging on a zero: every later king-safety experiment then runs on top of the
term and has to beat it, and a fitted 13-entry table plus a factor join the maintained surface.

### On stopping early

2886 of 4200 is 69 % of the planned length, and the interval is correspondingly wider: ±2.07 pp on a
difference instead of the ±1.7 the full run would have given. Nothing else changes. The stop was
scheduled the previous evening for a reason unconnected to the data — the machine had to be
transported — so it is not optional stopping and the estimate carries no selection bias. Had the
standing been consulted first and the run ended because it "looked clear", the result would have
been worth much less than its interval suggests.

The arms stayed balanced to within one game (961 / 960 / 960), because cutechess cycles the 15
pairings in order and an interrupted cycle can leave at most one game of imbalance per pairing.

---

## Afterwards

The one pairing the gauntlet deliberately omits is **king-line-v2 against placebo**, and it is the
cleanest A/B of the term that exists: identical code paths, identical cost, one constant apart. It
deserves its own two-engine match rather than 1/24 of a tournament — 1600 games give ±14.5 Elo,
3000 give ±11.

Smuggling copies of the arms in as extra opponents was considered and rejected: it would add the
variant-against-variant pairings at 350 games each (±33 Elo, four times worse than the self-play
run already provides), spend 12.5 % of the tournament on builds playing themselves, and dilute the
anchor comparison from ±17 to ±21.

The one legitimate use of such a copy is a **calibration run** — a build against an identical copy,
whose true result is 50 % by construction, which is the only way to separate an apparatus asymmetry
from a real strength difference. That is now policy in [`bench-history.md`](bench-history.md) § 6
rule 8: 2000 games, once, and repeated only when the apparatus changes.
