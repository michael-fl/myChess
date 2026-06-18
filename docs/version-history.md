# myChess version history

Compact, chronological summary of myChess releases — what changed, the measured Elo delta where available, and the rough CCRL-Blitz strength estimate at each release.

Coverage starts at **v3.0.0** (May 2026), when the two earlier engine variants (`engines/v1`, `engines/v2`) were removed and `MyChessEngine` became the sole engine. The 2019/2020 v1.0.0 line in git history belongs to that earlier, now-deleted lineage.

| Version | Datum | Hauptänderungen | Δ Elo (gemessen) | ~CCRL Blitz |
|---|---|---|---|---|
| **3.0.0** | 2026-05-23 | First release of the current single-engine line. Adds [Log utility](search.md#75-packed-int-move-representation) and stderr routing; refactors that consolidated the search around `MyChessEngine`. | — | ~1440 (rough) |
| **3.1.x** | 2026-05-26 – 31 | UCI protocol handler ([§ 12.9](roadmap.md#129-uci-protocol--m-12-days-no-elo-directly-but-unblocks-gui--measurement)), FEN importer, opening-book lookup with throttled logging, skip-hopeless-iteration heuristic ([§ 6.5.1](search.md#651-skip-hopeless-iteration-heuristic)), depth cap at 64, self-check rejection in the search. 3.1.2 = cherry-picked self-check-before-draw fix. | **Anchor: ~1441** measured against Pulse 1.7.3 (test12, TC 40/120, 400 games, ≈ Pulse − 64 ± 31 Elo at Blitz=1505) | **~1441 (anchor)** |
| **3.2.0** | 2026-06-01 | Chess960 (Fischer Random) support: FEN/Shredder-FEN import, 960 castling in MoveGenerator, king-captures-rook UCI form, position-table for 960 starts. | 0 on standard chess (variant feature) | ~1441 |
| **3.3.0** | 2026-06-03 | Replaced `calculateOpeningState` with tuned pawn PST. (Released as -SNAPSHOT; rolled into 3.4.0.) | — (unmeasured) | ~1441 ± 15 |
| **3.4.0** | 2026-06-04 | Mobility weight v2 (`f81f7bc`); switched alpha-beta and quiescence from fail-hard to **fail-soft** ([§ 12.13](roadmap.md#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--done)) — neutral by design, prerequisite for TT and aspiration windows. | ~0 (fail-soft refactor is by-design Elo-neutral) | ~1441 ± 15 |
| **3.5.0** | 2026-06-05 | `threadWeightFactor` tweaked 0.10 → 0.05; iterative-deepening depth cap; PV-validation guard ([`PositionSearch.alphaBetaSearchPre`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java)); generation-guard against stale UCI iteration events. | — (correctness fixes, small +ve expected) | ~1441 ± 15 |
| **3.5.2** | 2026-06-10 | Doubled-pawn detection rewritten (full-file scan, penalty −0.15), separated from pawn connection-quality. Investigation results: connection-quality net null ([§ 12.15](roadmap.md#1215-discontinue-the-pawn-structure-connection-quality-investigation--done)); doubled-pawn isolated change measured **−5.6 ± 21.3 Elo** (noise, merged for correctness). | −5.6 ± 21.3 (within noise) | ~1441 ± 20 |
| **3.6.0** | 2026-06-14 | **Hanging-pieces eval term** ([§ 12.19](roadmap.md#1219-add-hanging-pieces-penalty-to-the-evaluation-function--done-28-elo)) — first decisive successful eval addition after the long pawn-structure investigation series and three eval-removal closures (§§ 12.16–12.18). | **+28.1 ± 20.5** (LOS 99.6 %, SPRT H1 at 867 games) | **~1469** |
| **4.0.0** | 2026-06-17 | **Transposition table** ([§ 12.1](roadmap.md#121-transposition-table--done-93-elo)) — first TT implementation, depth-preferred-EXACT replacement, default 2²⁰ entries (~50 MB), TT-move ordering in `MoveSorterImpl`, `ucinewgame` clears, mate-score depth-adjustment in `WeightingFunction.scoreToTT/scoreFromTT`. | **+92.7 ± 15.2** (LOS 100 %, 1600-game fixed-N match) | **~1562** |
| **4.0.1** | 2026-06-18 | TT default size 2²⁰ → 2²² (1 M → 4 M entries, ~50 MB → ~200 MB) to reduce mid-depth eviction conflicts at TC 40/60; JVM wrapper Xmx 256 → 512 MB. Test fix: `ucinewgame` added to UciHandlerTest castle tests for state isolation. | (SPRT running 2026-06-18) | ~1572-1577 (expected +10-15) |

## Notes on the estimated CCRL Blitz column

- The **anchor** is the test12 measurement of myChess 3.1.x against Pulse 1.7.3 (CCRL Blitz = 1505 as of 2026-05): myChess scored Pulse − 64 ± 31 Elo at TC 40/120 over 400 games. That places 3.1.x at **~1441 CCRL Blitz**, with ±31 Elo absolute uncertainty.
- Versions 3.2.0–3.5.2 carry the anchor forward because every intervening change was either a non-strength variant (Chess960), a neutral-by-design refactor (fail-soft), or an investigation whose own measurement was noise / null (doubled-pawn −5.6 ± 21, connection-quality null). Net assumption: **3.1.x ≈ 3.5.2 ± 15 Elo**.
- 3.6.0 = 3.5.2 + 28.1. 4.0.0 = 3.6.0 + 92.7. Both deltas were measured at TC 40/60 SPRT / fixed-N matches against the immediately-prior version, so they propagate cleanly.
- 4.0.1 is the only forward-looking estimate; the test against 4.0.0 was still running at file-creation time.

**Absolute uncertainty.** The propagated 95 %-CI on 4.0.0 is roughly `±31 (anchor) + ±20 (3.6.0) + ±15 (4.0.0)` ≈ **±66 Elo** under naive addition, or **±~40 Elo under independent-error addition** (sqrt of sum of squares). The 1562 estimate could be anywhere from ~1520 to ~1600 in CCRL-Blitz terms. The relative deltas between versions are far better-known than the absolute number.

To tighten the absolute number, re-run a fresh measurement on the current `master` and reset the anchor. The canonical recipe is documented as [§ *Concrete recipe: 4-engine anchor-bracket measurement*](myChess-ELO-measurement.md#concrete-recipe-4-engine-anchor-bracket-measurement) — four cutechess matches against Pulse / TSCP / DoctorB / PurplePanda at TC 40/120 + Ordo combination with three anchors. Expected result at v4.0.x: ~1543 ± 18 Ordo-Elo.
