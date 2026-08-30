#!/usr/bin/env python3
"""Place the KING_ATTACK_PENALTY curve by measurement instead of by intuition.

`tools/king-safety-signal.py` measured king danger per **attacker** — one more piece bearing
on the ring is worth about −34 cp in the midgame. The evaluation's table is not indexed by
attacker count, though: it is indexed by **attack units**, the material-weighted sum
`P1 N2 B2 R3 Q5` over the distinct pieces bearing on the enemy king's 3×3 zone. Two knights
are 2 attackers and 4 units; a queen and a rook are also 2 attackers but 8 units. So "−34 cp
per attacker" yields no table entry at all.

That gap is where every hand-placed curve in this project came from: one quantity was measured
and a different one was written down, with intuition in between. This closes it.

## What is measured

**The curve itself, in isolation.** In positions where the *opponent* has zero attack units,
the evaluation's contribution reduces to `f(own units) − f(0)`, so grouping those positions by
own units and reading the mean game result gives `f` directly, one entry per index. No fitting,
no assumption of a shape.

**The living range.** The distribution of attack-unit values over all positions. A table entry
that real play never asks for cannot help however carefully it is chosen — Audax's equivalent
measurement found its median attack at 6 units and the top of its curve reached in under 2 % of
games.

Both are split by game phase, because `king-safety-signal.py` established that king danger
inverts sign toward the endgame rather than merely fading.

Material is held within `--material-window` centipawns of equal throughout, for the same reason
as in the sibling tool: material dominates the result and would otherwise swamp everything.

Usage::

    ../lichess-bot/venv/bin/python tools/king-attack-curve.py
    ../lichess-bot/venv/bin/python tools/king-attack-curve.py --epd tuning-data/human-dense.epd

@author Michael Fleischhauer
"""

import argparse
import json
import math
import sys
import time
from collections import defaultdict
from pathlib import Path

try:
    import chess
except ImportError as missing:
    sys.exit(f"missing dependency: {missing.name}. Run this with a Python that has "
             f"python-chess, e.g. ../lichess-bot/venv/bin/python")

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_EPD = REPO_ROOT / "tuning-data" / "hybrid.epd"
DEFAULT_OUTPUT = REPO_ROOT / "test-results" / "king-attack-curve.json"

DEFAULT_MATERIAL_WINDOW = 50

PIECE_VALUE = {chess.PAWN: 100, chess.KNIGHT: 300, chess.BISHOP: 300,
               chess.ROOK: 500, chess.QUEEN: 1000, chess.KING: 0}

#: Attack-unit weights, identical to WeightingFunction on branch attack-units. The king
#: contributes nothing and is therefore never counted as an attacker.
ATTACK_UNIT = {chess.PAWN: 1, chess.KNIGHT: 2, chess.BISHOP: 2,
               chess.ROOK: 3, chess.QUEEN: 5, chess.KING: 0}

RESULT = {"1-0": 1.0, "1/2-1/2": 0.5, "0-1": 0.0}

LOGISTIC_K = 400.0
MIN_GROUP = 400
PHASES = (("midgame", 4000, 99999), ("late-midgame", 2000, 4000), ("endgame", 0, 2000))
PROGRESS_EVERY = 100_000


def material_balance(board):
    total = 0

    for piece in board.piece_map().values():
        value = PIECE_VALUE[piece.piece_type]
        total += value if piece.color == chess.WHITE else -value

    return total


def non_pawn_material(board):
    return sum(PIECE_VALUE[p.piece_type] for p in board.piece_map().values()
               if p.piece_type not in (chess.PAWN, chess.KING))


def phase_of(board):
    material = non_pawn_material(board)

    for name, low, high in PHASES:
        if low <= material < high:
            return name

    return PHASES[-1][0]


def attack_units(board, attacker):
    """Attack units `attacker` accumulates on the enemy king's 3×3 zone.

    Each piece counts once however many zone squares it bears on — the origin-square dedup the
    evaluation performs, reproduced here by taking the union of attacker squares first.
    """
    king = board.king(not attacker)

    if king is None:
        return 0

    zone = [king] + [s for s in chess.SQUARES if chess.square_distance(king, s) == 1]
    pieces = set()

    for square in zone:
        pieces.update(board.attackers(attacker, square))

    total = 0

    for square in pieces:
        piece = board.piece_at(square)

        if piece is not None:
            total += ATTACK_UNIT[piece.piece_type]

    return total


def score_to_cp(score):
    if not 0.0 < score < 1.0:
        return None

    return LOGISTIC_K * math.log10(score / (1.0 - score))


