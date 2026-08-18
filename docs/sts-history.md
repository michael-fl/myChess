# STS history — the per-theme evaluation diagnostic

The [Strategic Test Suite](https://www.chessprogramming.org/Strategic_Test_Suite) score
per release, theme by theme. This document exists for one question: **which evaluation
component is weakest right now?** An SPRT gives the Elo delta of a change but never says
*what* moved; the theme table does.

Credits and provenance of the suite file: [README](../README.md#credits-and-third-party-material).
How it is implemented and tested: [`testing.md`](testing.md). Run it with `tools/run-sts.sh`.

## 1. What the number is, and what it is not

myChess searches each of the 1188 positions to a **fixed depth** and plays a move. Each
position lists up to ten candidate moves annotated by Stockfish 15 with a point value
apiece, the best worth 100. The engine earns the value of what it played, or zero if it
played none of them. The score is points earned over points available.

- **It is not comparable to published STS ratings.** Those are measured at fixed *time*.
  This one is fixed depth, which is what makes it reproducible on any machine under any
  load — and incomparable to anyone else's number.
- **It is not comparable across depths.** A depth-6 run and a depth-8 run measure
  different quantities. Only rows at the same depth belong in the same series.
- **It is not a pure evaluation metric.** At fixed depth, a search change (LMR, PVS, move
  ordering) surfaces a different move at the same depth and therefore moves the score too.
  The quantity is "move quality at depth N", evaluation *and* search together.
- **It is not a strength estimate.** Strength is measured with cutechess and SPRT; see
  [`myChess-ELO-measurement.md`](myChess-ELO-measurement.md).

**Always read `best` and `miss` alongside the percentage.** The same score can mean
"half-good everywhere" or "a third perfect, the rest off the candidate list", and those
call for different work. `best` counts positions where the annotated best move was found;
`miss` counts positions scoring zero.

## 2. Measurement policy

**1. Run STS when `bench`'s node signature moved — not on every release.** This inverts
[`bench-history.md`](bench-history.md) § 6 rule 1 deliberately. That rule ("measure every
release, even tooling-only ones") rests on two things that do not hold here: three minutes
of cost, and the fact that an *unchanged* node signature **proves** neutrality. An
unchanged STS score proves nothing — it is a sum over 1188 positions, and different
evaluations can produce the same total. The inversion also gives a clean redundancy
argument: if the node signature is unchanged, the search is provably identical, so the STS
score *cannot* have moved and the run is provably wasted.

**2. The depth is frozen across releases** at `Sts.DEFAULT_DEPTH` (8), matching
`Bench.DEFAULT_DEPTH`. A row measured at another depth is a calibration or a spot check and
does not belong in the series below.

**3. Time and NPS are recorded, never asserted** — carried over from `bench-history.md`
rule 4. They are machine- and load-dependent; the score is not.

**4. There is no threshold test.** A score floor can only be derived from a baseline, which
makes it a snapshot of the engine that produced it: every improvement loosens it, and if
nobody raises it the guard is green while guarding nothing. See
[`testing.md`](testing.md) for the reasoning and for what `StsTest` does instead.

## 3. Cost

Measured 2026-08-18 on an Apple M1 Pro (10 cores), macOS 15.6.1, Corretto JDK 25.0.2:
**the full suite at depth 8 takes ~33 minutes**, 1.66 s per position on average.

Per-position cost varies strongly by theme, so do not extrapolate from one: the 70 King
Activity positions are endgame-heavy and ran at 0.85 s each, while the material-rich
middlegame themes cost roughly twice that. Extrapolating from `bench` overestimates badly
in the other direction — its suite is dominated by two artificial many-piece stress
positions at ~2.8 s apiece, which predicted 55 minutes for a run that took 33.

A shallower depth is available for a quick check (`tools/run-sts.sh 6`), but its number is
not comparable to the series above; see policy rule 2.

## 4. Results

### 4.4.2 — depth 8, 2026-08-18

**STS score 71.1 %** (84 427 / 118 800). 553 best-move hits (46.5 %), 87 misses (7.3 %).
Raw output: [`test-results/sts-4.4.2-d8.txt`](../test-results/sts-4.4.2-d8.txt).

| STS | Theme | pos | score | pct | best | miss |
|---:|---|---:|---:|---:|---:|---:|
| 8 | AKPC | 80 | 4761/8000 | **59.5 %** | 28 | 11 |
| 9 | Advancement of a/b/c pawns | 71 | 4288/7100 | **60.4 %** | 26 | 11 |
| 11 | King Activity | 70 | 4383/7000 | **62.6 %** | 27 | 6 |
| 15 | AT | 73 | 4596/7300 | 63.0 % | 19 | 3 |
| 1 | Undermine | 85 | 5540/8500 | 65.2 % | 39 | 8 |
| 7 | Offer of Simplification | 82 | 5447/8200 | 66.4 % | 30 | 5 |
| 3 | Knight Outposts/Repositioning/Centralization | 86 | 5801/8600 | 67.5 % | 38 | 11 |
| 2 | Open Files and Diagonals | 80 | 5407/8000 | 67.6 % | 32 | 5 |
| 4 | Square Vacancy | 89 | 6437/8900 | 72.3 % | 47 | 6 |
| 10 | Simplification | 79 | 5964/7900 | 75.5 % | 43 | 5 |
| 13 | Pawn Play in the Center | 75 | 5714/7500 | 76.2 % | 44 | 6 |
| 12 | Center Control | 74 | 5870/7400 | 79.3 % | 43 | 5 |
| 5 | Bishop vs Knight | 85 | 6777/8500 | 79.7 % | 50 | 3 |
| 14 | 7th Rank | 79 | 6452/7900 | 81.7 % | 51 | 2 |
| 6 | Recapturing | 80 | 6990/8000 | **87.4 %** | 36 | 0 |

`AKPC` and `AT` are the suite file's own abbreviations for themes 8 and 15; the published
STS theme list names them *Advancement of f/g/h pawns* and *Avoid Pointless Exchange*.

**The distribution is healthy, which was not a given.** Only 87 of 1188 positions score
zero, so myChess plays one of Stockfish's top ten moves 92.7 % of the time. Partial credit
therefore carries real signal here rather than measuring noise — had the misses dominated,
the percentage would have been uninformative and this whole instrument would need
rethinking before any conclusion were drawn from it.

**The finding: the two weakest themes are both flank-pawn advancement.** Theme 8 (f/g/h
pawns) at 59.5 % and theme 9 (a/b/c pawns) at 60.4 %, with King Activity third at 62.6 % —
and themes 8 and 9 also carry the joint-highest miss count (11 each). That is an
independent corroboration of the open [`king-safety` defect family](testing.md), arriving
from a completely different direction: those 19 cases were hand-picked from lost games, and
their most common shape is a pawn push in front of myChess's own king (`33.f3`, `20.h3`,
`38...g6`) — which is exactly what theme 8 measures, over 80 curated positions instead of a
handful of anecdotes. Two unrelated instruments pointing at the same weakness is stronger
evidence than either alone, and it says the work in [roadmap
§ 12.21](roadmap.md#1221-king-safety--m--3060-elo) is aimed correctly.

At the other end, `Recapturing` at 87.4 % with **zero** misses is the quiescence search and
SEE ordering doing their job, and `Bishop vs Knight` at 79.7 % is consistent with the
bishop-pair bonus shipped in v4.3.3 (+31.3 Elo).

Cost: **1 973 751 ms (32.9 min)**, 3 673 067 545 nodes, 1 860 957 NPS. Time and NPS are
informative only. Apple M1 Pro (10 cores), macOS 15.6.1, Corretto JDK 25.0.2; the machine
was **not** fully idle (a `mvn test-compile` ran during the measurement), which affects the
time columns and nothing else — the score is depth-bound and therefore unaffected.
