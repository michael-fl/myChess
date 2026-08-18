#!/usr/bin/env python3
"""
Measure how much myChess actually loses in the STS positions it scored zero on.

WHY THIS STEP EXISTS. An STS score of zero means the played move is not among the ten
candidates the suite annotates -- it does **not** mean the move is bad. The ``c8`` point
values are a *ranking, rescaled per position*: the best move is always worth 100 whether
it leads the second by a tenth of a pawn or by three. So the misses list from
``StsRunner`` cannot be turned into characterization tests directly; some of those 87
positions are near-equal, where landing outside a top-ten list costs nothing real.

This script turns the ranking into a magnitude by asking Stockfish for the centipawn loss
of the move myChess played. It deliberately **reuses the measurement primitive** of
``lichess-blunder-scan.py`` (``evaluate_move``), so the losses here are directly
comparable to the ones in the lichess and anchor-bracket scans rather than being a third
private definition of "blunder".

Input is the archived runner output plus the suite file: every per-position line carries
``played <move>`` and ``pts <n>``, and the lines with ``pts 0`` are exactly the misses.
(The runner's own misses block is capped at 40 entries, so it is not the input here.)

INTERPRETER. ``python-chess`` is not installed in any system python on this machine; it
comes with the lichess-bot virtualenv, which is what the other scanners here use too:

    PY=/Users/mf/_PRIVAT_/New-Stuff/lichess-bot/venv/bin/python3

Usage:
    $PY tools/scan-sts-misses.py                               # depth 20, all 87
    $PY tools/scan-sts-misses.py --depth 24 --workers 4
    $PY tools/scan-sts-misses.py --run test-results/sts-4.4.2-d8.txt

Output: a ranked table on stdout plus a JSON file next to the run for later reference.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import multiprocessing as mp
import re
from pathlib import Path

import chess
import chess.engine

REPO_ROOT = Path(__file__).resolve().parent.parent
LICHESS_SCANNER = REPO_ROOT / "tools" / "lichess-blunder-scan.py"
SUITE = REPO_ROOT / "src" / "test" / "resources" / "sts" / "STS1-STS15_LAN_v6.epd"
DEFAULT_RUN = REPO_ROOT / "test-results" / "sts-4.4.2-d8.txt"
STOCKFISH = "/opt/homebrew/bin/stockfish"

DEFAULT_DEPTH = 20
DEFAULT_WORKERS = 6

#: EPD counters, which the suite omits (it ships a four-field FEN).
EPD_COUNTER_SUFFIX = " 0 1"

#: Loss above which a miss is worth writing a characterization test for. One pawn is the
#: threshold used by the lichess scan for a "notable" loss; below it the move is a
#: preference, not a defect.
TEST_WORTHY_CP = 100

#: A scored line from StsRunner's per-position output.
#:
#: The label must be captured non-greedily up to " played ", NOT as ``\S+``: eleven of the
#: fifteen theme names contain spaces ("Open Files and Diagonals.005", "King Activity.023").
#: A ``\S+`` label silently matched only the four single-word themes and quietly dropped 60
#: of the 87 misses -- with King Activity, the theme this scan exists for, entirely absent.
LINE_PATTERN = re.compile(r"^\s*\d+/\d+\s+(.+?)\s+played\s+(\S+)\s+pts\s+(\d+)\s")

#: Misses expected in the archived 4.4.2 run; a guard against the truncation above.
EXPECTED_MISSES = 87

_ENGINE: chess.engine.SimpleEngine | None = None
_SCANNER = None
_DEPTH = DEFAULT_DEPTH


def scanner():
    """The lichess scanner module, imported for its measurement primitive."""
    global _SCANNER

    if _SCANNER is None:
        spec = importlib.util.spec_from_file_location("blunder_scan", LICHESS_SCANNER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _SCANNER = module

    return _SCANNER


def load_suite() -> dict[str, dict]:
    """Map ``<theme>.<nnn>`` to the position's FEN, best move, and theme number."""
    positions = {}

    for line in SUITE.read_text().splitlines():
        line = line.strip()

        if not line:
            continue

        best_move_at = line.index(" bm ")
        fen = line[:best_move_at].strip() + EPD_COUNTER_SUFFIX
        identifier = re.search(r'id "([^"]*)"', line).group(1)
        candidates = re.search(r'c9 "([^"]*)"', line).group(1).split()
        theme = int(re.search(r"\(v(\d+)\.", identifier).group(1))
        label = identifier[identifier.index(") ") + 2:]

        positions[label] = {"fen": fen, "best": candidates[0], "theme": theme,
                            "candidates": candidates}

    return positions


