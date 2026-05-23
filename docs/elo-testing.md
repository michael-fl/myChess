# Measuring playing strength with cutechess

External match testing complements the [JUnit suite](testing.md): the
JUnit tests pin down per-position behavior, but they cannot tell us
how strong myChess actually plays. For that we use
[cutechess-cli](https://github.com/cutechess/cutechess), a tournament
runner that drives two UCI engines against each other and reports
score, ELO difference, and a sequential statistical test that
terminates the match as soon as the result is conclusive.

## The standard match command

The reference setup used for every measurement run (`test01`,
`test02`, …) is:

```sh
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess cmd=./mychess-uci.sh proto=uci \
    -engine name=SF-1600 cmd=stockfish proto=uci \
          option.UCI_LimitStrength=true option.UCI_Elo=1600 \
    -each tc=40/1200 \
    -rounds 20 -games 2 -repeat \
    -sprt elo0=-50 elo1=50 alpha=0.05 beta=0.05 \
    -openings file=8moves_v3.pgn format=pgn order=random plies=16 \
    -concurrency 2 -ratinginterval 2 \
    -recover \
    -draw movenumber=40 movecount=8 score=10 \
    -resign movecount=5 score=600 \
    -pgnout mychess-vs-sf1600.pgn
```

## Parameter walkthrough

### Engines

`-engine name=… cmd=… proto=uci` declares one competitor. Two
`-engine` blocks are required.

- **`myChess`** launches via `./mychess-uci.sh`, which pins
  `JAVA_HOME` to Corretto 25, `cd`s into the project root so
  `db/openings.db` resolves correctly, and `exec`s the UCI handler.
- **`SF-1600`** is a system Stockfish on the `$PATH` capped to
  approximately 1600 ELO via UCI options
  `UCI_LimitStrength=true UCI_Elo=1600`. Stockfish's strength
  reduction is achieved through noise / skill levels, *not* through
  search-depth limits, so its reported eval scores stay accurate
  even under the cap.

`-each tc=40/1200` applies the same time control to both engines:
40 moves in 1200 seconds per side, i.e. the classical "40/20"
control.

### Match length

`-rounds 20 -games 2 -repeat` schedules at most 20 rounds × 2 games
= 40 games. Each round plays the same opening twice with colors
swapped (the `-repeat` flag), so any per-opening color advantage
cancels out across each pair.

### Statistical termination (SPRT)

`-sprt elo0=-50 elo1=50 alpha=0.05 beta=0.05` enables Wald's
Sequential Probability Ratio Test. After every finished game,
cutechess updates a running log-likelihood ratio (`llr`) and checks
whether the data is now strong enough to accept one of two
hypotheses about the true ELO difference:

- **H0**: true ELO difference ≤ `elo0` = −50, i.e. *myChess is at
  least 50 ELO weaker than the opponent*.
- **H1**: true ELO difference ≥ `elo1` = +50, i.e. *myChess is at
  least 50 ELO stronger than the opponent*.
- **`alpha = 0.05`**: maximum probability of falsely accepting H1.
- **`beta = 0.05`**: maximum probability of falsely accepting H0.

The range between `elo0` and `elo1` is the *indifference zone*. SPRT
makes no strong claim there, and the nominal error rates do not
hold strictly inside it:

```
    H0 accepted               indifference zone              H1 accepted
  ←──────────────────●───────────────────────────────●──────────────────→
                   −50                              +50         ELO difference
                                                              (myChess − opponent)
```

The `llr` lives on a separate axis with two stop thresholds, derived
directly from `alpha` and `beta`:

```
ubound  =  ln((1 − beta) / alpha)  = ln(0.95 / 0.05) = ln(19) ≈ +2.944
lbound  = −ln((1 − alpha) / beta)  = −ln(19)                 ≈ −2.944
```

When `llr` crosses `ubound`, H1 is accepted and the match
terminates with verdict *"myChess is at least 50 ELO stronger"*.
When it crosses `lbound`, H0 is accepted with verdict *"myChess is
at least 50 ELO weaker"*. If the match runs out of games (max
`rounds × games`) before either bound is crossed, no verdict is
produced; cutechess just reports the score and the running `llr`.

