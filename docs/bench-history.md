# Bench history — node signatures per release

Reference table of the `bench` node signature for selected myChess releases, so
the node count of any past version can be looked up without rebuilding it.

- [1. How to read this table](#1-how-to-read-this-table)
- [2. Depth 8 — the full series](#2-depth-8--the-full-series)
- [3. Depth 9 — from 4.3.4 onward](#3-depth-9--from-434-onward)
- [4. What the numbers say](#4-what-the-numbers-say)
- [5. Method](#5-method)
- [6. Policy for future releases](#6-policy-for-future-releases)
- [7. Per-position archive](#7-per-position-archive)

---

## 1. How to read this table

**The node count is an equivalence oracle, not a strength metric.** At a fixed
depth the count is deterministic and bit-reproducible: two builds that visit the
identical number of nodes on every position are search- and eval-identical, and
any divergence localizes a behavioral change. That is what makes `bench` the
primary "is this refactor neutral?" check (see
[roadmap-backlog § 12.10](roadmap-backlog.md#1210-in-process-measurement-harness--sm-no-elo-but-adds-fast-per-change-diagnostics),
subsection 12.10.1).

**It is emphatically not a "lower is better" ranking.** This series makes that
concrete: the transposition table and null-move pruning each roughly halved the
node count, while the all-captures quiescence search — worth +40.6 Elo —
*multiplied it by 2.5*. Both directions bought Elo. A term can also be worth
+31.3 Elo and move the signature by under two percent (4.3.3, bishop pair).
Read the table for *what kind* of change a release was, never for how strong it is.

**Nodes are comparable across machines and years; time and NPS are not.** The
node columns are exact. The time and NPS columns are informative only — they
depend on the machine, the JVM, and the load at measurement time. The project
convention is explicit about it: *compare nodes, never time.*

That is now measured rather than assumed. 4.4.1 was benchmarked three times: once
with two cutechess matches running alongside it (load average ~10 on 10 cores) and
twice on a quiet machine. **All 55 positions were node-identical at both depth 8
and depth 9 across every run**, while the throughput moved by 7 % (1,728,813 to
1,861,394 NPS at depth 8). The equivalence-oracle property holds under load; the
time columns visibly do not, and a run made on a busy machine is worth repeating
before its NPS is recorded. Comparing per position rather than per total is what
makes this a real check — equal totals could also arise from differences that
cancel.

**Deltas span everything between two rows.** These releases are checkpoints, not
consecutive versions. The change from one row to the next bundles every release
in between, so the attribution column names the dominant feature in that span,
not a sole cause.

---

## 2. Depth 8 — the full series

Suite: `standard` (49 Stockfish benchmark positions + 6 myChess middlegames = 55).
All thirteen runs completed every position at the requested depth; no run was
time-truncated.

| Version | Nodes @ d8 | Δ nodes vs prev. row | 53 realistic positions | Dominant change in span | Δ Elo (measured) | ~CCRL |
|---|---:|---:|---:|---|---|---|
| **3.5.2** | 791,340,172 | — (baseline) | 543,710,965 | — | −5.6 ± 21.3 (noise) | ~1441 |
| **3.6.0** | 832,797,076 | **+5.2 %** | 580,029,043 (+6.7 %) | eval: pawn structure / weights | +28.1 ± 20.5 | ~1469 |
| **4.0.7** | 370,196,066 | **−55.5 %** | 275,025,711 (−52.6 %) | **transposition table** (4.0.0) + en-passant Zobrist fix | +92.7 for the TT itself | ~1587 |
| **4.1.0** | 194,309,372 | **−47.5 %** | 161,986,703 (−41.1 %) | **null-move pruning** | +76.0 ± 10.1 | ~1663 |
| **4.2.1** | 487,757,233 | **+151.0 %** | 199,631,831 (+23.2 %) | **quiescence: all captures + MVV-LVA + SEE** | +40.6 ± 9.4 | ~1764 |
| **4.2.3** | 390,641,100 | **−19.9 %** | 121,405,860 (−39.2 %) | **PV-table bug fix** (correct best-known-move ordering) | +8.7 ± 13.2 | ~1773 |
| **4.3.2** | 368,432,487 | **−5.7 %** | 121,930,761 (+0.4 %) | tapered + Texel-tuned endgame PSTs, queen 900→1000 | +12.6 ± 13.3 | ~1816 |
| **4.3.3** | 375,242,151 | **+1.8 %** | 128,265,386 (+5.2 %) | bishop-pair bonus | +31.3 ± 24.1 | ~1847 |
| **4.3.4** | 350,506,008 | **−6.6 %** | 93,759,041 (−26.9 %) | full-joint MG+EG PST tune | +23.0 ± 12.9 | ~1870 |
| **4.4.0** | 335,919,557 | **−4.2 %** | 98,908,027 (+5.5 %) | PeSTO piece-square tables | +32.6 ± 12.4 | ~1900 |
| **4.4.1** | 335,946,428 | **+0.008 %** | 101,431,971 (+2.6 %) | Repetition fix (§ 12.23) | ≈ +15 (SPRT H1 at 321 games, +42.4 ± 29.4) | **1928 ± 21** |
| **4.5.0** | 336,412,842 | **+0.139 %** | 101,553,277 (+0.1 %) | Complete principal variation ([§ 12.25](roadmap.md#1225-tried--repairing-the-roots-move-choice-after-the-pv-re-search-reverted-twice-444-and-166-elo)) | +1.8 ± 11.6 over 2463 games — **neutral** | ~1928 |
| **4.6.0** (`415a6ac`) | 1,300,002,835 | **+286 %** | 101,626,447 (+0.1 %) | Material-only shortcut only for quiet root moves ([§ 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)) | **+14.8 ± 10.5** over 3000 games | ~1943 |

### Measured but not a release — `4.6.0-king-line` (2026-09-02, shelved 2026-09-03)

The king-line danger term: the three files at and beside the king, classified 0–4 and summed into
a fitted penalty table ([king-safety.md § 4.11](king-safety.md)). **Shelved — SPRT accepted H0
after 437 games at −28.9 ± 28.6 Elo**, so it gets no row in the series above. The numbers below
stay because they are the most instructive part of the attempt: this is what a *cheap* and
*provably neutral* eval term looks like, and it still cost 29 Elo.

| | Nodes @ d8 | NPS | Time |
|---|---:|---:|---:|
| **4.6.0** (baseline) | 1,300,002,835 | 1,251,215 | 17:18 |
| **4.6.0-king-line, factor 0** | **1,300,002,835** | 1,181,726 | 18:20 |
| **4.6.0-king-line** | 572,148,460 | 1,250,540 | 7:38 |

**The middle row is the whole reason this entry exists.** With `kingLinePenaltyFactor = 0` the term
is still computed and simply not applied, so the evaluation is identical to 4.6.0's — and the
signature comes back **bit-identical**. That is the neutrality proof rule 2 asks for: the new code
paths do not perturb the search, and the `Statistics` material-only leaf counter added in the same
change is covered by it too.

**It also makes the cost measurable without a confound.** Baseline and control search the same tree
node for node, so the NPS gap is pure computation: **−5.55 %**. For contrast, the shelved
attack-unit term of 2026-08-31 cost **−21.9 %** on an *unchanged* tree — four times as much for a
weaker screen result. (Caveat: the 1,251,215 comes from a different run on a possibly
differently-loaded machine. Identical trees remove the node-mix confound, not the machine's.)

**The −56 % tree is a change, not an improvement — and after the SPRT, a suspect.** The term lost
29 Elo, and a static score reaching 223 cp is louder than a pawn; a term that loud steers
alpha-beta hard, which is one mechanism that would produce both the halved tree and the lost Elo.
Untested, but it is the only concrete hypothesis the attempt left behind. The original wording of
this paragraph follows, because the caution in it was right for the wrong reason: At depth 8 the term reaches the same depth with
less than half the nodes, and 7:38 against 17:18 is tempting to read as progress. It is not
evidence of strength: fewer nodes means more cutoffs, and whether the *right* moves are cut only an
SPRT can say. Rule 3 applies in full. Note also that the entire effect sits in **one position** —
number 37 goes from 1,129,861,147 nodes to 385,435,203, −65.9 % — so this is not an average over
55 positions but a single dominant case.

**A correction to how this file gets quoted.** `336,412,842` is **4.5.0**, superseded twice over;
the current baseline is 4.6.0's `1,300,002,835`, +286 % above it. That older number is the one
written into `CLAUDE.md` as "the" signature, and quoting it from there produced a wrong reading of
this very measurement on 2026-09-02: the tree looked 70 % *larger* when it is in fact 56 %
*smaller*. Read the baseline out of the table above, never out of memory.

### Measured but not a release — `4.6.0-attack-units` (2026-08-31)

Shelved at **−42.9 ± 33.9 Elo** ([roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo)), so
it gets no row in the series above. Recorded here because its signature is the clearest example
this document has of why the total is the wrong number to read, and because the run cost two
JVM-hours that should not have to be spent twice.

| | 4.6.0 | 4.6.0-attack-units | |
|---|---:|---:|---|
| Nodes @ d8, all 55 | 1,300,002,835 | 451,759,691 | **−65.2 %** |
| Positions 37 + 38 | 1,198,376,388 (92.2 %) | 347,410,684 (76.9 %) | |
| **53 realistic** | **101,626,447** | **104,349,007** | **+2.7 %** |
| Time, 53 realistic | 56,651 ms | 74,480 ms | **+31.5 %** |
| NPS, 53 realistic | 1,793,903 | 1,401,033 | **−21.9 %** |

**A 65 % smaller signature that means nothing, and a 22 % NPS drop that means everything.** Read
as a total, this looks like the largest tree reduction since null-move pruning. It is one
position: number 37 alone falls from 1,130 M to 265 M, which is more than the entire delta. On
the 53 realistic positions the tree is **unchanged within 3 %**, the median position moves by
0.0 %, and 24 of 55 positions get *more* expensive. The king-attack term barely changes what the
search explores; it changes how long each node takes.

**That cost contradicts the estimate the term was built on.** `king-safety.md` § 4.5 measured a
standalone king-zone scan at +118 % of an evaluation and concluded that hanging it on the
existing per-piece walk instead would make it "nearly free" — the reason the build plan insists
on not refactoring it into a separate pass. Nearly free is not what it is: **−21.9 % NPS**, so
about +31.5 % wall clock to reach depth 8 on realistic positions. Under a clock that is depth,
and it compounds with a term that was independently measured wrong.

**Both figures reproduce exactly.** The 4.6.0 baseline was re-measured on the same machine
immediately after the candidate and returned 1,300,002,835 total and 101,626,447 realistic —
identical to the row above, to the digit. The two numbers are therefore comparable and the
signature is deterministic, which is the property the whole instrument rests on.

**Read the 4.6.0 row with its own warning.** +286 % nodes at a fixed depth alongside **+14.8
Elo** is not a contradiction, it is this table's third demonstration that the signature answers a
different question than the one being asked. The extra work sits in capture subtrees, which
alpha-beta prunes once they prove worse: at a fixed depth every one of them is paid for, under a
clock most are not. The variant lost **0.13 plies** in play, against 0.29 for a variant with the
same tree that measured −47.6 Elo. When comparing releases whose extra work is unevenly
distributed, the plies lost under the clock is the comparable quantity and the node count is not.

**The `~CCRL` column is propagated except for one row.** 4.4.1 carries a *measured* absolute rating — 1928 ± 21 from the 2026-08-17 re-anchor, 2000 games against five externally rated engines (see [ELO measurement § 7](myChess-ELO-measurement.md#the-v441-re-anchor--measured-2026-08-17)). Every other value in that column is carried forward from per-version SPRT deltas. The propagated chain had predicted ~1915 for this row, so the deltas proved well calibrated over the ~500 Elo since the previous direct measurement.

Across the whole series, 4.4.0 needs **2.36× fewer nodes than 3.5.2** for the
same depth (−57.6 %) while playing roughly **460 Elo stronger**.

Time and NPS for the same runs — **informative only**. Rows 3.5.2 to 4.4.1 were measured
2026-08-09 (4.4.0: 2026-08-11); 4.5.0 and 4.6.0 on 2026-08-27, when their archives were
backfilled. Same machine throughout: an Apple M1 Pro (10 cores), macOS 15.6.1, Corretto
JDK 25.0.2, each engine with `-Xms256m -Xmx256m -XX:+UseSerialGC`. Two measurement dates in
one table is exactly the mixing rule 4 exists to make harmless — these columns are never
compared, only recorded:

| Version | Time @ d8 | NPS |
|---|---:|---:|
| 3.5.2 | 4:42 | 2,805,663 |
| 3.6.0 | 4:54 | 2,826,432 |
| 4.0.7 | 2:00 | 3,077,555 |
| 4.1.0 | 1:03 | 3,052,011 |
| 4.2.1 | 3:46 | 2,152,066 |
| 4.2.3 | 3:45 | 1,735,904 |
| 4.3.2 | 3:21 | 1,829,911 |
| 4.3.3 | 3:26 | 1,814,411 |
| 4.3.4 | 3:06 | 1,875,577 |
| 4.4.0 | 3:00 | 1,861,173 |
| 4.4.1 | 3:00 | 1,861,394 |
| 4.5.0 | 2:57 | 1,900,573 |
| 4.6.0 | **17:18** | 1,251,215 |
| 4.6.0-king-line (shelved, −28.9 Elo) | 7:38 | 1,250,540 |
| 4.6.0-king-line, factor 0 | 18:20 | 1,181,726 |

The NPS decline from ~3.0 M to ~1.9 M is the cost of the richer evaluation and
the deeper quiescence search: fewer nodes per second, but each node is worth
more. It is not a performance regression, and — being a time-derived figure — it
is not a number to optimize against.

**4.6.0's 17:18 is not the engine having become six times slower**, and its NPS
drop to 1.25 M is not a throughput regression either. Both are the two artificial
stress positions: position 37 alone burns **15:35** of that wall clock and
position 38 another 0:47. The 53 realistic positions together take **0:55** — the
whole rest of the suite costs less than a minute, and 90 % of a seventeen-minute
benchmark is spent on one position that cannot occur in a game. This row is why
policy rule 1's cost argument had to be rewritten.

### A pure speed change, measured relatively — `king-field-tracking` (2026-09-05)

**`Board` now carries both king squares instead of searching for them**, so
`isKingChecked` and `canCaptureOpposingKing` read a field where they used to run a
linear scan over a1..h8. Behavior is unchanged by construction, which makes the
signature the entry ticket and the wall clock the only number the change can move.

**Signature: 1,300,002,835 in all six runs**, master and candidate alike — the same
value as 4.6.0, so the three commits master gained since that release (the
material-only counter, the TT EXACT-bound guard, the extended bench output) are
covered by this as neutral too.

**The rows below are not table rows and must not be quoted as absolute figures.**
They were taken while a 6000-game match held four cores, so every number here sits
below what the same builds would produce idle — the candidate's 1,603,731 against
4.6.0's archived 1,251,215 compares nothing. What survives the load is the
*difference*, because both builds met the same load: six runs alternating
`A B B A A B`, a sequence chosen so neither build systematically draws the earlier
slots if the match's load drifts (slot sums 10 against 11; strict alternation would
give 9 against 12).

| | runs (NPS over 54 positions) | mean | SD |
|---|---|---:|---:|
| master `6b3f480` | 1,573,404 / 1,572,881 / 1,588,728 | 1,578,338 | 0.57 % |
| + tracking `505d1f4` | 1,607,886 / 1,594,326 / 1,608,980 | **1,603,731** | 0.51 % |

**+1.61 %**, SE of the difference 0.44 pp, t = 3.62 — and the raw values are
**disjoint**: the fastest master run is below the slowest candidate run, which under
the null has probability 1/20 at three against three.

**The comparator is the 54-position figure, not the total, and that is the
methodological point of this entry.** On total NPS the same six runs give +0.92 % at
t = 1.28 with overlapping samples — indistinguishable from noise. The total is 86.9 %
one position whose seventeen minutes are exposed to load drift end to end (master's
SD 1.21 %), while the 54 short positions average it out (SD 0.57 %). A real effect
was resolvable in one measure and invisible in the other. Rule 4 says never assert on
time or NPS; this adds *which* time, when a relative reading is what is wanted.

In Elo this is small and known to be small. Section 4.10 of
[`king-safety.md`](king-safety.md) prices −21.9 % NPS at **up to** 52 Elo, an
explicit upper bound; 1.61 % is therefore at most ~3.4 Elo and on the textbook
60-per-doubling rate about 1.4. No match anyone would run resolves that — a 6000-game
fixed-N reads ±12 — which is why this change was measured on the bench and shipped on
the bench, without a match.

**Open: a control measurement on an idle machine.** The relative result is what the
merge decision needed and it is in hand, but the absolute pair belongs in the table
above and cannot be taken under load. Re-run both builds once the machine is free and
add the row then; until it exists, `4.6.0`'s 1,251,215 / 17:18 remains the last
absolute reading in this document.

---

## 3. Depth 9 — from 4.3.4 onward

Recorded from 4.3.4 on, because search techniques scale with depth: late move
reductions, PVS, and history heuristics save proportionally more the deeper the
search runs, so a single-depth table would understate exactly the work planned
next. Older releases are not measured at depth 9 — they predate the TT and NMP
and are historically closed.

| Version | Nodes @ d9 | Nodes @ d8 | d9 / d8 | 53 realistic @ d9 | d9 / d8, realistic |
|---|---:|---:|---:|---:|---:|
| **4.3.4** | 916,947,170 | 350,506,008 | **2.62** | 334,123,988 | **3.56** |
| **4.4.0** | 920,132,868 | 335,919,557 | **2.74** | 366,672,809 | **3.71** |
| **4.4.1** | 918,718,652 | 335,946,428 | **2.73** | 372,117,621 | **3.67** |
| **4.5.0** | 919,377,788 | 336,412,842 | **2.73** | 372,430,743 | **3.67** |
| **4.6.0** (`415a6ac`) | 2,352,454,034 | 1,300,002,835 | **1.81** | 372,716,472 | **3.67** |

**The 4.6.0 row breaks the comparability of this column, and must not be read as a search
improvement.** Its 1.81 is the lowest value in the table by a wide margin, and none of it comes
from better search. The material-only shortcut change inflates the depth-8 tree by 3.86× and the
depth-9 tree by only 2.56×, because the cost of the accurate evaluation is front-loaded and falls
away relatively with depth. A ratio between two differently inflated numbers drops mechanically.

That is no longer only an argument. The rightmost column measures it: on the 53 realistic
positions 4.6.0's ratio is **3.67, unchanged from 4.4.1 and 4.5.0 to two decimals**. The whole
collapse from 2.73 to 1.81 lives in the two artificial stress positions, and the search's
effective branching factor did not move — see [§ 4](#4-what-the-numbers-say).

The inverse reading is just as wrong. "One more ply now costs 1.81× instead of 2.73×" sounds like
good news, and against a fixed node budget it predicts a loss of more than a whole ply; the
measured loss under the clock is **0.13 plies**. Whenever a release changes what an evaluation
costs *as a function of depth*, this ratio stops being comparable across the boundary, and the
plies lost under the clock is the quantity that still tracks strength
(see [roadmap § 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)).

The **d9/d8 ratio is the effective branching factor** and the most interesting
single number here. At 2.62–2.74 it is far below the ~5.9 (√35) that perfect move
ordering would give in a plain alpha-beta search: transposition-table hits and
null-move cutoffs remove whole subtrees, and the previous iteration's principal
variation orders the next one. Tracking this ratio across future releases
measures search quality more directly than either absolute count does.

**Read the realistic column for that, not the full one.** The 2.62–2.74 band is
depressed by the two artificial positions, which are saturated at depth 8 and so
gain proportionally less from the extra ply. On the 53 realistic positions the
factor is **3.56–3.71** — a third higher, and the honest figure for how well the
search actually prunes.

**4.4.0 raised it, from 2.62 to 2.74 — and that is not a regression.** The PeSTO
tables shrank the depth-8 tree by 4.2 % while leaving depth 9 essentially unchanged
(+0.3 %), so the ratio between them grew. The pattern is a *fading* ordering benefit
rather than a worse search: the sharper evaluation separates moves more clearly, which
helps most where little other ordering information exists yet, and matters less once
the transposition table, the previous iteration's PV, and the killer moves dominate.
The same effect is far more dramatic at very shallow depth — `NodeCountTest` records
the depth-2 signature falling **140 → 82 nodes (−41 %)** with the same change. Read
the ratio as a search-quality indicator only when the evaluation is held fixed;
across an evaluation change it also moves for this reason.

---

## 4. What the numbers say

**Search architecture moves the signature by factors; evaluation tuning moves it
by percent.** The TT (−55 %), NMP (−48 %), and the all-captures quiescence
(+151 %) are the only changes that shifted the count by more than a fifth. Every
evaluation change in the 4.2.3–4.3.4 range stayed within ±7 %, including the
+31.3 Elo bishop-pair bonus at +1.8 %.

**Node count and Elo are largely uncorrelated.** The two largest measured jumps
in project history pull in *opposite* directions: NMP (+76 Elo) cut nodes almost
in half, the quiescence upgrade (+40.6 Elo) more than doubled them. Anyone
reading this table as a strength proxy will draw the wrong conclusion.

**A correctness fix can be almost invisible here — and the "almost" is the point.**
4.4.1 changes the signature by **+0.008 %** at depth 8 and **−0.15 %** at depth 9,
the smallest movement in the table. That is expected: the repetition fix only
alters lines in which a position recurs, and the 55 bench positions are tactical
and middlegame ones where that is rare. What matters is that the numbers are *not
identical*. Bench is deterministic, so any difference at all is a real behavioral
change — a zero delta would have meant the new code path never executes on this
suite, and the run would have proved nothing. Note also that the two depths move in
opposite directions, which is a reminder that "fewer nodes" is not the objective:
the fix cuts repeating subtrees off early but also makes the search explore
alternatives it previously never reached.

**An ordering fix is visible as a pure node saving.** 4.2.3 fixed the PV table,
changing neither search depth nor evaluation, and the signature dropped 19.9 %.
A correct principal variation means correct best-known-move ordering, hence
earlier cutoffs — the textbook shape of an ordering improvement.

**Better evaluation orders moves better.** The 4.2.3 → 4.3.4 evaluation series
lowered the count by 10.3 % in total without touching search code: better
piece-square tables rank moves better, which produces earlier cutoffs. **4.4.0
confirms it a fourth time** — the PeSTO tables took another 4.2 % off depth 8,
again with no search change, bringing the 4.2.3 → 4.4.0 total to **−14.0 %**.

That effect is strongly depth-dependent, which is worth keeping in mind before
reading a node count as a search-quality verdict. The same 4.4.0 change removes
**41 %** of the depth-2 tree (`NodeCountTest`: 140 → 82 nodes), 4.2 % at depth 8,
and **nothing measurable at depth 9** (+0.3 %). A sharper evaluation helps exactly
where no other ordering information exists yet; deeper down, the transposition
table, the previous iteration's principal variation, and the killer moves have
already done the work.

### Where the nodes actually are — two positions carry the signature

Every reading above is a reading of the **total**, and the total is composed very
unevenly. The suite's 55 positions include exactly two that are not chess: the
pawnless, figure-dense stress positions at index 37 and 38, inherited from
Stockfish's own bench, with 24 and 22 non-king pieces against 11 to 16 pawns in
every other position. The separation is absolute — there is no borderline case.

Those two have grown from a quarter of the depth-8 signature to almost all of it:

| Version | Nodes @ d8 | Positions 37 + 38 | Their share | 53 realistic positions | Δ vs prev. |
|---|---:|---:|---:|---:|---:|
| **3.5.2** | 791,340,172 | 247,629,207 | 31.3 % | 543,710,965 | — |
| **3.6.0** | 832,797,076 | 252,768,033 | 30.4 % | 580,029,043 | +6.7 % |
| **4.0.7** | 370,196,066 | 95,170,355 | 25.7 % | 275,025,711 | −52.6 % |
| **4.1.0** | 194,309,372 | 32,322,669 | **16.6 %** | 161,986,703 | −41.1 % |
| **4.2.1** | 487,757,233 | 288,125,402 | 59.1 % | 199,631,831 | +23.2 % |
| **4.2.3** | 390,641,100 | 269,235,240 | 68.9 % | 121,405,860 | −39.2 % |
| **4.3.2** | 368,432,487 | 246,501,726 | 66.9 % | 121,930,761 | +0.4 % |
| **4.3.3** | 375,242,151 | 246,976,765 | 65.8 % | 128,265,386 | +5.2 % |
| **4.3.4** | 350,506,008 | 256,746,967 | 73.3 % | 93,759,041 | −26.9 % |
| **4.4.0** | 335,919,557 | 237,011,530 | 70.6 % | 98,908,027 | +5.5 % |
| **4.4.1** | 335,946,428 | 234,514,457 | 69.8 % | 101,431,971 | +2.6 % |
| **4.5.0** | 336,412,842 | 234,859,565 | 69.8 % | 101,553,277 | +0.1 % |
| **4.6.0** | 1,300,002,835 | 1,198,376,388 | **92.2 %** | 101,626,447 | +0.1 % |

**The consequence is blunt: conclusions drawn from the total across this series
were dominated by two positions that cannot occur in a game.** From 4.1.0 to
4.5.0 the total rose 73 % while the realistic positions got **37 % cheaper**
(162 M → 102 M). The two series do not merely differ in scale, they point in
opposite directions.

**The single largest jump in this table is mostly not what it appears to be.**
4.2.1 — all-captures quiescence with MVV-LVA and SEE — shows **+151 %** on the
total but only **+23.2 %** on the realistic positions. In a pawnless position
where nearly every move is a capture, an all-captures quiescence has nothing to
prune; the change looked six times more expensive than it was where games are
actually played. Its share jumps from 16.6 % to 59.1 % in that one release and
never comes back down.

**The identity of the most expensive position is not stable, which is why nothing
here is keyed to an index.** Through 4.1.0 the largest single position was **38**;
from 4.2.1 onward it is **37**. A diagnostic hard-wired to one index would have
reported the wrong position for four archived versions without ever failing.
`BenchResult.largestPosition()` therefore reports the maximum, and this table
excludes the *pair*, which is the quantity that stays comparable across the whole
series.

**The reduced figure is a healthy aggregate, unlike the total.** Among the 53
realistic positions the largest single contributor is 7.5 % and the top five are
5 to 8 % each (measured on 4.4.1 and 4.5.0), so no one position can move it much.
That is what makes it readable as a cost figure rather than as one position's cost.

**But it is emphatically not a neutrality oracle, and 4.6.0 is the proof.** Its
reduced total moved **+0.1 %** — 101,553,277 to 101,626,447 — for a release whose
full signature tripled and which measured +14.8 Elo. Read as a sum, the realistic
positions would have declared that release neutral. Read one at a time they catch
it easily: 28 of the 53 diverge. The reduced number answers *"what does the search
cost where games are actually played"*; the question *"did anything change at
all"* is answered by the per-position diff of § 7 over the full 55, and by nothing
else.

That flatness is itself the most striking number in this section. Across 4.4.1,
4.5.0 and 4.6.0 — a repetition fix, a principal-variation correctness release and
an evaluation-regime change worth +14.8 Elo — the realistic positions cost
101.4, 101.6 and 101.6 million nodes at depth 8. Three consecutive releases,
one part in a thousand apart, while the headline signature went from 336 million
to 1.3 billion.

#### It also corrects the effective branching factor, and explains 4.6.0's collapse

§ 3 calls the d9/d8 ratio the most interesting single number here and puts it at
2.62–2.74, far below the ≈5.9 that √35 would give. On the realistic positions it
is **3.67**. The two artificial positions are already saturated at depth 8 — one
more ply adds proportionally less there than in a normal position — so they pull
the published figure down by roughly a third. The search prunes noticeably less
well than this document has been claiming, which strengthens rather than weakens
the case for the § 12.3 search cluster.

**And it settles what happened to 4.6.0's ratio.** § 3 warns that its 1.81 must
not be read as a search improvement and calls the drop mechanical. That is now
measured rather than argued: on the realistic positions 4.6.0's ratio is
**3.67 — unchanged from 4.4.1 and 4.5.0 to two decimals**, while the full-suite
figure fell from 2.73 to 1.81. The entire collapse is two positions. The effective
branching factor of the search did not move at all.

The same holds for the depth-9 cost itself: 372.1, 372.4 and 372.7 million nodes
over 4.4.1, 4.5.0 and 4.6.0 — the identical one-part-in-a-thousand flatness the
depth-8 column shows, from an independent measurement.

#### What this does not justify

**The artificial positions are not privileged divergence detectors, and the
earlier argument that they were does not survive measurement.** Comparing
consecutive archives position by position, real changes move nearly the whole
suite: 45 of 55 positions for 4.4.0 → 4.4.1, and 53 of 55 for both
4.3.3 → 4.3.4 and 4.4.1 → 4.5.0. Position 37 was outright *blind* to the
repetition fix while ten realistic positions caught it. On this evidence the two
carry no sensitivity that the other 53 do not, and their 70 to 90 % share is cost
without a matching detection benefit.

**4.6.0 was the fairest possible test of the opposite view, and it did not rescue
it.** That release is the only one in the series whose change is aimed squarely at
capture subtrees — precisely where a pawnless, figure-dense position should be the
uniquely sensitive probe. It moves only **30 of 55** positions, the narrowest
divergence in the table, and both artificial positions are among them. But so are
**28 realistic ones**, so the change was never at risk of going unnoticed. What
the two artificial positions contribute is not detection but magnitude: position
37 alone grows from 150,230,710 to 1,129,861,147 nodes (**+652 %**) and accounts
for essentially the entire +286 % headline, while position 38 gets 19 % *cheaper*
over the same change. They are where the cost shows up, not where the change is
found.

**They stay in the suite anyway, and the reason is comparability alone.** Removing
them would silently redefine the signature and devalue twelve archived versions,
while the reduced column recovers every analysis above without discarding
anything. Where the runtime becomes an obstacle, the honest shortcut is to run
the 53 realistic positions for a quick intermediate check — **55 seconds against
seventeen minutes**, measured on the 4.6.0 archive — and keep the full 55 for
anything that goes into this table. Compare them per position, never as a sum,
for the reason given above.

Reproduce every number in this section from the archives, for example:

```sh
LC_ALL=C awk '/\/55  nodes/ {gsub(/,/, "", $3); n[$1] = $3}
     END { for (k in n) { t += n[k]; if (k ~ /^3[78]\//) a += n[k] }
           printf "total %d  artificial %d  reduced %d  share %.1f %%\n", t, a, t - a, 100 * a / t }' \
    test-results/bench/4.4.1-d8.txt
```

---

## 5. Method

Every build was driven through its **UCI front-end** by a small harness rather
than through the `bench` command, for one reason: `bench` only arrived in
**4.2.2**, so 3.5.2, 3.6.0, 4.0.7, 4.1.0, and 4.2.1 do not have it. Driving all
builds the same way keeps the series internally comparable, and it measures the
released artifacts as they are — no old build had to be modified or rebuilt.

The harness reproduces `Bench.run()` semantics exactly:

- **`ucinewgame` before every position.** `UciHandler.handleNewGame()` performs
  `tt.clear()` and `ChessEngine.resetIterationTimings()` — precisely the
  per-position isolation `Bench` establishes for each FEN, so counts are
  order-independent. This has held since the transposition table was introduced
  (commit `175e834`, present in every tag from v4.0.0), and 3.5.2 / 3.6.0 have no
  transposition table at all.
- **`go depth N` runs with an infinite time budget** (`UciHandler`'s
  `INFINITE_MILLIS` for depth-only searches), so depth is the only bound and the
  skip-hopeless-iteration heuristic can never fire. Without this the counts would
  be machine-speed dependent.
- **No opening book.** `db/openings.db` is absent, so every position is really
  searched instead of being answered from the book.
- **Self-check per position:** the harness compares the depth reported on the
  final `info` line against the requested depth and writes a `WARNING` line on
  any mismatch, which would indicate a truncated — and therefore
  non-comparable — search. **No run produced a warning.**

**Method validated by cross-checking the same artifact two ways.** The
`versions/4.3.4` jar was measured with its own built-in `bench 8` *and* with the
UCI harness:

```
built-in `bench 8` : 350,506,008 nodes
UCI harness (d8)   : 350,506,008 nodes   -> identical
```

Because the two agree to the node, the harness numbers for the five pre-4.2.2
releases are directly comparable to `bench` output.

One honest caveat on the time column: the harness times each position around the
UCI round-trip, so its totals run slightly below the built-in command's internal
timing (187.0 s vs 190.0 s for the same 4.3.4 run, ~1.6 %). The node counts are
unaffected, and time is informative only.

### Reproducing a row

```sh
mvn package -DskipTests
printf 'bench\nquit\n' | java -cp "target/my-chess-<version>.jar:target/dependency/*" \
    org.michaelfl.mychess.MyChessMain     # depth 8 = Bench.DEFAULT_DEPTH
printf 'bench 9\nquit\n' | java -cp "target/my-chess-<version>.jar:target/dependency/*" \
    org.michaelfl.mychess.MyChessMain     # depth 9
```

`bench` prints its numbers with `Locale.ROOT`, so the grouping separator is a
comma on every machine and the output is diffable regardless of system locale.

---

## 6. Policy for future releases

1. **Measure every release, not only those that touch search or evaluation.** The
   value of a signature is that an *unchanged* number proves neutrality. Skipping
   a "tooling only" release forfeits exactly the case worth catching — an
   unintended behavioral change. (Historical precedent: 4.2.2 was declared "no
   runtime strength change" — asserted, never demonstrated.)

   **The rule stands, but its old justification does not.** This entry used to
   read "at three minutes for depth 8, the cost is negligible". That was true
   through v4.4.1 and is no longer: v4.6.0 takes **17 minutes at depth 8 and 29
   at depth 9**, so a full release measurement is about **47 minutes** rather than
   the ten it once was. The rule survives on a different argument — 47 minutes is
   still an order of magnitude below the SPRT that accompanies any real change,
   and it buys a proof rather than an estimate.

   Two things follow. Do **not** read the new cost as the engine having become
   slow: essentially all of it is two positions (see § 4), and across 4.4.1,
   4.5.0 and 4.6.0 the other 53 stayed flat at 101.4, 101.6 and 101.6 million.

   And a cheap intermediate check on the 53 realistic positions — **55 seconds
   against seventeen minutes** — must be run as a **per-position comparison, never
   as a reduced total.** The distinction is not pedantic: 4.6.0 moved the reduced
   total by **+0.1 %**, so a sum over the realistic positions would have called
   the release neutral while the full signature tripled. The same 53 positions
   caught it perfectly well one at a time — 28 of them diverge — which is why the
   diff recipe in § 7 is the instrument and the sum is not. What gets **archived
   and tabulated stays the full 55**, because a signature is only worth anything
   next to signatures measured the same way, and the series reaches back to 3.5.2.
2. **Record both depth 8 and depth 9** from 4.3.4 onward, and track the d9/d8
   ratio.
3. **Archive the per-position output**, not just the totals (see below).
4. **Never assert on time or NPS.** Record them with machine and date, or leave
   them out.
5. **Record the commit the number was measured at, in the same cell as the
   version.** A measurement cannot be attributed to a code state after the fact.
   This was learned on 2026-08-27, when the 4.6.0 depth-8 figure had already been
   written into this table, into [version history](version-history.md) and into the
   `v4.6.0` tag message before anyone asked which build produced it — and the
   commit timestamp could not answer, because it records when `git commit` was
   typed and not when the change was in the tree. The depth-9 figure was
   attributable only by accident, because no commit had touched `src/main` since.
   A jar can be checked against its bytecode afterwards; a measurement has no
   equivalent. Rows before 4.6.0 carry no hash and none should be invented for
   them: the release tag is not evidence that the bench ran at that commit, and
   filling it in would assert precisely what was never recorded.

   **Measure from the version jar, not from `target/classes`.** The latter is a
   working directory that can change under a running process: Java loads classes
   lazily, so a `mvn compile` during a bench run can hand new bytecode to a JVM
   that is already minutes in. That happened on 2026-08-27 and cost the run. The
   jar under `versions/<v>/` is sealed once copied and checkable against its own
   bytecode, which makes the attribution structural rather than a matter of
   timestamps:

   ```sh
   cd versions/4.6.0 && printf 'bench 8\nq\n' \
       | java -cp "my-chess-4.6.0.jar:lib/*" org.michaelfl.mychess.MyChessMain
   ```
6. **An unchanged signature proves logical neutrality, never affordability — and a
   changed one does not measure cost.** Three distinct failures of this instrument were
   learned in one week, each at the price of a measurement. The third: the signature
   **overstates the cost of any change whose extra work sits in prunable branches**,
   because a fixed depth charges for subtrees a clock would abandon. v4.6.0 is +286 %
   nodes and +14.8 Elo (see [§ 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)).
   The first two: The
   two are different claims and the difference is not academic: the full-window PV
   re-search of 2026-08-21 moved the depth-8 signature by **+93 nodes on 336
   million** — +0.00003 % — and cost **−44.4 ± 17.2 Elo** over 1180 games at
   `tc=40/60`. Both numbers are correct. `bench` clears the table before every
   position and searches to a fixed depth, so a change whose cost depends on the
   table being *warm*, and which shows up as wall-clock rather than as nodes, is
   invisible to it **by construction**. Whenever a change adds work to such a path,
   the signature is the wrong instrument and only a time-controlled match answers
   the question. **Nor does an unchanged signature establish correctness**: the same
   section records a move-selection bug that corrupted nearly every real move and
   showed up as −320 nodes on 336 million, because the path it broke almost never
   runs with a table cleared before every position. A signature speaks only about
   the paths `bench` exercises. The sharpest evidence for that came from this very
   table: the depth-8 signature is **336,412,842 both with and without** the
   descent over the root move list — byte-identical, because with the table
   cleared before every position that code never ran once on these 55 positions,
   while in play it corrupted nearly every move (see
   [roadmap § 12.25](roadmap.md#1225-tried--repairing-the-roots-move-choice-after-the-pv-re-search-reverted-twice-444-and-166-elo)).

The Chess960 suite (10 positions, `bench 960`) is deliberately excluded from this
table: comparability across the older releases is not established. It can be
added later as a separate table.

---

## 7. Per-position archive

Totals alone only say *that* something changed. Localizing it requires the
per-position comparison — one diverging position points at a specific bug, all
positions diverging points at a global evaluation or ordering change. Full
outputs are therefore archived per run:

```
test-results/bench/<version>-d<depth>.txt
```

To find out where two builds diverge, compare the node counts **without the time
column** — every line carries a wall-clock time, so a plain `diff` reports all 55
positions as changed even between two runs of the same build:

```sh
nodes() { awk '/\/55  nodes/ {print $1, $3, $NF}' "$1"; }
diff <(nodes test-results/bench/4.3.3-d8.txt) <(nodes test-results/bench/4.3.4-d8.txt)
```

This matters most for the instrument's primary use. A neutral refactor must produce
**no output at all** here; a plain `diff` produces 114 changed lines for it, so in
that form the archive cannot express neutrality — the one thing the signature
exists to prove. It also removes false positives in the other direction: between
4.4.1 and 4.5.0, 53 positions genuinely diverge while the plain `diff` implicates
all 55.

A long diff is not itself a problem. Most real changes move nearly every position
(30 to 53 of 55 across the four transitions checked in § 4); a *short* one is the
interesting case, because it localizes the change to a handful of positions.

These files are committed alongside this document, so the per-position detail of
every measured release is available from a fresh clone — not just the totals
tabulated above.

### The archive is now a by-product of the run, not a separate step

**It was missed twice, for 4.5.0 and 4.6.0.** Both releases were measured — their
totals are in the tables above and reproduce exactly from the sealed jars — but
the per-position output was never written to disk, so rule 1 was followed while
rule 3 was not. The mechanism is worth naming, because it is not forgetfulness:
the total is what gets transcribed into the table, so reading it off the screen
completes the visible task, while archiving is an extra redirect whose payoff
arrives months later. Nothing enforced it.

The cost landed immediately. The concentration finding in § 4 came *out of* these
archives — it is only computable retroactively because twelve versions had been
archived — and the two runs whose archives were skipped were exactly the two
versions in which the concentration exploded. Both were backfilled on 2026-08-27
from `versions/4.5.0` and `versions/4.6.0`, which is possible only because the
sealed jars still exist; had they been overwritten, those rows would have been
permanently unarchivable.

**The structural fix is that `bench` now streams its per-position lines during the
run** instead of collecting them and printing at the end. Producing the archive is
therefore no longer a step anyone has to remember:

```sh
printf 'bench 8\nq\n' | java -cp "my-chess-4.7.0.jar:lib/*" \
    org.michaelfl.mychess.MyChessMain | tee test-results/bench/4.7.0-d8.txt
```

This is also why the progress line kept the archive's existing column layout
(`n/total  nodes N  time T ms  fen`) rather than a more readable one with running
totals: a by-product is only worth having if it diffs against the fourteen files
that came before it.

Incidentally, the first cross-check already paid for itself: the depth-8 and
depth-9 signatures of the current `master` are identical to those of the released
`versions/4.3.4` artifact, which proves that the post-4.3.4 refactor work
(`decideIteration` extraction, the `volatile` switch, the `Locale.ROOT` output
change) left search behavior untouched.
