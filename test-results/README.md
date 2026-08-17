# test-results

Raw cutechess / Ordo output. Filenames are the index; this file only records
what a filename cannot say.

## Which files are valid input

`match-<version>-vs-<engine>.pgn` — the unsuffixed name is always the match that
counts. `tools/run-anchor-bracket.sh` concatenates exactly these for Ordo, so a
file with any suffix is invisible to the evaluation by construction.

A rerun never overwrites: the script moves an existing match aside to
`*-superseded-<timestamp>.*` before starting. "Superseded" is all the script can
honestly say at that moment — once the *reason* is known, rename it to something
that states the reason, as with the `INVALID-hash16` files below.

## Absolute Elo re-anchor, myChess 4.4.1 (2026-08-15/16)

Four matches at TC 40/120, 400 games each, against TSCP 1.81 (1607), Zeta Dva
0402 (1801), Princhess 0.7.0 (1985) and Kojiro 0.1.4 (free).

| file | status |
|---|---|
| `match-4.4.1-vs-tscp.pgn` | valid — 309-57-34, 81.5 %, +257.6 ± 41.0 |
| `match-4.4.1-vs-zetadva.pgn` | valid — 248-94-58, 69.3 %, +141.0 ± 33.7 |
| `match-4.4.1-vs-princhess.pgn` | rerun with `option.Hash=256`, started 17:10 |
| `match-4.4.1-vs-princhess-INVALID-hash16.pgn` | **do not evaluate** — see below |
| `anchor-driver-4.4.1-run1.log` | driver log of the first run: TSCP and Zeta Dva **valid**, Princhess invalid |
| `anchor-driver-4.4.1-rerun.log` | driver log of the Princhess + Kojiro rerun |

### Why the first Princhess match is invalid

It ran at Princhess's own default `Hash` of 16 MB, because the driver passed no
`option.Hash`. Princhess is an MCTS engine — every tree node lives in the hash —
so 16 MB fills after ~9 600 nodes and the search stops: **0.07 s per move against
myChess's 2.89 s, a factor of 40.** The match scored 86.4 % for myChess, which
would have implied ~2300 Elo. It measured a crippled opponent, not an 1985 one.

The file is kept rather than deleted because it is the only physical record of
that failure mode, and because it doubles as a negative control: it is what a
match against a hobbled opponent looks like in the per-move times.

**The check that finds this in a minute** — compare the per-move times in the PGN
comments. Two engines at the same TC belong within a factor of ~2 of each other:

```
vs tscp       TSCP      2.66 s/move     myChess 2.91 s/move    ok
vs zetadva    ZetaDva   2.93 s/move     myChess 2.91 s/move    ok
vs princhess  Princhess 0.07 s/move     myChess 2.89 s/move    <- broken
              (after the fix: 1.38 s)
```

CCRL's published blitz conditions require 128 or 256 MB for **all** engines in a
match, so 256 MB is not a tuning choice here but the condition the anchor ratings
were established under. Kojiro's default is 1 MB and gets the same treatment
(less damaging for an alpha-beta engine, which loses hit rate rather than
stopping, but equally incomparable).
