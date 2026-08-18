# STS history — the per-theme evaluation diagnostic

The [Strategic Test Suite](https://www.chessprogramming.org/Strategic_Test_Suite) score
per release, theme by theme. It was built for one question — **which evaluation component is
weakest right now?** — because an SPRT gives the Elo delta of a change but never says *what*
moved.

**Read § 5 before using the table for that purpose.** A low theme score turns out to mean
"myChess cannot calculate these positions out" at least as often as "myChess does not
understand them", and telling the two apart needs a second run at a greater depth. Six of the
fifteen themes are search-limited, including the weakest ones.

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

**The two weakest themes are both flank-pawn advancement**: theme 8 (f/g/h pawns) at 59.5 %
and theme 9 (a/b/c pawns) at 60.4 %, with King Activity third at 62.6 %, and themes 8 and 9
also carrying the joint-highest miss count (11 each).

> **This was read as an evaluation finding, and § 5 shows it is not one.** The reading was:
> the piece-square tables carry a gradient in the center (theme 13 at 76.2 %) and almost none
> on the wings, so flank-pawn judgment must be a missing evaluation term — and since the open
> `king-safety` cases are mostly pawn pushes in front of myChess's own king, two independent
> instruments appeared to agree. The depth-10 run refutes both halves. Themes 8 and 9 capture
> 21 % and 18 % of their remaining headroom from two extra plies, so their low scores are
> **reach, not knowledge**, while theme 13 — the supposedly table-supported one — turned out
> the most evaluation-limited of the three at 7 %. And theme 11 is *King Activity*, the king
> as an active piece; the suite has no king-safety theme, so it cannot corroborate that
> family in either direction. **Do not cite this table as support for
> [roadmap § 12.21](roadmap.md#1221-king-safety--m--3060-elo)**; that case rests on the 19
> game-derived cases and the keBKOXd1 guard. Read § 5 before drawing any conclusion from the
> ordering above.

At the other end, `Recapturing` at 87.4 % with **zero** misses is the quiescence search and
SEE ordering doing their job, and `Bishop vs Knight` at 79.7 % is consistent with the
bishop-pair bonus shipped in v4.3.3 (+31.3 Elo).

Cost: **1 973 751 ms (32.9 min)**, 3 673 067 545 nodes, 1 860 957 NPS. Time and NPS are
informative only. Apple M1 Pro (10 cores), macOS 15.6.1, Corretto JDK 25.0.2; the machine
was **not** fully idle (a `mvn test-compile` ran during the measurement), which affects the
time columns and nothing else — the score is depth-bound and therefore unaffected.

#### What the zero-scoring positions turned out to be — a qualification

The 87 misses were followed up in two stages, and the result narrows what may be concluded
from them. `tools/scan-sts-misses.py` measured each one's real centipawn loss with
Stockfish, because the suite's point values are a **ranking rescaled per position** — the
best move is worth 100 whether it leads the second by a tenth of a pawn or by three, so a
zero says nothing about magnitude. **32 of the 87 lose less than a pawn**: those are
preferences, and a zero there is ranking noise. The 55 survivors were then re-searched at
depths 8 to 13 to see whether myChess keeps the move (evaluation hole) or abandons it
(horizon effect).

Of the 25 classified, **all 25 are horizon effects**, and the abandonment depths cluster at
**depth 9** — one single ply beyond the measurement depth; not one survived to depth 13.
Ranking misses by centipawn loss turns out to select horizon effects *systematically*: a
large loss usually means something concrete and tactical, and tactics are exactly what extra
depth resolves.

#### Where the evaluation defects actually are

The same pipeline applied to the **low-score band** — moves worth 1 to 20 points, i.e. moves
that *are* among Stockfish's ten candidates but worth a fifth of the best or less — finds the
opposite. Such a move carries no refutation a ply deeper would reveal, so keeping it is a
preference rather than a reach problem.

Both filters are needed, and the cheap one first. Of the 191 positions scoring 0–20 points,
**91 lose less than a pawn** and are ranking noise; of the 45 survivors in the low-score
band, the depth sweep over 8–11 classified **27 as horizon** and **18 as evaluation
defects** (11 holding the same point value at every depth, 7 changing to something no
better). 14 are clean under the stricter reading that the maximum across depths must also
stay low.

`King Activity.100` is the case that proves the loss filter earns its keep: myChess's
2-point move and the suite's 100-point move both score **exactly 0.00** to Stockfish 18, so
the 98-point gap is Stockfish 15 disagreeing with Stockfish 18. Selecting on point values
alone would have made it a test.

**Fourteen of the 18 became characterization tests** in `StsDefectTest`, alongside the five
horizon cases — 19 in all. The first five were chosen for **aggregate backing** rather than
loss size (four of their five themes are among the five weakest above, so each stands on more
than its own position; ranking by loss would have picked three cases from a single theme).
The remaining nine were then written from the same data, and grouping them by *mechanism*
rather than by theme produced the finding the individual cases had hidden:

**In seven of nine, the better move is an activation and myChess plays a quiet piece move or
a trade instead** — a rook to the seventh or onto an open file (`Rc7`, `Rf7`, `Rh8`, `Re5`), a
pawn thrust that breaks a chain (`b4`, `d5`), a king stepping up (`Kg6`). Four of them are
rook activations, which makes `rook-activation` the deepest evaluation family in the suite and
a property of the evaluation rather than a quirk of one position. The mechanism is nameable:
mobility rewards the squares a rook can *see* and the tables reward centralization, but
nothing prices a rook **on** the seventh or the tempo of seizing a file — both pay off past
the horizon.

Families: `rook-activation` (4), `pointless-exchange` (3), `pawn-thrust` (2),
`king-activity` (2), `flank-pawn-advance`, `passed-pawn`, `defensive-resource` (provisional,
one case). Four excluded candidates recover substantially with depth
(`[6,40,40,40]`, `[18,32,32,32]`, `[20,34,34,34]`, `[20,35,35,35]`) and are at least partly
horizon effects. See [`testing.md`](testing.md) for each family.

Raw data for all of it is tracked: `sts-4.4.2-d8-losses-to-20pt.json`,
`sts-4.4.2-d8-classified-misses.jsonl`, `sts-4.4.2-d8-eval-trajectories.jsonl`.

**What this does not overturn:** the theme table above. The misses are the extreme tail,
7.3 % of positions; the score itself comes overwhelmingly from partial credit, i.e. from
positions where myChess plays a listed-but-worse move. Those are not shown to be
depth artifacts.

**What it does mean:** that the theme ranking reflects *evaluation* rather than search
depth is a plausible reading, not a measured one, and the flank-pawn interpretation above
should be read with that caveat. `Center Control.071` is the concrete warning — myChess
plays `1.h3`, the literal move shape of several open `king-safety` cases, and it is
measurably a horizon effect there, corrected at depth 10. Not every flank-pawn nudge is the
same defect.

**The measurement that would settle it** is a second full run at a deeper depth: themes
whose score rises sharply with depth are search-limited, themes that stay flat are
evaluation-limited. That run was made the same day — see § 5.

The five deepest-correcting cases became characterization tests in `StsDefectTest`, family
`search-horizon (defect)` — evidence for the search work in roadmap §§ 12.1–12.6 rather
than for the evaluation.

---

## 5. Depth 8 against depth 10 — is the theme table an evaluation diagnostic?

**Run 2026-08-18, v4.4.2, all 1188 positions at depth 10.** Raw output:
[`test-results/sts-4.4.2-d10-calibration.txt`](../test-results/sts-4.4.2-d10-calibration.txt).
Reproduce the comparison with `tools/compare-sts-depths.py`.

**This is not a series entry.** The series in § 4 is frozen at depth 8 so releases stay
comparable; this run exists to answer one question and the `-calibration` in its filename is
there to keep the two from being confused.

### The question

§ 4 reports the theme table as if it named the weakest *evaluation* components. That reading
is not free. A theme can score low because myChess misjudges its positions, or because those
positions need more plies than depth 8 allows — and the two call for opposite work, a new
evaluation term versus LMR/PVS/history. What forced the question: of the zero-scoring
positions at depth 8, **25 of 25 turned out to be horizon effects**. If a theme's worst
results are reach, its score may be too.

### Reading the numbers

Not raw points. `Recapturing` at 87.4 % has 12.6 points of headroom left, `AKPC` at 59.5 %
has 40.5 — the same +1.0 means something entirely different, so an absolute threshold marks
every already-strong theme "evaluation-limited" by ceiling effect alone. The column below is
the **share of remaining headroom** two extra plies capture. The 20 % line is a reading aid
sitting in the gap the data leaves, not a finding.

| STS | Theme | d8 | d10 | of room | same move | reading |
|---:|---|---:|---:|---:|---:|---|
| 3 | Knight Outposts/Repositioning/Centralization | 67.5 % | 78.1 % | **33 %** | 57 % | depth |
| 12 | Center Control | 79.3 % | 86.0 % | **32 %** | 74 % | depth |
| 11 | King Activity | 62.6 % | 71.8 % | **24 %** | 66 % | depth |
| 1 | Undermine | 65.2 % | 73.1 % | **23 %** | 69 % | depth |
| 8 | AKPC (f/g/h pawns) | 59.5 % | 68.2 % | **21 %** | 64 % | depth |
| 10 | Simplification | 75.5 % | 80.7 % | **21 %** | 70 % | depth |
| 14 | 7th Rank | 81.7 % | 84.9 % | 18 % | 78 % | evaluation |
| 15 | AT (Avoid Pointless Exchange) | 63.0 % | 69.5 % | 18 % | 62 % | evaluation |
| 9 | Advancement of a/b/c pawns | 60.4 % | 67.4 % | 18 % | 62 % | evaluation |
| 5 | Bishop vs Knight | 79.7 % | 82.7 % | 15 % | 76 % | evaluation |
| 4 | Square Vacancy | 72.3 % | 75.3 % | 11 % | 80 % | evaluation |
| 7 | Offer of Simplification | 66.4 % | 69.8 % | 10 % | 60 % | evaluation |
| 13 | Pawn Play in the Center | 76.2 % | 77.9 % | **7 %** | 71 % | evaluation |
| 2 | Open Files and Diagonals | 67.6 % | 68.7 % | **3 %** | 72 % | evaluation |
| 6 | Recapturing | 87.4 % | 87.2 % | **−1 %** | 89 % | evaluation |
| | **all 1188** | **71.1 %** | **76.2 %** | | 70 % | |

Totals at depth 10: 90 489 / 118 800, 611 best-move hits (51.4 %), 60 misses (5.1 %),
36.2 G nodes. The `same move` column is a second, independent read on the same split — where
the evaluation misranks, the engine keeps its choice however deep it looks.

### What it says

**The depth-8 ranking is not an evaluation diagnostic.** Six of the fifteen themes gain more
than a fifth of their headroom from two plies, and they include the weakest ones. The three
lowest scores at depth 10 are themes 9, 8 and 2 — and only theme 2 is evaluation-limited.
Strength and cause are independent axes: `Center Control` (79.3 %) and `Simplification`
(75.5 %) are strong *and* depth-limited.

**The one place two independent measurements agree is theme 2, open files and diagonals**, at
3 % — and the `rook-activation` family in [`testing.md`](testing.md) has four individually
verified cases of exactly that. **But** § 12.7.2 already tried a rook-file and battery bonus
and measured **−2.0 ± 10.8 Elo over 2 420 games**. Before building anything, resolve the
tension: the shelved term scored a *state* (a rook standing on an open file) while all four
cases are about the *move* that seizes the file first, and theme 2 covers diagonals too.
Count what the theme's own weak positions actually are before writing a term.

### Three caveats

1. **It cannot measure king safety.** No theme covers the king *under attack*; theme 11 is
   *King Activity*, the king as an active piece. Attacking motifs appear from the
   aggressor's side in theme 8, and that is depth-limited. So a king-safety term must be
   judged by SPRT — checking the STS score after building one would show nothing, and
   reading that as failure would be wrong. Roadmap § 12.21 rests on the 19 game-derived
   cases and the keBKOXd1 guard, neither touched by this run.
2. **The time column of the depth-10 file is contaminated.** The laptop lid was closed
   mid-run, putting about 26 minutes of suspension into the times of positions 409-413
   (411 alone reads 947 s). Scores are unaffected — the search is depth-bound. Use the
   median (9.9 s/position) rather than the mean if a figure is needed at all.
3. **It becomes stale when the search changes.** Every verdict here is relative to today's
   search. Once LMR/PVS/history land, the split has to be re-measured; a theme that is
   depth-limited now may become evaluation-limited when the same depth sees more.
