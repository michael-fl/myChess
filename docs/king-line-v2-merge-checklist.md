# king-line-v2 — what has to happen before it is merged

Short-lived working note for branch `king-line-v2`. **Fold into
[`king-safety.md`](king-safety.md) § 4.15 or delete once the branch is decided** — it lives in
its own file only so it cannot conflict with master, whose § 4.15 this branch does not carry yet.

## Where it stands (2026-09-07)

The term is the king-line danger of [§ 4.11–4.14](king-safety.md), plus a gate: it is scored only
once that **color's** castling question is settled — castled, or both rights lost. The gate was
first written against `GameStatus.isCastlingPossible()`, which asks only about the side to move;
that made the term flicker with the clock and is fixed in `cbaf8e5`, pinned by
`WeightingFunctionKingLineTest.theKingLineTermIsIndependentOfWhoseTurnItIs`.

| | |
|---|---|
| **Final result** | **+6.1 ± 7.5** Elo over the full 6000 games, score 0.50883 (2257 − 2151 − 1592), LOS 94.5 %, interval [−1.40 … +13.68] |
| Unbiased | `-sprt` was passed **without a band**, so no bound could fire and the run went to full length. The estimate is not one taken at an accepted bound |
| Comparable | the ungated variant measured **−4.9 ± 10.7** over 2989 games (§ 4.14). Those intervals no longer overlap much: this variant is the better of the two |
| Caveat | by segment the candidate scored 50.1 / 49.2 / 50.6 / **53.2** / 51.9 %, so one 1160-game stretch at +2.6 SE carries about half the figure. Not grounds to correct the estimate — dropping a favourable segment is post-hoc — but at 2296 games the same run read −2.6 |

Behavior, candidate against baseline in the same run, and it confirms the fix did what it should:

| | v2 (fixed) | 4.6.1 | Δ | v2 (buggy gate) |
|---|---:|---:|---:|---:|
| unsheltered king files, **settled** | 1.0472 | 1.5358 | **−31.8 %** | −31.8 % |
| unsheltered king files, **unsettled** | 0.3101 | 0.2552 | **+21.5 %** | −17.8 % |
| average castling move | 9.60 | 9.11 | 0.49 **later** | 0.36 earlier |
| castling rate | 95.5 % | 94.1 % | +1.4 pp, z = 3.5 | — |

The sign of the unsettled row flipped and the castling move went from earlier to later: the term
really is off before castling now. Read the signs, not the magnitudes — the unsettled figure comes
from ~19 k plies and the baseline arm moved 17 % between runs on its own.

## The position to decide

The owner's inclination is to **merge on a neutral result**: some king safety, specifically on the
defensive side, is better than none. That is defensible, and this document's own expectations
section says a zero would be progress for this family. Two numbers are missing before it can be
decided on evidence rather than on inclination, and both are cheap against the 6000 games already
being spent.

## Step 1 — let the 6000 games finish — **DONE (2026-09-09)**

Result above: **+6.1 ± 7.5**, unbiased. It was meant as the assurance that the term does not hurt,
and it delivers more than that — the point estimate is positive and the null sits just inside the
lower edge of the interval. Separating +6 from 0 would need about **13 700 games**, i.e. another
6.7 days, for a number that would not change the decision.

## Step 2 — measure what the gated term costs

**The number on record is the wrong one.** [`bench-history.md`](bench-history.md) has **−5.55 %
NPS** for the king-line term, measured as `4.6.0-king-line, factor 0` against `4.6.0`: identical
tree node for node, so pure computation. That is the **ungated** term.