def parse(line):
    parts = line.strip().split(" c9 ")

    if len(parts) != 2:
        return None

    outcome = RESULT.get(parts[1].strip().strip(';').strip('"'))

    if outcome is None:
        return None

    try:
        return chess.Board(parts[0].strip() + " 0 1"), outcome
    except ValueError:
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--epd", default=str(DEFAULT_EPD))
    parser.add_argument("--limit", type=int, default=0, help="positions to read; 0 for all")
    parser.add_argument("--material-window", type=int, default=DEFAULT_MATERIAL_WINDOW)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    # curve[phase][own units] over positions where the opponent has none
    curve = {p: defaultdict(lambda: [0, 0.0]) for p, _, _ in PHASES}
    # how often each unit count occurs at all, per phase
    seen = {p: defaultdict(int) for p, _, _ in PHASES}
    read = kept = 0
    started = time.monotonic()

    with open(args.epd, encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            if args.limit and read >= args.limit:
                break

            read += 1

            if read % PROGRESS_EVERY == 0:
                print(f"  {read:,} read, {kept:,} kept "
                      f"({(time.monotonic() - started) / 60:.1f} min)", flush=True)

            parsed = parse(line)

            if parsed is None:
                continue

            board, outcome = parsed

            if abs(material_balance(board)) > args.material_window:
                continue

            kept += 1
            phase = phase_of(board)
            white = attack_units(board, chess.WHITE)
            black = attack_units(board, chess.BLACK)

            seen[phase][white] += 1
            seen[phase][black] += 1

            # Isolate f: with the opponent at zero, white − black reduces to f(own) − f(0).
            if black == 0:
                entry = curve[phase][white]
                entry[0] += 1
                entry[1] += outcome

            if white == 0:
                entry = curve[phase][black]
                entry[0] += 1
                entry[1] += 1.0 - outcome

    report = {"epd": args.epd, "read": read, "kept": kept,
              "material_window_cp": args.material_window, "phases": {}}

    print(f"\nread {read:,}, kept {kept:,} at material within ±{args.material_window} cp\n",
          flush=True)

    for phase, _, _ in PHASES:
        total_seen = sum(seen[phase].values())
        rows = []
        baseline = None

        for units in sorted(curve[phase]):
            count, score_sum = curve[phase][units]
            mean = score_sum / count
            cp = score_to_cp(mean)

            if units == 0 and cp is not None:
                baseline = cp

            rows.append({"units": units, "positions": count, "mean_score": mean,
                         "raw_cp": cp,
                         "share_of_all": 100.0 * seen[phase].get(units, 0) / total_seen
                         if total_seen else 0.0})

        for row in rows:
            row["curve_cp"] = None if row["raw_cp"] is None or baseline is None \
                else row["raw_cp"] - baseline

        report["phases"][phase] = {"samples": total_seen, "rows": rows}

        print(f"################ {phase} — {total_seen:,} king samples ################")
        print(f"{'units':>6}{'positions':>12}{'white score':>13}{'curve cp':>11}"
              f"{'% of all':>10}")

        for row in rows:
            cp = "—" if row["curve_cp"] is None else f"{row['curve_cp']:+.0f}"
            mark = "" if row["positions"] >= MIN_GROUP else "  (thin)"
            print(f"{row['units']:>6}{row['positions']:>12,}{row['mean_score']:>13.4f}"
                  f"{cp:>11}{row['share_of_all']:>9.1f}%{mark}")

        usable = [r for r in rows if r["positions"] >= MIN_GROUP and r["curve_cp"] is not None]

        if usable:
            top = min(usable, key=lambda r: r["curve_cp"])
            print(f"  -> deepest reliable entry: {top['curve_cp']:+.0f} cp at {top['units']} "
                  f"units, reached in {top['share_of_all']:.1f} % of king samples")

        thin = sum(r["share_of_all"] for r in rows if r["positions"] < MIN_GROUP)
        print(f"  -> {thin:.1f} % of samples sit on indices too thin to place\n")

    Path(args.output).write_text(json.dumps(report, indent=1), encoding="utf-8")
    print(f"-> {args.output}")
    print("\n'curve cp' is the KING_ATTACK_PENALTY entry for that index, relative to index 0,\n"
          "read from the ATTACKER's side: positive means the attacking side scores better, which\n"
          "is the sign the evaluation stores.\n\n"
          "Caveat: isolating f by requiring the opponent to have zero attack units does not\n"
          "control for general activity. A side with nothing bearing on the enemy king tends to\n"
          "be the less active side overall, so part of what this column shows may be activity\n"
          "rather than king pressure. Material is held equal; activity is not.")


if __name__ == "__main__":
    main()
