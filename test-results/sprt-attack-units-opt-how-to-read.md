# How to read the second attack-unit SPRT

Written 2026-09-01 at 01:30, while the run is at 228 games, so that the argument is on record
before the number is — it is easier to reason about an experiment than about a result one likes.

## The two runs differ in exactly one thing

| | run 1 | run 2 |
|---|---|---|
| build | `4.6.0-attack-units` | `4.6.0-attack-units-opt` |
| bench signature @ d8 | 451,759,691 | **451,759,691** |
| NPS, 53 realistic | 1,401,033 | 1,638,414 |
| time to depth 8 | +31.5 % vs base | +12.4 % vs base |

The bench signatures are **bit-identical, position by position**. The two builds explore the same
tree and choose the same moves at the same depth; the optimisation removed 432 boolean writes and
a board traversal per evaluation and changed nothing else. Verified against the same 4.6.0
baseline re-measured on the same machine, which reproduced its documented 1,300,002,835 and
101,626,447 to the digit.

**So under a clock there is exactly one channel by which the two runs can differ: how deep each
gets in the time available.** Not the curve, not the gate, not the move choices at equal depth —
those are provably the same.

## What follows, and what does not

If run 2 lands materially above run 1's −42.9 ± 33.9, the cause is **not in question**. It is the
node rate. There is no second explanation available.

That is a causal statement, not a statistical one, and the two must not be merged. Whether the
difference is *real* is still governed by the intervals: two results at roughly ±35 give a
difference with an interval near ±48, so a gap of 20 or 30 Elo is not distinguishable from noise.
The clean attribution only helps once the gap is large enough to believe.

## What it would mean for what is already written

`docs/king-safety.md` § 4.8 and roadmap § 12.21 currently attribute the −42.9 to a diffuse
evaluation error — "static noise added to a search that already resolves king attacks
tactically" — on the evidence that only 6 of 301 games showed a material investment. That
attribution was made from run 1 alone, when the cost was 22 % of the node rate.

**If run 2 comes back near zero, that attribution is wrong and has to be corrected**, not
softened: the term would then not be harmful, it would be worthless — a different finding with a
different consequence. The decision is the same either way, because the rule agreed in advance is
to ship only on a clearly positive result. The reason recorded in the documents is not the same,
and the reason is what someone reads in a year.

## The check that separates the two

The optimised build plays identically at fixed depth, so its *style* must be unchanged:
`MatchStyleAnalysis` over run 2's PGN should reproduce run 1's conviction rate of 2.0 % against
the baseline's 0.3 %, and the peak attack index of 8 against 7. If it does, the style evidence
carries over untouched and only the Elo moved. If it does not, something other than the clock
differed between the runs and the comparison above is void.

    java -cp "target/classes:target/test-classes:target/dependency/*" \
         org.michaelfl.mychess.MatchStyleAnalysis test-results/sprt-attack-units-opt.pgn

---

## The falsification check, run at 273 games (2026-09-01, 02:00)

Mixed, and the half that fails concerns a claim already written into the documents.

**Reproduces — the robust indicators.**

| | run 1 (301 games) | run 2 (273 games) |
|---|---|---|
| checks per game, candidate : base | 4.9 : 4.0 | 4.8 : 3.9 |
| peak attack index, median | 8 : 7 | 8 : 7 |

Near-identical, which supports the argument above: the builds play the same at equal depth.

**Does not reproduce — the baseline's conviction rate.** 0.3 % in run 1 (1 game of 301) against
2.2 % in run 2 (6 of 273). That is the *same 4.6.0 binary* in both runs; its style cannot have
changed. Different openings and small absolute counts are the whole story.

So the single conviction game in run 1 was a low draw, not a stable property — and the claim built
on it does not hold. `roadmap.md` § 12.21 and `king-safety.md` § 4.8 both cite "6 conviction games
against the baseline's 1" as evidence that the term tempts the engine into material investments.
In run 2 it is 7 against 6, and the contrast is gone.

