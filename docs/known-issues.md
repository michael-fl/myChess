# Known issues

Open bugs and investigations whose status is not yet a resolved commit. Each
section captures: what was observed, what was tried, what is still open, and
the artefacts (tests, log hooks, reproducer positions) created so far.

## Illegal PV emitted by the engine (under cutechess, 2026-05-21)

### Observation

During an SPRT match against Stockfish (`UCI_LimitStrength=true`,
`UCI_Elo=1600`, `tc=40/1200`, 8-move book), cutechess printed:

```
Warning: Illegal PV move g4g5 from myChess (3)
Warning: PV:    Ra2+ Bf2 Rfxf2+ Kg3 Rf3+ Kh4 Rxf4 g4g5
Finished game 2 (SF-1600 vs myChess): 1-0 {White wins by adjudication}
```

The trailing `(3)` is cutechess's running counter — by the time game 2 was
adjudicated, myChess had already emitted three illegal PVs in this run.

### Position where the bug was triggered

Game 2 in `mychess-vs-sf1600.pgn` (SF-1600 vs myChess, Modern Alekhine /
Saemisch-like middlegame). myChess as Black, to move after White's 32. f4.
Move list up to and including White's 32. f4:

```
1. e4 Nf6 2. e5 Nd5 3. d4 d6 4. Nf3 c6 5. c4 Nc7 6. exd6 exd6 7. d5 Be7
8. Nc3 O-O 9. Be2 Bf6 10. h3 Bxc3+ 11. bxc3 Qf6 12. Qd4 cxd5 13. cxd5 Qxd4
14. Nxd4 Nxd5 15. Bd2 Nc6 16. Nb5 Rd8 17. O-O Be6 18. c4 Nb6 19. Bg5 f6
20. Be3 Bxc4 21. Rfe1 Bxe2 22. Nxa7 Nxa7 23. g3 Nc6 24. Bxb6 Re8 25. a3 d5
26. Bc5 Bd3 27. f3 Rxe1+ 28. Kh2 Rxa1 29. g4 Rf1 30. Kg2 Ne5 31. a4 Rxa4
32. f4
```

myChess's reply to 32. f4 was 32...Ra2+, after which the position was
adjudicated (`-resign movecount=5 score=600`; myChess reported -19.00 cp at
depth 9 in 50 s). The PV that caused the warning began with that Ra2+ and
walked eight plies deep; the eighth ply, `g4g5`, is illegal in the position
reached after the preceding seven plies — no white g-pawn remains on g4 by
that point (it was already captured several plies earlier in the line).

### Hooks added so far

- **`UciHandler.validatePv`** (commit pending, source-only edit). Replays
  every PV from the search-root board the moment it is emitted via the
  iteration callback, and on the first illegal move writes a
  `[pv-validate] illegal PV move … at ply … of PV … — root FEN: … — illegal
  at FEN: …` line via `Log.error` (stderr → `mychess-stderr.log`). The next
  cutechess run after a rebuild will capture the exact root FEN, full PV,
  and illegal-move position, removing the need to reconstruct from PGN.
- **`IllegalPvRegressionTest`** (`src/test/java/.../IllegalPvRegressionTest.java`).
  Replays the move list above via `GameImporter`, runs the search at the
  same depth at which the bug was observed (`maxDepth=9`, no time limit),
  captures every `IterationInfo.pv()` plus the final `MoveAndWeight.path()`,
  and asserts that each move in each PV is legal in the position reached by
  applying the preceding PV moves to the search root. **Currently red** —
  reproduces the bug deterministically once the legality check is correct;
  see "Reproduction confirmed" below.

### Reproduction confirmed (2026-05-21)

The first version of `validatePv` / `assertPvIsLegal` used only
{@code Moves.contains(move)} as the legality predicate. That accepted the
bug move `g4g5` as legal, because {@code MoveGenerator.calculateMoves}
returns **pseudo-legal** moves — moves that leave the own king in check
are still in there, since pin / self-check filtering is the
search's responsibility via the {@code Moves.ILLEGAL} sentinel returned on
the *next* ply when a king-capture is found.