**Measured 2026-09-07, and the estimate was too low.** Nine `bench 6` runs over the three
gauntlet arms in a Latin square ([`bench-history.md`](bench-history.md), "What the gated king-line
term costs"): the walk costs **+7.75 %** of wall clock, the candidate pays only **+2.40 %** net
because its tree is **5.66 %** smaller, and base against placebo comes out **identical to the
node** — 151,248,502 — which is what makes the cost figure pure. In plies: 0.126 for the walk,
0.040 net, the latter matching § 4.14's independently derived 0.041.

The estimate this replaces read: "the gate saves about a seventh of the cost, an estimated −4.7 %",
reasoned from 108 k settled plies against 19 k unsettled in the running match. Wrong in both
directions at once — the walk is more expensive than the ungated figure suggested, and the
candidate is much cheaper than the walk, which no ply-share argument could have produced.

**Use `bench` over the standard suite for the cost, not `benchv2` — this note had it backwards
at first.** 49 of the standard suite's 55 positions carry no castling rights, which means both
sides count as *settled*, the gate stands open and the walk runs almost everywhere. That is exactly
what a cost measurement wants. What the standard suite cannot see is the *behavioral* difference
between the gated and the ungated term, because wherever the gate is open the two agree — and that
is the question `benchv2`'s half-uncastled set answers. Two different questions, and the earlier
wording applied the answer to the wrong one. (`benchv2` also only exists in the base build: the
king-line branches predate it.)

Why this matters even though the +2.3 is already net of the cost: it is net **at `tc=40/60`**. An
NPS loss weighs more at faster time controls, so a term that is exactly zero at 40/60 is probably
slightly negative at 3+2 — which is what the engine plays on lichess.

## Step 3 — run it against the anchors, and decide on that — **DONE (2026-09-12)**

**Result: net 0.0 Elo, composed of +10.9 effect and −10.9 cost.** 2886 games of the planned 4200,
960 per arm, stopped on a schedule fixed the evening before for a machine transport, so the estimate
is unbiased. Full write-up in [`king-line-gauntlet.md`](king-line-gauntlet.md).

| arm | score | Ordo |
|---|---:|---:|
| base | 55.9 % | 1945.9 |
| king-line-v2 | 55.9 % | 1945.9 |
| placebo | 54.6 % | 1935.0 |

And the term demonstrably does its job against foreign opponents: **−15.2 % unsheltered king files**
at t = −5.05, against a placebo null line of t = 0.03. Split by the gate, −16.8 % where the term is
on and nothing where it is off. The behavior transfers; the Elo does not follow, because the walk
costs what the term is worth.

**All three steps are now done.** What remains is the decision itself, and the three numbers on the
table are: +6.1 ± 7.5 over 6000 self-play games, 0.0 net against the anchors, and a behavioral effect
confirmed twice over. The new information the gauntlet added is not the zero — it is that the zero is
a **cancellation**, which makes the cost the thing to attack rather than the term.

### The original framing of this step



**All 6000 games are self-play**, and that is the constellation in which a defensive term shows
least: its opponent is exactly as good at exploiting king exposure as it is, because it *is* the
same engine. The target metric is standard-chess Elo against foreign opponents.

This is the owner's own objection from 2026-09-04 — "bringt vielleicht nichts wenn myChess
praktisch gegen sich selber spielt, aber vielleicht bringt es ja allgemein" — and it has never been
tested by a **match**. It was tested by screens (§ 4.13, § 4.15), which came out negative, but a
screen can only stop, never license, and it measures association rather than gain.

The instrument exists: `tools/run-anchor-bracket.sh`, five foreign engines from 1609 to 2019,
2000 games.

**Decision rule:** neutral or better against the anchors → merge without reservation, the insurance
is free. Negative → the inclination is refuted in exactly the place it claims an advantage.

## What merging on a zero costs, beyond Elo

**It makes every later measurement more expensive.** After the merge every further king-safety
experiment runs on top of the term and has to beat it. If it is truly zero that is harmless; if it
is slightly negative — and ±7.5 leaves that open — it becomes a permanent small tax no later run
can isolate.

And it adds a fitted 13-entry table plus a factor to the maintained surface, both of which should
strictly be re-fitted after any other evaluation change.

Not a veto. The price of the insurance, stated next to the benefit it might have.