def read_scored(run: Path, suite: dict[str, dict], max_points: int) -> list[dict]:
    """
    Every position the run scored at most ``max_points`` on, joined with its suite entry.

    ``max_points=0`` is the misses. A higher bound reaches the *low-score band*, which needs
    exactly the same Stockfish treatment for exactly the same reason: a move worth 2 of 100
    points can be objectively equal to the best one. `King Activity.100` is the case that
    proved it -- Stockfish 18 scores both myChess's 2-point move and the suite's 100-point
    move at 0.00, so the point gap is Stockfish 15 disagreeing with Stockfish 18, not a
    defect. Selecting from point values alone would have made it a test.
    """
    scored = []

    for line in run.read_text().splitlines():
        match = LINE_PATTERN.match(line)

        if not match:
            continue

        label, played, points = match.group(1), match.group(2), int(match.group(3))

        if points > max_points:
            continue

        entry = suite.get(label)

        if entry is None:
            raise SystemExit(f"run refers to a position the suite does not have: {label}")

        scored.append({"label": label, "played": played, "points": points, **entry})

    return scored


def init_worker(depth: int) -> None:
    global _ENGINE, _DEPTH

    _DEPTH = depth
    _ENGINE = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    _ENGINE.configure({"Threads": 1, "Hash": 256})


def measure(work: dict) -> dict:
    """Centipawn loss of the played move, and of the suite's best move for reference."""
    board = chess.Board(work["fen"])
    played = scanner().evaluate_move(_ENGINE, work["fen"], work["played"], board.turn, _DEPTH)
    best = scanner().evaluate_move(_ENGINE, work["fen"], work["best"], board.turn, _DEPTH)

    return {**work,
            "loss_cp": played["loss_cp"],
            "eval_before_cp": played["eval_before_cp"],
            "eval_after_played_cp": played["eval_after_cp"],
            "eval_after_best_cp": best["eval_after_cp"],
            # What the suite's own best move gives up, if anything. A large value here
            # means the position is simply sharp, not that myChess erred.
            "best_loss_cp": best["loss_cp"],
            "depth": _DEPTH}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=DEFAULT_RUN)
    parser.add_argument("--depth", type=int, default=DEFAULT_DEPTH)
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS)
    parser.add_argument("--max-points", type=int, default=0,
                        help="include positions scoring up to this many points (0 = the "
                             "misses only; 20 adds the low-score band)")
    args = parser.parse_args()

    suite = load_suite()
    scored = read_scored(args.run, suite, args.max_points)

    print(f"{len(scored)} positions scoring 0-{args.max_points} from {args.run.name}, "
          f"Stockfish depth {args.depth} on {args.workers} workers")

    # Cross-check against the run's own footer rather than trusting the parse. A regex that
    # quietly matches a subset produces a plausible-looking ranking over the wrong data.
    if args.max_points == 0:
        reported = re.search(r"Misses \(0 pts\) : ([\d,]+)", args.run.read_text())

        if reported:
            expected = int(reported.group(1).replace(",", ""))

            if expected != len(scored):
                raise SystemExit(f"parsed {len(scored)} misses but the run reports {expected} "
                                 f"-- the line pattern is dropping positions")

    with mp.Pool(args.workers, initializer=init_worker, initargs=(args.depth,)) as pool:
        measured = pool.map(measure, scored)

    measured.sort(key=lambda finding: -finding["loss_cp"])

    print()
    print(f"{'loss':>7}  {'thm':>3}  {'position':<44} {'played':<6} {'best':<6}  before -> after")
    print("-" * 104)

    for finding in measured:
        print(f"{finding['loss_cp'] / 100:>6.2f}  {finding['theme']:>3}  {finding['label']:<44} "
              f"{finding['played']:<6} {finding['best']:<6}  "
              f"{finding['eval_before_cp'] / 100:>+6.2f} -> {finding['eval_after_played_cp'] / 100:>+6.2f}"
              f"  (best {finding['eval_after_best_cp'] / 100:>+6.2f})")

    worthy = [f for f in measured if f["loss_cp"] >= TEST_WORTHY_CP]
    noise = len(measured) - len(worthy)

    print()
    print(f"{len(worthy)} of {len(measured)} misses lose at least "
          f"{TEST_WORTHY_CP / 100:.0f} pawn -- those are the candidates for a test.")
    print(f"{noise} lose less than that: the move is a preference, not a defect, and a "
          f"zero there is ranking noise.")

    by_theme: dict[int, int] = {}

    for finding in worthy:
        by_theme[finding["theme"]] = by_theme.get(finding["theme"], 0) + 1

    print("Test-worthy misses per theme: "
          + ", ".join(f"{theme}:{count}" for theme, count in sorted(by_theme.items())))

    suffix = "-miss-losses.json" if args.max_points == 0 else f"-losses-to-{args.max_points}pt.json"
    out = args.run.with_name(args.run.stem + suffix)
    out.write_text(json.dumps(measured, indent=1))
    print(f"\nwrote {out}")


if __name__ == "__main__":
    main()