After tightening both validators to call {@code calculateMoves} again
after each {@code makeMove} and check {@code Moves.isIllegal()} on the
resulting position, the test fails on the first run in IntelliJ with:

```
iteration #8 PV: a4-a2 c5-f2 f1-f2 g2-g3 f2-f3 g3-h4 f3-f4 g4-g5
ply 7 move g4-g5 leaves own king in check
```

Translated to human notation:

| Ply | Side  | Move    | Note |
|-----|-------|---------|------|
| 0   | Black | Ra2+    | check |
| 1   | White | Bf2     | bishop blocks |
| 2   | Black | Rxf2+   | rook from f1 captures bishop, check |
| 3   | White | Kg3     | king escapes |
| 4   | Black | Rf3+    | check on the third rank |
| 5   | White | Kh4     | king to h4 |
| 6   | Black | Rxf4    | rook captures the f-pawn, **king on h4 is now in check from the rook on f4 along the 4th rank** |
| 7   | White | g4-g5   | **fails to address the check** — pseudo-legal, but leaves the king attacked |

So the bug class is precise: the search is letting a **self-check escape
the {@code Moves.ILLEGAL} filter** somewhere along this PV branch. The
move generator is doing its job (g4-g5 is correctly pseudo-legal — there
is a white pawn on g4); the search is failing to discard it.

Importantly, the earlier hypothesis that the regression test couldn't
reproduce the bug because of time pressure, `IterationTimings` warmup, or
per-instance accumulation was wrong. The test always reproduced it — we
just couldn't see it because our legality predicate matched the search's
own (pseudo-legal-only) predicate. Tightening the predicate uncovered the
bug immediately at depth 8, without any of the production-only conditions
listed below.

### Variants for tracking the bug down

#### Variant A — make the test sharper ~~(planned)~~ **obsolete**

~~Add a second test that mirrors production timing more faithfully (set
`millisPerMove`, iterate over several depths, warm up `IterationTimings`,
etc.).~~

Obsolete: the existing `IllegalPvRegressionTest` already fails at depth 8
without any of these stress conditions, once the legality predicate
properly accounts for self-check (see "Reproduction confirmed" above).
None of the production-only conditions are needed to trigger the bug.

#### Variant B — capture the next live occurrence

Still useful as a confidence check that the regression test catches the
*same* class of bug that fires in production. After fixing the search
issue, run cutechess again with the `validatePv` hook compiled in and
confirm that `[pv-validate]` no longer appears in
`mychess-stderr.log` over a long SPRT match.

#### Variant C — read the search code (now the active path)

The bug is a **self-check that survives the `Moves.ILLEGAL` filter** on
some branch of the depth-8 search from the offending root position.
Concrete leads, in order of suspicion:

1. **PV propagation after a beta cutoff in `PositionSearch`.** When the
   first child move produces β-cutoff, the cutoff move is written into
   the PV row at the current depth. If this happens *before* the child's
   own `calculateMoves` has detected that the move was actually a
   self-check (i.e. before the child position's `Moves.ILLEGAL` is
   consulted), the PV will contain an illegal move. Check the order
   between `makeMove → calculateMoves(child) → isIllegal? → pv[depth]
   = move`.
2. **Quiescence-to-main-search transition.** Ply 6 in the failing PV
   (`Rxf4`) is a capture, ply 7 (`g4-g5`) would be the static-eval reply
   inside `QuiescenceSearch`. `QuiescenceSearch` only considers captures
   in its move list, and the implementation of self-check filtering
   there may differ from `PositionSearch`'s main loop. If the static-eval
   stand-pat branch in quiescence picks up a stored PV move that wasn't
   validated against `Moves.ILLEGAL`, this is exactly where it would
   surface.
3. **PV-table row reset between iterations of iterative deepening.** The
   flattened `int[pvMaxLength * pvMaxLength]` indexed by
   `depth * pvMaxLength + depth` — is the next-depth row cleared, or does
   it rely on being overwritten? If a deeper iteration reads from a row
   that a shallower iteration partially wrote, stale entries can leak in.
4. **`bestKnownPath` vs the freshly-built PV at the current depth.** The
   `MoveSorterImpl` is asserted to place the previous iteration's PV
   first; if the *rest* of the previous PV is read out of an unsanitised
   row, the assertion holds but the tail is stale.