With symmetric `alpha = beta`, the two bounds are symmetric. An
asymmetric choice — e.g. `alpha=0.05, beta=0.01` — yields
`ubound = +4.6`, `lbound = −2.94`, making H1 harder to accept than
H0.

### When to vary the SPRT window

- **`elo0 = −50, elo1 = +50`** (broad): suitable when expecting a
  large ELO gap. SPRT typically decides within 20–40 games.
- **`elo0 = 0, elo1 = 10`** (tight, common in Stockfish patch
  testing): used to detect tiny improvements. Needs thousands of
  games to decide; only worth running for engines already close in
  strength.
- **Omit `-sprt` entirely**: cutechess plays out all `-rounds`
  without early termination and reports a final ELO estimate and
  confidence interval based on the full match.

### Other flags

| Flag | Purpose |
|---|---|
| `-openings file=… format=pgn order=random plies=N` | Pick a random opening line from the file; play it for `N` plies before letting the engines take over. See [Opening books](#opening-books) below. |
| `-concurrency 2` | Run two games in parallel. Pushing this beyond physical cores ÷ 2 distorts time-control behavior because engines compete for CPU. |
| `-ratinginterval 2` | Print the running score block every 2 finished games. |
| `-recover` | Restart an engine on crash / hang rather than aborting the match. |
| `-draw movenumber=40 movecount=8 score=10` | Adjudicate as a draw after move 40 if both engines agree the score is within ±10 cp for 8 consecutive moves. Captures and pawn moves reset the counter. |
| `-resign movecount=5 score=600` | Adjudicate as a loss if one engine reports ≥ 600 cp deficit for 5 of its own moves in a row. |

The specific adjudication numbers above are **convention-near but
chosen by us**, not lifted from an authoritative standard. The
Stockfish testing infrastructure (Fishtest) — the closest thing to a
community baseline — uses comparable values, identical for the draw
settings (`movenumber=40 movecount=8 score=10`) and slightly tighter
for the resign trigger (Fishtest typically uses `movecount=3
score=600`; we picked `movecount=5` to wait longer before declaring a
position lost, because myChess's evaluation noise is larger than
Stockfish's and we want to give defensive lines more time to
stabilize before adjudicating).
| `-pgnout file.pgn` | Append every finished game to a PGN file. |

## Opening books

The choice of opening book has a substantial effect on the per-color
outcome distribution, and a smaller but real effect on the overall
ELO measurement. Two books ship with this project:

| File | Plies (depth) | Lines | Character |
|---|---:|---:|---|
| `8moves_v3.pgn` | 16 | 34 700 | Theoretical openings 8 moves deep. Many lines exit at a known evaluation around +0.3 to +1.0 for White. Heavily White-favoring in aggregate. |
| `2moves_v2.pgn` | 4 | 12 092 | Random first-2-move sequences across both sides. Engines diverge from the book very early; positions stay close to the starting setup and are substantially more balanced. |

Both come from the [Stockfish books
repository](https://github.com/official-stockfish/books) and are
kept at the project root.

### Effect on the per-color score split

In `test01` and `test02` (both with `8moves_v3.pgn`) the side
playing White scored ~0.80 across both engines combined — far above
the typical engine-match first-move advantage of ~0.55. The book's
deliberately unbalanced theoretical lines account for most of that
gap; see
[known-issues.md → Color asymmetry](known-issues.md#color-asymmetry-mychess-plays-much-weaker-as-black)
for the full analysis.

`2moves_v2.pgn` is the natural follow-up choice whenever the
question is "how strong is the engine, independent of book bias?".

Rule of thumb:

- **`8moves_v3.pgn`** when a long, diverse set of theoretical
  middlegame starting positions is wanted, and the inherent
  White-side advantage in aggregate is acceptable.
- **`2moves_v2.pgn`** when a clean engine-strength reading or a
  per-color analysis is the goal.

Other books — for example the Stockfish UHO collection, the Noomen
test suite, or a hand-crafted minimal set — can be dropped into the
project root and selected by changing `-openings file=…`.

## Interpreting the output

The score-summary block printed at every `-ratinginterval` interval
and at the end of the match reads like this:

```
Score of myChess vs SF-1600: 14 - 4 - 1  [0.763] 19
...      myChess playing White: 9 - 0 - 0  [1.000] 9
...      myChess playing Black: 5 - 4 - 1  [0.550] 10
...      White vs Black: 13 - 5 - 1  [0.711] 19
Elo difference: 203.3 +/- 222.6, LOS: 99.1 %, DrawRatio: 5.3 %
SPRT: llr 3.18 (108.1%), lbound -2.94, ubound 2.94 - H1 was accepted
```

Line by line:

- **`Score of myChess vs SF-1600: 14 - 4 - 1  [0.763] 19`** —
  wins, losses, draws from myChess's perspective; the bracketed
  score percentage `(W + 0.5·D) / total`; the total count of
  *decided* games. 0.5 means even, > 0.5 means myChess outperformed
  the opponent.
- **`myChess playing White` / `playing Black`** — same statistic,
  filtered by which color myChess held. Useful for spotting
  color-asymmetric play (e.g. the pre-fix 0.825 / 0.225 split
  documented in known-issues.md).
- **`White vs Black`** — *which side* won, aggregated across both
  engines combined. Quantifies whether the test setup
  intrinsically favors one color. The typical engine-match value
  is 0.52–0.55; substantially higher indicates a biased book or
  adjudication setup.
- **`Elo difference: X +/- Y`** — point estimate and 95 %
  confidence half-width. Y is wide for small samples (e.g. ±222
  ELO after 19 games is normal).
- **`LOS`** — "Likelihood of Superiority". Probability that the
  true ELO difference is positive given the data. Distinct from
  the SPRT verdict: LOS just asks "is myChess stronger at all?",
  SPRT asks "by at least `elo1`?".
- **`DrawRatio`** — fraction of decided games that ended in a draw.
- **`SPRT: llr X (Y%), lbound A, ubound B - verdict`** — `llr` is
  the running log-likelihood ratio; the parenthesized percentage
  shows how far through `[lbound, ubound]` we are toward the
  nearer bound (100 % = exactly at a bound, > 100 % = the bound
  was crossed and the verdict has fired). The verdict is
  `H1 was accepted`, `H0 was accepted`, or absent if the match
  ended without crossing either bound.

For each finished game cutechess also prints

```
Finished game N (PlayerA vs PlayerB): RESULT {reason}
```

with `RESULT` ∈ {`1-0`, `0-1`, `1/2-1/2`, `*`} and `reason` one of
`White wins by adjudication`, `Black mates`, `Draw by 3-fold
repetition`, etc. `*` ("No result") appears for games that were
still running when SPRT terminated and were never finished.

The Player-summary block at the end of the match groups all of
each engine's individual game outcomes by `reason`:

```
Player: myChess
   "Draw by 3-fold repetition": 1
   "Loss: White wins by adjudication": 4
   "No result": 1
   "Win: Black mates": 2
   "Win: Black wins by adjudication": 3
   "Win: White wins by adjudication": 9
```

Each prefix (`Win` / `Loss` / `Draw` / `No result`) is the outcome
from *this engine's* perspective; the second clause describes how
the game ended. The breakdown is useful for spotting, for example,
"this engine never actually mates the opponent — every win comes
via the `-resign` adjudication".

## Practical workflow

1. **Build** so cutechess's freshly-spawned engine processes pick
   up the current sources:
   ```sh
   mvn package
   ```
2. **Disable the in-engine opening book** for a clean engine-only
   reading (otherwise myChess's own book plays the first few moves
   on top of cutechess's opening):
   ```sh
   mv db/openings.db db/openings.db.disabled
   ```
3. **Run the match**, capturing cutechess's stdout into a log:
   ```sh
   <the standard command above> > test-NN-cutechess-stdout.log 2>&1
   ```
   The engine's own stderr is captured by `mychess-uci.sh` via
   `tee -a mychess-stderr.log` into the project root.
4. **Archive the artifacts** with a matching prefix:
   ```sh
   mv mychess-stderr.log         test-NN-mychess-stderr.log
   mv mychess-vs-sf1600.pgn      test-NN-mychess-vs-sf1600.pgn
   ```
5. **Restore the book** when done:
   ```sh
   mv db/openings.db.disabled db/openings.db
   ```

Historical artifacts from previous runs are kept at the project
root with `test01-*`, `test02-*`, `test03-*` prefixes for easy
cross-reference.
