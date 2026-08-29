#!/usr/bin/env python3
"""Does king exposure predict the result? Measure it before building a term.

Three hand-crafted king-safety terms have measured net-negative in this project (−14.7,
−18.1, −57.5 Elo). Each time the diagnosis was "untuned" or "wrong magnitude". This asks the
question that comes before either: **does the signal carry information at all?**

The framing matters, and an earlier design of this step got it wrong. King safety is mostly
*prevention* — not opening your own pawn shield in the first place — and prevention does not
show up as a flip in a handful of sharp positions. A move that advances a shield pawn costs
0.2–0.5 pawns at the moment it is played and only becomes fatal twenty moves later. Measuring
over a corpus of *blunders* therefore filters the subject out: those corpora select for losses
of three pawns or more, i.e. for tactical accidents. What prevention looks like in data is a
**statistical tendency across many ordinary positions**.

So: over a large set of quiet positions labeled with the result of the game they came from,
and **holding material constant**, does the side with the weaker king shelter score worse?

For each position this computes, per side, the shield pawns, the files around the king without
one, and the pieces attacking and defending the king ring. Positions are grouped by the
white-minus-black differential of each feature, and each group's mean White score is reported.
A feature that carries information produces a monotone column; one that does not produces
noise around 0.5.

The mean score is also converted back to centipawns through the standard logistic used by
Texel tuning, which turns the table into a direct answer to "what is one shield pawn worth?".
That number is what all three failed attempts had to guess.

Material is controlled by restricting to positions within `--material-window` centipawns of
equal, because material dominates the result and would otherwise swamp everything else.

Usage::

    ../lichess-bot/venv/bin/python tools/king-safety-signal.py
    ../lichess-bot/venv/bin/python tools/king-safety-signal.py --limit 0 --material-window 100

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
OUTPUT = REPO_ROOT / "test-results" / "king-safety-signal.json"

DEFAULT_LIMIT = 300_000
DEFAULT_MATERIAL_WINDOW = 50

#: Same values the engine uses, so "material equal" means the same thing here and there.
PIECE_VALUE = {chess.PAWN: 100, chess.KNIGHT: 300, chess.BISHOP: 300,
               chess.ROOK: 500, chess.QUEEN: 1000, chess.KING: 0}

RESULT = {"1-0": 1.0, "1/2-1/2": 0.5, "0-1": 0.0}

#: Texel's logistic scaling constant. score = 1 / (1 + 10^(-cp / K)).
LOGISTIC_K = 400.0

#: Groups thinner than this are reported but excluded from the fitted centipawn value.
MIN_GROUP = 500

FEATURES = ("shield", "open_files", "attackers", "defenders")

#: Non-pawn material of both sides, in centipawns, splitting the corpus by game phase. The
#: split exists because the two phases want opposite things from a king: shelter in the
#: midgame, activity in the endgame. Measured together they cancel, and a real signal reads as
#: no signal — the same mistake as finding F1 in docs/king-safety.md, made once more in the
#: measuring tool rather than in the term.
PHASES = (("midgame", 4000, 99999), ("late-midgame", 2000, 4000), ("endgame", 0, 2000))

PROGRESS_EVERY = 50_000


def non_pawn_material(board):
    """Both sides' non-pawn material in centipawns — the phase proxy."""
    total = 0

    for piece in board.piece_map().values():
        if piece.piece_type not in (chess.PAWN, chess.KING):
            total += PIECE_VALUE[piece.piece_type]

    return total


def phase_of(board):
    for name, low, high in PHASES:
        if low <= non_pawn_material(board) < high:
            return name

    return PHASES[-1][0]


def material_balance(board):
    """White minus black, in centipawns."""
    total = 0

    for square, piece in board.piece_map().items():
        value = PIECE_VALUE[piece.piece_type]
        total += value if piece.color == chess.WHITE else -value

    return total


def king_features(board, color):
    """Shelter and pressure around `color`'s king."""
    king = board.king(color)

    if king is None:
        return None

    ring = [king] + [s for s in chess.SQUARES if chess.square_distance(king, s) == 1]
    enemy = not color
    attackers = set()
    defenders = set()

    for square in ring:
        attackers.update(board.attackers(enemy, square))
        defenders.update(board.attackers(color, square))

    file_index = chess.square_file(king)
    rank_index = chess.square_rank(king)
    direction = 1 if color == chess.WHITE else -1
    shield = 0

    for df in (-1, 0, 1):
        f = file_index + df

        if not 0 <= f <= 7:
            continue

        for dr in (1, 2):
            r = rank_index + direction * dr

            if not 0 <= r <= 7:
                continue

            piece = board.piece_at(chess.square(f, r))

            if piece is not None and piece.piece_type == chess.PAWN and piece.color == color:
                shield += 1
                break

    return {
        "shield": shield,
        "open_files": 3 - shield,
        "attackers": len(attackers),
        "defenders": len(defenders),
    }