Leads 1 and 2 are the most likely given the shape of the failing PV
(check evaded by a quiet move on the eighth ply, right where quiescence
would kick in). Worth instrumenting `PositionSearch.calculateBestMove`
and `QuiescenceSearch` with the same per-move legality check the
regression test now uses, then re-running the test to bisect which
recursion level first writes the bad move into the PV.

### Next step

Variant C, led by the two leads above. The regression test stays in
place as the canary; once a fix is in `PositionSearch` or
`QuiescenceSearch`, the test must go green without changes to its
assertions.

### Pointers

- Source: `src/main/java/org/michaelfl/mychess/engines/PositionSearch.java`,
  `src/main/java/org/michaelfl/mychess/UciHandler.java` (`emitInfo`,
  `validatePv`).
- Test: `src/test/java/org/michaelfl/mychess/IllegalPvRegressionTest.java`.
- Match artefacts: `mychess-vs-sf1600.pgn` (game 2), `mychess-stderr.log`
  (timestamps 22:06 onward for the run in which the warning was first
  observed).

## Color asymmetry: myChess plays much weaker as Black

### Observation

Intermediate score in the SPRT match against SF-1600
(`tc=40/1200`, 8-move book, `-resign movecount=5 score=600`) after
**34 of 40 games**:

```
myChess vs SF-1600:            18 - 14 - 2  [0.559] 34
... myChess playing White:     14 -  2 - 1  [0.853] 17
... myChess playing Black:      4 - 12 - 1  [0.265] 17
... White vs Black overall:    26 -  6 - 2  [0.794] 34
Elo difference: +41.1 ± 118.5, LOS: 76.0 %, DrawRatio: 5.9 %
SPRT: llr 1.24 (42.1 %), bounds ±2.94 — undecided
```

The 0.853 vs 0.265 split between myChess-as-White and myChess-as-Black is
the dominant signal. The aggregate White-side score of 0.794 across both
engines combined is also well above the typical ~0.55 first-move advantage
in engine matches — this asymmetry is structural to myChess, not a
property of chess.

### Two leading hypotheses

1. **`WeightingFunction` / `PieceSquareTables` are not cleanly antisymmetric
   under colour flip.** PSTs that aren't mirrored across the board centre
   for Black, mobility or king-safety terms with a sign error, or any
   weight that doesn't satisfy `eval(swap_colors(board)) == -eval(board)`
   would systematically push the engine to underrate Black's position and
   pick weaker Black moves.
2. **The `g4-g5` self-check bug fires preferentially on the Black side.**
   The confirmed PV bug occurred in a search where myChess was Black, and
   the failing line involved a king-on-the-rim middlegame that Black
   reaches more readily than White in the openings this match samples. If
   the underlying search defect interacts with Black-typical structures,
   Black play would degrade systematically. Fixing the self-check filter
   should reduce — but not necessarily eliminate — the asymmetry; the
   residual is then hypothesis 1.

Not mutually exclusive. The match data alone can't tell them apart.

### Pointers

- Evaluation: `src/main/java/org/michaelfl/mychess/WeightingFunction.java`,
  `src/main/java/org/michaelfl/mychess/PieceSquareTables.java` —
  inspect for any value table that isn't built / read symmetrically for
  the two sides.
- Match artefacts (in-progress at the time of writing):
  `mychess-vs-sf1600.pgn`, `mychess-stderr.log`.

## Planned investigations (after the current match completes)

1. **Sweep `mychess-stderr.log` for `[pv-validate]` entries.** cutechess's
   `(3)`-and-counting warning counter showed multiple illegal-PV hits
   during the run, so the in-engine validator should have logged
   several. Each entry contains the search-root FEN, the offending ply
   index, and the full PV — enough to build a FEN-based regression test
   per occurrence, independent of `GameImporter` move replay. Goal: lock
   down several instances of the bug class with separate tests, so a fix
   in `PositionSearch` / `QuiescenceSearch` can be verified
   breadth-first instead of relying on a single example.
