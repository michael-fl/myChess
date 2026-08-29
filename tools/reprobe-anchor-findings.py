#!/usr/bin/env python3
"""Re-probe the 2026-08-17 anchor-scan findings against the current build.

`docs/testing.md` § 11.3 calls the unread findings the largest known defect reserve in the
project — 81 verified, only five ever worked up — and names the reason they cannot simply be
pinned as tests: they were found on **v4.4.1**, and v4.6.0 changed which evaluation regime a
subtree runs under. A finding is a candidate, not a defect, until the current engine has been
asked again.

This asks. For every finding whose position was **not already lost** (§ 11.3's own pick
criterion: a phase starting at −3.16 says nothing about the mistake that lost the game), it
runs the current build at a range of depths and records which move comes back. The output
splits the set in two, and the two halves have different owners:

* **repaired** — the engine no longer plays the losing move. That is a regression test waiting
  to be written: it passes today and pins the behavior against a future change.
* **still reproducing** — the engine plays it again. That is a defect report, not a test; a
  test asserting the correct move would fail on commit.

Deliberately no Stockfish. The truth for each position was already established at depth 20 in
the original scan and is carried in the findings file; asking again would cost an hour and
change nothing. What is unknown is only what *myChess* does now.

Writes one JSON object per line as each position finishes, and skips on restart whatever is
already in the file — a run of this length must not lose everything when it is interrupted.

Usage::

    ../lichess-bot/venv/bin/python tools/reprobe-anchor-findings.py
    ../lichess-bot/venv/bin/python tools/reprobe-anchor-findings.py --depths 8,10 --limit 5

@author Michael Fleischhauer
"""

import argparse
import json
import logging
import subprocess
import sys
import time
from pathlib import Path

try:
    import chess
    import chess.engine
except ImportError as missing:
    sys.exit(f"missing dependency: {missing.name}. Run this with a Python that has "
             f"python-chess, e.g. ../lichess-bot/venv/bin/python")

REPO_ROOT = Path(__file__).resolve().parent.parent
FINDINGS = REPO_ROOT / "test-results" / "blunder-scan-findings.json"
OUTPUT = REPO_ROOT / "test-results" / "anchor-reprobe-4.6.0.jsonl"
#: The repo-root launcher, which loads from target/classes — i.e. whatever was last compiled,
#: which is the point here. The version-pinned copies under versions/ would answer for an old
#: build, which is precisely the question this script exists to move past.
MYCHESS_UCI = REPO_ROOT / "mychess-uci.sh"

DEFAULT_DEPTHS = "8,10,12"

#: Positions starting below this are already lost and say nothing about the mistake that lost
#: the game — § 11.3's pick criterion, in centipawns.
NOT_YET_LOST_CP = -100

#: Safety cap per search, in seconds. A depth-12 midgame search must not stall the whole run.
SEARCH_TIMEOUT_S = 60


def qualifying(findings):
    """The findings worth re-probing, worst damage first."""
    kept = [f for f in findings if f["eval_before_cp"] >= NOT_YET_LOST_CP]

    return sorted(kept, key=lambda f: -f["loss_cp"])


def already_done(path):
    """FENs present in a previous run's output, so a restart resumes rather than repeats."""
    if not path.exists():
        return set()

    done = set()

    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()

            if not line:
                continue

            try:
                done.add(json.loads(line)["fen"])
            except (ValueError, KeyError):
                # A truncated final line from an interrupted run — that position simply
                # gets probed again.
                continue

    return done


def probe(engine, finding, depths):
    """Run the current build at each depth and report what it plays."""
    board = chess.Board(finding["fen"])
    blunder = chess.Move.from_uci(finding["uci"])
    per_depth = []

    for depth in depths:
        started = time.monotonic()
        result = engine.play(
            board,
            chess.engine.Limit(depth=depth, time=SEARCH_TIMEOUT_S),
            info=chess.engine.INFO_SCORE,
        )
        elapsed = time.monotonic() - started
        move = result.move
        score = result.info.get("score")
        per_depth.append({
            "depth": depth,
            "move": move.uci() if move else None,
            "san": board.san(move) if move else None,
            "reproduces": move == blunder,
            "score_cp": score.relative.score(mate_score=100000) if score else None,
            "seconds": round(elapsed, 2),
        })

    return per_depth


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--depths", default=DEFAULT_DEPTHS, help="comma-separated search depths")
    parser.add_argument("--limit", type=int, help="probe only the first N findings")
    args = parser.parse_args()

    # python-chess relays every engine stderr line at INFO, and myChess logs one line per
    # deepening iteration — thousands of lines that bury the per-position verdict this run
    # exists to produce. The engine's own log file keeps them.
    logging.getLogger("chess.engine").setLevel(logging.WARNING)

    depths = [int(d) for d in args.depths.split(",")]
    findings = qualifying(json.loads(FINDINGS.read_text(encoding="utf-8")))

    if args.limit:
        findings = findings[:args.limit]

    done = already_done(OUTPUT)
    todo = [f for f in findings if f["fen"] not in done]

    print(f"qualifying findings : {len(findings)}  (of "
          f"{len(json.loads(FINDINGS.read_text(encoding='utf-8')))} total)", flush=True)
    print(f"already probed      : {len(findings) - len(todo)}", flush=True)
    print(f"to probe            : {len(todo)} at depths {depths}", flush=True)
    print(flush=True)

    if not todo:
        print("nothing to do", flush=True)
        return

    # DEVNULL rather than the logging level alone: myChess's launcher tees its stderr to its own
    # log file *and* back to the inherited stream, so raising the logger's level does not stop the
    # lines from arriving. The engine's log file keeps everything.
    engine = chess.engine.SimpleEngine.popen_uci(str(MYCHESS_UCI), stderr=subprocess.DEVNULL)
    started = time.monotonic()

    try:
        with OUTPUT.open("a", encoding="utf-8") as out:
            for index, finding in enumerate(todo, start=1):
                per_depth = probe(engine, finding, depths)
                record = {
                    "fen": finding["fen"],
                    "blunder_uci": finding["uci"],
                    "blunder_san": finding["san"],
                    "anchor": finding["anchor"],
                    "move_number": finding["move_number"],
                    "loss_cp": finding["loss_cp"],
                    "eval_before_cp": finding["eval_before_cp"],
                    "eval_after_cp": finding["eval_after_cp"],
                    "own_cp_then": finding["own_cp"],
                    "per_depth": per_depth,
                    "reproduces_any": any(d["reproduces"] for d in per_depth),
                }
                out.write(json.dumps(record) + "\n")
                out.flush()

                verdict = "REPRODUCES" if record["reproduces_any"] else "repaired  "
                elapsed_min = (time.monotonic() - started) / 60
                print(f"{index}/{len(todo)} {verdict} {finding['san']:<7} "
                      f"loss {finding['loss_cp'] / 100:5.2f}  "
                      f"plays {[d['san'] for d in per_depth]}  "
                      f"({elapsed_min:.1f} min elapsed)", flush=True)
    finally:
        engine.quit()

    print(f"\ndone in {(time.monotonic() - started) / 60:.1f} min -> {OUTPUT}", flush=True)


if __name__ == "__main__":
    main()
