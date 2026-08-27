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
All twelve runs completed every position at the requested depth; no run was
time-truncated.

| Version | Nodes @ d8 | Δ nodes vs prev. row | Dominant change in span | Δ Elo (measured) | ~CCRL |
|---|---:|---:|---|---|---|
| **3.5.2** | 791,340,172 | — (baseline) | — | −5.6 ± 21.3 (noise) | ~1441 |
| **3.6.0** | 832,797,076 | **+5.2 %** | eval: pawn structure / weights | +28.1 ± 20.5 | ~1469 |
| **4.0.7** | 370,196,066 | **−55.5 %** | **transposition table** (4.0.0) + en-passant Zobrist fix | +92.7 for the TT itself | ~1587 |
| **4.1.0** | 194,309,372 | **−47.5 %** | **null-move pruning** | +76.0 ± 10.1 | ~1663 |
| **4.2.1** | 487,757,233 | **+151.0 %** | **quiescence: all captures + MVV-LVA + SEE** | +40.6 ± 9.4 | ~1764 |
| **4.2.3** | 390,641,100 | **−19.9 %** | **PV-table bug fix** (correct best-known-move ordering) | +8.7 ± 13.2 | ~1773 |
| **4.3.2** | 368,432,487 | **−5.7 %** | tapered + Texel-tuned endgame PSTs, queen 900→1000 | +12.6 ± 13.3 | ~1816 |
| **4.3.3** | 375,242,151 | **+1.8 %** | bishop-pair bonus | +31.3 ± 24.1 | ~1847 |
| **4.3.4** | 350,506,008 | **−6.6 %** | full-joint MG+EG PST tune | +23.0 ± 12.9 | ~1870 |
| **4.4.0** | 335,919,557 | **−4.2 %** | PeSTO piece-square tables | +32.6 ± 12.4 | ~1900 |
| **4.4.1** | 335,946,428 | **+0.008 %** | Repetition fix (§ 12.23) | ≈ +15 (SPRT H1 at 321 games, +42.4 ± 29.4) | **1928 ± 21** |
| **4.5.0** | 336,412,842 | **+0.139 %** | Complete principal variation ([§ 12.25](roadmap.md#1225-tried--repairing-the-roots-move-choice-after-the-pv-re-search-reverted-twice-444-and-166-elo)) | +1.8 ± 11.6 over 2463 games — **neutral** | ~1928 |
| **4.6.0** | 1,300,002,835 | **+286 %** | Material-only shortcut only for quiet root moves ([§ 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)) | **+14.8 ± 10.5** over 3000 games | ~1943 |

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

Time and NPS for the same runs — **informative only**, all measured
2026-08-09 (4.4.0: 2026-08-11) on an Apple M1 Pro (10 cores), macOS 15.6.1, Corretto JDK 25.0.2,
each engine with `-Xms256m -Xmx256m -XX:+UseSerialGC`:

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

The NPS decline from ~3.0 M to ~1.9 M is the cost of the richer evaluation and
the deeper quiescence search: fewer nodes per second, but each node is worth
more. It is not a performance regression, and — being a time-derived figure — it
is not a number to optimize against.

---

## 3. Depth 9 — from 4.3.4 onward

Recorded from 4.3.4 on, because search techniques scale with depth: late move
reductions, PVS, and history heuristics save proportionally more the deeper the
search runs, so a single-depth table would understate exactly the work planned
next. Older releases are not measured at depth 9 — they predate the TT and NMP
and are historically closed.

| Version | Nodes @ d9 | Nodes @ d8 | d9 / d8 |
|---|---:|---:|---:|
| **4.3.4** | 916,947,170 | 350,506,008 | **2.62** |
| **4.4.0** | 920,132,868 | 335,919,557 | **2.74** |
| **4.4.1** | 918,718,652 | 335,946,428 | **2.73** |
| **4.5.0** | 919,377,788 | 336,412,842 | **2.73** |
| **4.6.0** | 2,352,454,034 | 1,300,002,835 | **1.81** |

**The 4.6.0 row breaks the comparability of this column, and must not be read as a search
improvement.** Its 1.81 is the lowest value in the table by a wide margin, and none of it comes
from better search. The material-only shortcut change inflates the depth-8 tree by 3.86× and the
depth-9 tree by only 2.56×, because the cost of the accurate evaluation is front-loaded and falls
away relatively with depth. A ratio between two differently inflated numbers drops mechanically.

The inverse reading is just as wrong. "One more ply now costs 1.81× instead of 2.73×" sounds like
good news, and against a fixed node budget it predicts a loss of more than a whole ply; the
measured loss under the clock is **0.13 plies**. Whenever a release changes what an evaluation
costs *as a function of depth*, this ratio stops being comparable across the boundary and the
plies lost under the clock is the quantity that is
(see [roadmap § 12.26](roadmap.md#1226-material-only-shortcut-only-for-quiet-root-moves--done-148-elo-v460)).

The **d9/d8 ratio is the effective branching factor** and the most interesting
single number here. At 2.62–2.74 it is far below the ~5.9 (√35) that perfect move
ordering would give in a plain alpha-beta search: transposition-table hits and
null-move cutoffs remove whole subtrees, and the previous iteration's principal
variation orders the next one. Tracking this ratio across future releases
measures search quality more directly than either absolute count does.

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
   unintended behavioral change. At three minutes for depth 8, the cost is
   negligible against a documented "identical to the previous release, hence
   provably neutral" line. (Historical precedent: 4.2.2 was declared "no runtime
   strength change" — asserted, never demonstrated.)
2. **Record both depth 8 and depth 9** from 4.3.4 onward, and track the d9/d8
   ratio.
3. **Archive the per-position output**, not just the totals (see below).
4. **Never assert on time or NPS.** Record them with machine and date, or leave
   them out.
5. **An unchanged signature proves logical neutrality, never affordability — and a
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

To find out where two builds diverge:

```sh
diff test-results/bench/4.3.3-d8.txt test-results/bench/4.3.4-d8.txt
```

These files are committed alongside this document, so the per-position detail of
every measured release is available from a fresh clone — not just the totals
tabulated above.

Incidentally, the first cross-check already paid for itself: the depth-8 and
depth-9 signatures of the current `master` are identical to those of the released
`versions/4.3.4` artifact, which proves that the post-4.3.4 refactor work
(`decideIteration` extraction, the `volatile` switch, the `Locale.ROOT` output
change) left search behavior untouched.