2. **Filter PGN for Black-side disasters.** Scan `mychess-vs-sf1600.pgn`
   for games myChess lost as Black with sudden score jumps in the
   move-comment evaluations (e.g. `{≈0/d}` to `{-3.5/d}` within a single
   ply). For each suspect position, capture the FEN myChess scored from
   plus its reported score, then evaluate the **mirror position** (swap
   colours and ranks) with the same engine and compare. A large
   asymmetry between `eval(position)` and `-eval(mirror(position))` is
   direct evidence for hypothesis 1 in the colour-asymmetry section
   above — any positions that don't mirror cleanly become either an
   evaluation fix candidate or a second-class bug entry of their own.

Both items will produce either new regression tests (case 1) or new
entries in this file (case 2) — not features.

## Test01 findings (full 40-game run, 2026-05-22)

The full 40-game SPRT match against `SF-1600`
(`tc=40/1200`, `8moves_v3.pgn` opening book, `-resign movecount=5
score=600`, `-recover`, `-draw movenumber=40 movecount=8 score=10`)
completed overnight. Final state:

```
myChess vs SF-1600:           20 - 18 - 2  [0.525] 40
... myChess playing White:    16 -  3 - 1  [0.825] 20
... myChess playing Black:     4 - 15 - 1  [0.225] 20
... White vs Black overall:   31 -  7 - 2  [0.800] 40
Elo difference: +17.4 ± 108.3, LOS: 62.7 %, DrawRatio: 5.0 %
SPRT: llr 0.612 (20.8 %), bounds ±2.94 — undecided
```

Artefacts in the project root (renamed for archival):
`test01-cutechess-stdout.log`, `test01-mychess-stderr.log`,
`test01-mychess-vs-sf1600.pgn` (40 games).

### Illegal PV warnings — much more pervasive than initially thought

cutechess flagged **129 illegal-PV warnings across 27 of 40 games
(67.5 %)**. The bug is not a rare edge case; it is a structural defect
that fires in a majority of games at least once.

Distribution of warnings per game (top of the list):

| cutechess game | myChess side | Warnings |
|----------------|--------------|---------:|
| 9              | White        | 36       |
| 31             | White        | 23       |
| 37             | White        |  8       |
| 39             | White        |  7       |
| 26             | Black        |  5       |
| 28             | Black        |  4       |
| 32             | Black        |  4       |
| 33             | White        |  4       |

Eleven further games are in the 1–3 range; the rest are clean.

**Color-independence of the bug.** The previous working hypothesis was
that the illegal-PV bug might preferentially fire on the Black side
(because the first observed instance was a Black-to-move position).
The full run flatly contradicts that: the three worst offenders (games
9, 31, 37 with 67 warnings combined) all have myChess on the White
side. The bug is symmetric — it just doesn't always cost a game when
myChess is White, presumably because the move actually played is often
still strong even when the rest of the reported PV is corrupted.

This is an important update to the "Two leading hypotheses" section
above: hypothesis 2 (Black-side score asymmetry caused by the PV bug)
becomes much less plausible. Hypothesis 1 (`WeightingFunction` /
`PieceSquareTables` are not cleanly antisymmetric) moves to the front.

### Illegal-PV patterns

Sampling the 129 warnings, several distinct shapes appear:

1. **Self-check escape ignored.** The bug we already analysed. PV ends
   with a quiet move that fails to address a check delivered earlier in
   the PV. Example from game 1 / round 1:
   `Ra2+ Bf2 Rfxf2+ Kg3 Rf3+ Kh4 Rxf4 g4g5` — ply 7's `g4g5` does not
   address the check from the rook on f4.
2. **King moves onto attacked squares.** Several PVs end with a king
   move that walks straight into a discovered or direct attack. E.g.
   game 11/17: `Kg6 Qe6+ Kg7 Ke7 Kh7 Kf8 Kh8 Qg8# h8h7` — the final
   `h8h7` ignores that Qg8# was already announced. Variants of this
   pattern recur with king shuffles `h8h7` ↔ `h7h8` ↔ `g5g4` ↔ `g6g5`
   inside positions that contain an active queen.
