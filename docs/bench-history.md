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

**Deltas span everything between two rows.** These releases are checkpoints, not
consecutive versions. The change from one row to the next bundles every release
in between, so the attribution column names the dominant feature in that span,
not a sole cause.

---

## 2. Depth 8 — the full series

Suite: `standard` (49 Stockfish benchmark positions + 6 myChess middlegames = 55).
All ten runs completed every position at the requested depth; no run was
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

Across the whole series, 4.3.4 needs **2.26× fewer nodes than 3.5.2** for the
same depth (−55.7 %) while playing roughly **430 Elo stronger**.

Time and NPS for the same runs — **informative only**, all measured
2026-08-09 on an Apple M1 Pro (10 cores), macOS 15.6.1, Corretto JDK 25.0.2,
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

The **d9/d8 ratio is the effective branching factor** and the most interesting
single number here. At 2.62 it is far below the ~5.9 (√35) that perfect move
ordering would give in a plain alpha-beta search: transposition-table hits and
null-move cutoffs remove whole subtrees, and the previous iteration's principal
variation orders the next one. Tracking this ratio across future releases
measures search quality more directly than either absolute count does.

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

**An ordering fix is visible as a pure node saving.** 4.2.3 fixed the PV table,
changing neither search depth nor evaluation, and the signature dropped 19.9 %.
A correct principal variation means correct best-known-move ordering, hence
earlier cutoffs — the textbook shape of an ordering improvement.

**Better evaluation orders moves better.** The 4.2.3 → 4.3.4 evaluation series
lowered the count by 10.3 % in total without touching search code: better
piece-square tables rank moves better, which produces earlier cutoffs.

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