**The conclusion that rested on it survives; the reason has to change.** The argument was that six
games of 301 cannot explain a twelve-point win-rate gap, so the damage is diffuse rather than a
handful of sacrifices. That still holds — it holds *more* strongly at 7 against 6, because now the
candidate is barely sacrificing more than the baseline at all. What must go is the framing that
the term produced a distinctive sacrificial style. It did not; it produced more checks and a
higher peak attack index, and those are the two indicators that actually reproduce.

**Correction to make on master**, both files: replace the conviction-rate contrast with the two
indicators that survived a second run, and say why — a metric whose counts are 1 and 6 was read as
a sixfold difference when it was a difference of five games.

---

## A worked example of the trap this note warns about (03:10)

The section above says the causal attribution "only helps once the gap is large enough to
believe", and separates that from the statistics. Ninety minutes later I ignored my own warning,
so here is the arithmetic, because the abstract version evidently does not stick.

At 359 games run 2 stood at **0.0 ± 31.2**. Run 1 finished at **−42.9 ± 33.9**. It is tempting —
and I fell for it — to observe that −42.9 lies outside `[−31.2, +31.2]` and conclude the runs
differ. That is the wrong test. It compares one run's point estimate against the other's interval
and ignores the first run's own error entirely.

The right test is the difference, which carries both:

    difference  =  0.0 − (−42.9)  =  +42.9
    error       =  sqrt(33.9² + 31.2²)  =  46.1
    interval    =  [−3.2, +89.0]        -> covers zero

So the runs are **not** distinguishable, narrowly. Both are consistent with a true value anywhere
between about −35 and 0.

**When it would become decidable.** At the 1600-game cap run 2's interval would be near ±13, the
difference near ±36, and a gap of 43 would then clear it. Not before.

**Keep the two statements apart.** That the builds are bit-identical at fixed depth means a real
difference could only come from the node rate — a statement about *cause*, valid whenever a
difference exists. Whether one exists is a separate question with a separate test, and the
attribution must not be used to smuggle the difference into existence.

---

## The larger problem is in run 1, and run 2 cannot fix it (04:30)

Tracking the difference test as run 2 accumulates games:

| games | difference | interval | |
|---:|---:|---|---|
| 359 | +42.9 ± 46.1 | [−3.2, +89.0] | covers zero |
| 400 | +46.4 ± 44.7 | [+1.7, +91.1] | excludes, barely |
| 441 | +46.8 ± 43.8 | [+3.0, +90.6] | excludes, barely |
| 485 | +45.1 ± 43.0 | [+2.1, +88.1] | excludes, barely |

A lower bound of +2.1 is not something to build on, and it has not moved in 130 games.

**And the heavier objection is one this project already documented.** Run 1 stopped at the H0
bound after 304 games, and § 12.23 of the roadmap states the house convention for exactly that
situation: *"an early SPRT stop overestimates"*, with its own worked examples — +69 later
measured +23.0, and +39.8 later +32.6. A factor of three in the first case.

So **−42.9 is a biased estimate of the unoptimised build's true strength**, biased away from
zero. If the truth is nearer −15 or −20, the gap to run 2's ≈ 0 falls to something well inside
its own error and the comparison says nothing at all.

**This is a defect in what I wrote yesterday.** Roadmap § 12.21 quotes −42.9 as the result with
no such caveat, while the same document applies the caveat explicitly at § 12.23 ("Quote it as
≈ +15, not +42"). Correcting that is owed on master regardless of how run 2 ends.

**What it means for the question.** The uncertainty sits in run 1, and run 2 cannot heal it: no
number of games in the second run makes the first run's early stop unbiased. Answering "was it
the cost or the term" properly would need run 1 repeated to a fixed game count, not to a bound —
another six hours, for a branch that is being shelved either way. That is probably not worth it,
but the honest record should say the question stays open rather than let the difference table
above imply it was settled.