def score_to_cp(score):
    """Inverse of the Texel logistic; returns None at the saturated ends."""
    if not 0.0 < score < 1.0:
        return None

    return LOGISTIC_K * math.log10(score / (1.0 - score))


def parse(line):
    """An EPD line into (board, white-POV result), or None if unusable."""
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
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT,
                        help="positions to read; 0 for all")
    parser.add_argument("--material-window", type=int, default=DEFAULT_MATERIAL_WINDOW,
                        help="keep positions within this many centipawns of equal material")
    parser.add_argument("--output", default=str(OUTPUT),
                        help="where to write the JSON report; lets several corpora be compared")
    args = parser.parse_args()

    groups = {phase: {name: defaultdict(lambda: [0, 0.0]) for name in FEATURES}
              for phase, _, _ in PHASES}
    read = 0
    kept = 0
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

            white = king_features(board, chess.WHITE)
            black = king_features(board, chess.BLACK)

            if white is None or black is None:
                continue

            kept += 1
            phase = phase_of(board)

            for name in FEATURES:
                bucket = white[name] - black[name]
                entry = groups[phase][name][bucket]
                entry[0] += 1
                entry[1] += outcome

    report = {"epd": args.epd, "read": read, "kept": kept,
              "material_window_cp": args.material_window, "features": {}}

    print(f"\nread {read:,}, kept {kept:,} at material within "
          f"±{args.material_window} cp\n", flush=True)

    summary = {}

    for phase, _, _ in PHASES:
        report["features"][phase] = {}
        total_positions = sum(c for name in (FEATURES[0],)
                              for c, _ in groups[phase][name].values())
        print(f"################ {phase} — {total_positions:,} positions ################\n")

        for name in FEATURES:
            rows = []

            for bucket in sorted(groups[phase][name]):
                count, total = groups[phase][name][bucket]
                mean = total / count
                rows.append({"diff": bucket, "positions": count, "mean_score": mean,
                             "implied_cp": score_to_cp(mean)})

            report["features"][phase][name] = rows
            print(f"=== {phase} / {name} (white minus black) ===")
            print(f"{'diff':>6}{'positions':>12}{'white score':>14}{'implied cp':>13}")

            for row in rows:
                cp = "—" if row["implied_cp"] is None else f"{row['implied_cp']:+.0f}"
                mark = "" if row["positions"] >= MIN_GROUP else "  (thin)"
                print(f"{row['diff']:>6}{row['positions']:>12,}{row['mean_score']:>14.4f}"
                      f"{cp:>13}{mark}")

            usable = [r for r in rows
                      if r["positions"] >= MIN_GROUP and r["implied_cp"] is not None]

            if len(usable) >= 2:
                span = usable[-1]["implied_cp"] - usable[0]["implied_cp"]
                steps = usable[-1]["diff"] - usable[0]["diff"]
                per_unit = span / steps if steps else 0.0
                summary[(phase, name)] = per_unit
                report["features"][phase][name + "_cp_per_unit"] = per_unit
                print(f"  -> {per_unit:+.0f} cp per unit across the usable range\n")
            else:
                print("  -> too few usable groups to fit\n")

    print("################ summary: cp per unit, by phase ################\n")
    print(f"{'feature':<14}" + "".join(f"{p:>16}" for p, _, _ in PHASES))

    for name in FEATURES:
        cells = "".join(f"{summary.get((p, name), float('nan')):>+16.0f}" for p, _, _ in PHASES)
        print(f"{name:<14}{cells}")

    print("\nA feature that is a real king-safety signal should be strongest in the midgame "
          "and fade — or invert — toward the endgame, where the king wants to be active "
          "rather than sheltered.")

    Path(args.output).write_text(json.dumps(report, indent=1), encoding="utf-8")
    print(f"-> {args.output}")
    print("\nA monotone column means the feature carries information. A column that wanders "
          "around 0.5000 means there is nothing to tune, and three failed attempts are "
          "explained.")


if __name__ == "__main__":
    main()