3. **Promotion PVs of length 1 (most striking).** Game 31 emits PVs
   that are just a bare promotion move: `Warning: PV:  f7f8q` (with no
   preceding moves). It also emits sequences like
   `Kc3 Ra3+ f7f8q` where `f7f8q` doesn't address the check from
   `Ra3+`. Twelve of game 31's warnings involve `f7f8q` in some form.
   This is not the usual "last ply is garbage" shape; either the PV
   table only contains a single entry and that entry is the wrong
   move, or the entire row was cleared mid-iteration and partly
   refilled.
4. **PVs of length 2.** Several `(0)`-tagged warnings have just two
   moves where the second is illegal: `Rxe8+ h2f3`, `Bxb4+ c6c5`,
   `Kxh7 Qc2+ b8c6`, `Qf8# e2d1`. These tend to arrive after a
   forcing move (check/capture) and look like the PV writer simply
   copying an unrelated move from a different node into the second
   slot.

The diversity of shapes — length-1 promotion, length-2 forcing-move
tails, deep self-check-evasion failures, repetitive king shuffles —
suggests the underlying defect isn't a single typo in one branch. It
is more consistent with a PV-table reset / index-arithmetic problem
that contaminates the table whenever certain alpha-beta interactions
happen.

The `(N)` suffix on each cutechess warning (`(0)`, `(3)`) is *not* a
running counter, as previously assumed. It varies independently and
is most likely a cutechess-internal field (PV-index or score-type
flag). Don't read meaning into it.

### Evaluation asymmetry — concrete example (PGN round 3)

Round 3 (`SF-1600` vs `myChess`, myChess as Black, lost 1-0 in
adjudicated 129-move game) is a textbook illustration of hypothesis 1.
A representative slice of the score timeline, taking SF's eval as the
ground-truth reference:

| Move pair                | SF eval (White POV) | myChess eval (Black POV) | Mirror diff |
|--------------------------|--------------------:|-------------------------:|------------:|
| 18. Qe4 / 18...Rf6       |              +0.33 |                   −0.41 |        +0.08 |
| **19. h5 / 19...Nxb2**   |          **+2.64** |               **−0.53** |    **−2.11** |
| 26. Bxe5 / 26...Re6      |              +3.47 |                   −1.02 |        −2.45 |
| 28. Nf4 / 28...Rf6       |              +4.50 |                   −0.75 |        −3.75 |
| 29. Ncd5 / 29...Bxc7     |              +5.72 |                   −2.21 |        −3.51 |
| **30. Nxf6+ / 30...exf6** |          **+9.43** |               **−0.59** |    **−8.84** |
| **31. Qg6+ / 31...Kh8**  |         **+10.61** |               **+2.00** |    **−8.61** |
| **32. Qxf6+ / 32...Kg8** |        **+82.43**¹ |               **+4.00** | **mate gap** |

¹ SF's "+82.43" is its centipawn proxy for an imminent mate; it
announces explicit mate scores (`M53`) starting the next move.

myChess's Black-side evaluation **never sees the catastrophe coming**.
At move 30 SF reports +9.43 for White (lost queen for Black,
essentially decided), while myChess as Black still scores the position
at −0.59 (roughly equal). The gap is *not* the usual "shallower search
misses tactics" issue alone — depths are 9–11 for myChess vs 23–30 for
SF, which would explain a few centipawns of disagreement, not eight or
nine pawns.

A 5- to 9-pawn systematic underestimation by myChess of losing
positions on the Black side, while myChess **on the White side**
correctly identifies the same kinds of positions as winning by similar
margins (see SF's mirrored scores in winning Black-vs-myChess-as-Black
games — myChess as White routinely reports `+5..+9` when SF mirrors
report `−5..−9`), is direct evidence for an asymmetric evaluation. The
sign asymmetry under colour flip is exactly what hypothesis 1
predicts.

### Suggested ordering for the follow-up work

Given the test01 data, the two open tracks both have concrete leads.
**The illegal-PV bug is tackled first, the Black-eval asymmetry
second** — even though the latter probably moves more ELO, fixing the
PV bug is structural hygiene with a known reproducer set already in
place, and silencing it removes a source of noise from any subsequent
strength comparison.

