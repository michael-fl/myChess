#!/usr/bin/env python3
"""Ask Stockfish for the move that *holds* in each re-probed anchor finding.

The scan of 2026-08-17 recorded, for every finding, the evaluation before the blunder and
after it — enough to prove a move was bad, but not enough to write a test. Every case in
`BlunderTest` names the refutation and the alternative ("which drops the rook on d8 to
26.Qxd8 …; 25...Nd7 holds"), and neither of those is in the findings file. This fills that
gap in one pass instead of 58 invocations of `probe-blunder.py`.

Three things per position, which is exactly what the JavaDoc of a case needs:

1. **The move that holds** — Stockfish's best, with its evaluation, so the test can say what
   should have been played instead of only what should not.
2. **The refutation** — the principal variation after the blunder actually played, which is
   the "because" in every case comment.
3. **A re-confirmation of the loss** at the same depth, so a finding that no longer
   reproduces under Stockfish 18 is caught rather than quoted.

Depth 22 by default, matching `probe-blunder.py`'s `DEFAULT_SF_DEPTH`, so numbers quoted in
a new test are commensurable with those in the existing ones. The original scan verified at
depth 20; the extra two plies are cheap here because only the selected positions are asked.

Writes one JSON object per line as each position finishes and skips on restart whatever is
already there.

Usage::

    ../lichess-bot/venv/bin/python tools/refute-anchor-findings.py
    ../lichess-bot/venv/bin/python tools/refute-anchor-findings.py --only reproducing --limit 10

@author Michael Fleischhauer
"""

import argparse
import json
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
REPROBE = REPO_ROOT / "test-results" / "anchor-reprobe-4.6.0.jsonl"
OUTPUT = REPO_ROOT / "test-results" / "anchor-refutations.jsonl"
STOCKFISH = "/opt/homebrew/bin/stockfish"

DEFAULT_DEPTH = 22

#: Plies of principal variation to keep. Enough to show why the move loses, short enough that
#: the case comment stays readable — the existing cases quote three to four moves.
PV_PLIES = 8


def load_reprobe(only):
    """The re-probed findings, filtered by outcome, worst damage first."""
    rows = [json.loads(line) for line in REPROBE.read_text(encoding="utf-8").splitlines() if line.strip()]

    if only == "reproducing":
        rows = [r for r in rows if r["reproduces_any"]]
    elif only == "repaired":
        rows = [r for r in rows if not r["reproduces_any"]]

    return sorted(rows, key=lambda r: -r["loss_cp"])


def already_done(path):
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
                continue

    return done


def describe(board, score):
    """Score from White's point of view, in centipawns, with mates kept recognizable."""
    if score is None:
        return None

    white = score.white()

    return white.score(mate_score=100000)


def san_line(board, moves):
    """A principal variation rendered in SAN, on a throwaway copy of the board."""
    probe = board.copy()
    out = []

    for move in moves[:PV_PLIES]:
        if not probe.is_legal(move):
            break

        out.append(probe.san(move))
        probe.push(move)

    return out


def analyse(engine, row, depth):
    board = chess.Board(row["fen"])
    limit = chess.engine.Limit(depth=depth)

    best = engine.analyse(board, limit)
    best_move = best["pv"][0] if best.get("pv") else None

    played = chess.Move.from_uci(row["blunder_uci"])
    after = board.copy()
    after.push(played)
    refutation = engine.analyse(after, limit)

    return {
        "fen": row["fen"],
        "blunder_san": row["blunder_san"],
        "blunder_uci": row["blunder_uci"],
        "anchor": row["anchor"],
        "move_number": row["move_number"],
        "reproduces_any": row["reproduces_any"],
        "depth": depth,
        "holds_san": board.san(best_move) if best_move else None,
        "holds_uci": best_move.uci() if best_move else None,
        "holds_line": san_line(board, best.get("pv", [])),
        "eval_before_cp": describe(board, best.get("score")),
        "eval_after_cp": describe(after, refutation.get("score")),
        "refutation_line": san_line(after, refutation.get("pv", [])),
        "scan_eval_before_cp": row["eval_before_cp"],
        "scan_eval_after_cp": row["eval_after_cp"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--depth", type=int, default=DEFAULT_DEPTH)
    parser.add_argument("--only", choices=["all", "reproducing", "repaired"], default="all")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()

    rows = load_reprobe(args.only)

    if args.limit:
        rows = rows[:args.limit]

    done = already_done(OUTPUT)
    todo = [r for r in rows if r["fen"] not in done]

    print(f"selected  : {len(rows)} ({args.only})", flush=True)
    print(f"already   : {len(rows) - len(todo)}", flush=True)
    print(f"to analyse: {len(todo)} at depth {args.depth}\n", flush=True)

    if not todo:
        print("nothing to do", flush=True)
        return

    engine = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    started = time.monotonic()

    try:
        with OUTPUT.open("a", encoding="utf-8") as out:
            for index, row in enumerate(todo, start=1):
                record = analyse(engine, row, args.depth)
                out.write(json.dumps(record) + "\n")
                out.flush()

                loss = (record["eval_before_cp"] - record["eval_after_cp"]) / 100
                elapsed_min = (time.monotonic() - started) / 60
                print(f"{index}/{len(todo)} {record['blunder_san']:<7} "
                      f"holds {str(record['holds_san']):<7} "
                      f"{record['eval_before_cp'] / 100:+7.2f} -> {record['eval_after_cp'] / 100:+7.2f} "
                      f"(loss {loss:5.2f})  ({elapsed_min:.1f} min)", flush=True)
    finally:
        engine.quit()

    print(f"\ndone in {(time.monotonic() - started) / 60:.1f} min -> {OUTPUT}", flush=True)


if __name__ == "__main__":
    main()