1. **Illegal-PV bug — already has regression tests, fix
   incrementally.**
   - `IllegalPvRegressionTest` now covers **three** distinct
     concrete positions, all reproducing the bug deterministically
     (see "Reproducers in `IllegalPvRegressionTest`" below).
   - Investigate Variant C's lead #1 (PV propagation after beta
     cutoff) and lead #2 (quiescence transition) with the legality
     check inlined into `PositionSearch` and `QuiescenceSearch`.
     Iteration depth at which each reproducer fires is small
     (3, 4, 8), so the offending node should be easy to locate
     once instrumented.
   - When the three current tests go green, add reproducers for the
     not-yet-covered shapes (length-1 promotion, length-2 forcing
     tails, repetitive king shuffles) before declaring the bug
     class closed.
2. **Black-eval asymmetry — most likely the bigger ELO lever.**
   - Pick 3–5 positions from round 3 (the table above) where myChess
     as Black reports a small negative and SF as White reports a
     large positive.
   - For each, build a unit test that evaluates the position with
     myChess and then evaluates the mirrored position (swap colours
     and ranks 1↔8) with myChess. If the two scores aren't
     sign-flipped equal (modulo small mobility / king-safety
     differences), the eval has an asymmetry — bisect into
     `WeightingFunction` term by term to find which contributes the
     gap.
   - Closely audit `PieceSquareTables` for non-mirrored entries.
     Antisymmetric construction (`white[sq] = -black[mirror(sq)]`)
     should be a tested invariant.

Once the PV-bug is silent, re-run a test02 SPRT match with the same
parameters as test01 to get a clean colour-asymmetry signal that
isn't contaminated by illegal-PV moves leaking into actual play.
That cleaner signal is the input for the Black-eval work in step 2.

## Reproducers in `IllegalPvRegressionTest`

Three concrete positions from the test01 run, each reproducing the
illegal-PV bug deterministically:

| Test method                                       | Source game           | Side  | Failing PV                                                    | First-failing iter |
|---------------------------------------------------|-----------------------|-------|---------------------------------------------------------------|-------------------:|
| `selfCheckEvasion_round1_blackToMove`             | Round 1, after 32. f4 | Black | `a4-a2 c5-f2 f1-f2 g2-g3 f2-f3 g3-h4 f3-f4 g4-g5`             |  #8 (depth 8)      |
| `selfCheckEvasion_round2_blackToMove`             | Round 2, after 14. Bxh7+ | Black | `g8-h7 d1-c2 b8-c6`                                          |  #3 (depth 3)      |
| `pinnedPieceViolation_round3_whiteToMove`         | Round 3, after 43...Bf8 | White | `d2-h6 a6-c5 c1-c5 f8-d6`                                    |  #4 (depth 4)      |

All three fail with the same error: **"leaves own king in check"** —
the move is pseudo-legal but, after being applied, the opponent could
capture the own king. Two of the three are classical check-evasion
failures (king was in check, the PV move fails to address it). The
third is a pinned-piece move (king was not in check, but the move
exposes it). The same `Moves.ILLEGAL` sentinel is supposed to catch
both classes inside `PositionSearch`; it doesn't.

The three reproducers cover **three different search depths** at which
the bug first surfaces (8, 3, 4), suggesting it is not depth-tied.
They cover both colours. None of them are flaky in repeated runs;
search is deterministic enough at these inputs that the same PVs come
out every time.

### Shapes not yet covered

The test01 run produced several other PV shapes that were not turned
into reproducers in this pass, because the source position could not
be cleanly identified from the cutechess output alone (concurrency 2,
warnings arrive interleaved between two running games):

- **Length-1 promotion PVs** like `Warning: PV:  f7f8q` (12+
  occurrences in cutechess game 31 / Round 16).
- **Length-2 forcing-move tails** like `Rxe8+ h2f3`, `Bxb4+ c6c5`,
  `Qf8# e2d1`.
- **Repetitive king-shuffle endings** in games 11 and 17 — same
  illegal move emitted from many sibling iterations.

These will become individual reproducers later, ideally driven from
the rebuilt-engine `[pv-validate]` log (which captures the search-root
FEN directly) rather than from PGN move-replay reconstruction.
