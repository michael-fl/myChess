#!/usr/bin/env python3
"""Measure what a king-safety term would have to be worth to fix the depth-stable cases.

`docs/king-safety.md` names seven depth-stable evaluation defects as the instrument for the
next attempt, and the plan rests on an assumption nobody has checked: that these positions
are actually about **king danger**, and that a danger penalty of a plausible size would flip
myChess's decision. Three previous attempts failed on exactly this quantity — one term was
too weak, one was four times too strong, and none of them measured what was needed.

Per case this reports three things.

1. **Is there danger, and whose?** Attackers on each king's 3x3 ring, by piece type, for both
   sides — plus shield pawns and open files toward the king. If the side to move is not the
   one under pressure, a king-danger penalty cannot be what fixes the case, whatever the
   family label says.

2. **How wrong is myChess?** Its evaluation and chosen move at a fixed depth, against
   Stockfish's verdict and best move.

3. **The margin that matters.** myChess's own evaluation of the move it picks, minus its
   evaluation of Stockfish's move, both measured by making the move and searching the reply.
   That difference is the size of the swing a term has to produce. A penalty smaller than the
   margin cannot change the choice no matter how correct it is.

The margin is deliberately measured through myChess and not through Stockfish: the question is
not "how bad is the move" but "how much would *this engine* have to change its mind".

Writes one JSON object per line as each case completes.

Usage::

    ../lichess-bot/venv/bin/python tools/king-safety-margins.py
    ../lichess-bot/venv/bin/python tools/king-safety-margins.py --depth 12

@author Michael Fleischhauer
"""

import argparse
import json
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
OUTPUT = REPO_ROOT / "test-results" / "king-safety-margins.jsonl"
MYCHESS_UCI = REPO_ROOT / "mychess-uci.sh"
STOCKFISH = "/opt/homebrew/bin/stockfish"

DEFAULT_DEPTH = 10
STOCKFISH_DEPTH = 24
SEARCH_TIMEOUT_S = 120

#: The seven depth-stable evaluation defects of roadmap 12.21, with the side whose king the
#: family claims is the problem. `expected_victim` is the *claim under test*, not an input to
#: the measurement — the attacker counts either support it or they do not.
CASES = [
    {"id": "qd2", "fen": "rn1q1r2/4n2k/2p1Bp1p/b6R/8/2P4Q/Pp3PPP/5RK1 b - - 1 23",
     "expected_victim": "black", "note": "ignores its own king file"},
    {"id": "hxg4", "fen": "3rk2r/1p3pp1/pNb1p3/3pP1q1/3Q2p1/4P2P/PPP5/3R1RK1 w k - 0 21",
     "expected_victim": "white", "note": "recaptures instead of trading queens"},
    {"id": "captureOnF6", "fen": "r2r2k1/1p1qbppp/3p1N2/2nPp3/p1P5/P3BB2/1P3PPP/1R1Q1RK1 b - - 0 18",
     "expected_victim": "black", "note": "recaptures with the g-pawn, opening its own king"},
    {"id": "qb4", "fen": "r1b1r2k/pppp1p1p/5p2/5N1Q/2q5/6R1/2P2PPP/2R3K1 b - - 7 22",
     "expected_victim": "black", "note": "three pawns up and lost; gives up f7's only defender"},
    {"id": "bxd4", "fen": "r7/P1p2k2/2p1pb2/2P2p2/3PpP2/2q1P1Qp/7P/6RK b - - 7 55",
     "expected_victim": "black", "note": "anchor corpus, also an adopted BlunderTest case"},
    {"id": "keBKOXd1", "fen": "N4Q2/1p1kn2r/p2b1p2/2p5/8/5b1P/PPPPnPP1/R1B2R1K w - - 0 19",
     "expected_victim": "white", "note": "reads +5.00 while mated in six"},
    {"id": "qa3", "fen": "4r1k1/1b1r1pp1/p1p2b1p/2p1pN2/P2pP1PP/qP1P1N2/2PQ1PK1/3RR3 w - - 3 22",
     "expected_victim": "black", "note": "pawn storm the evaluation cannot price"},
]


def king_ring(board, color):
    """The king's square plus its up-to-eight neighbours."""
    king = board.king(color)

    if king is None:
        return []

    return [king] + [s for s in chess.SQUARES if chess.square_distance(king, s) == 1]


def danger_profile(board, color):
    """How exposed `color`'s king is: who attacks its ring, and what cover it has."""
    king = board.king(color)
    ring = king_ring(board, color)
    enemy = not color
    by_type = {}
    distinct = set()

    for square in ring:
        for attacker in board.attackers(enemy, square):
            piece = board.piece_at(attacker)

            if piece is None:
                continue

            name = chess.piece_name(piece.piece_type)
            by_type[name] = by_type.get(name, 0) + 1
            distinct.add(attacker)

    # Shield pawns: own pawns on the three files around the king, ahead of it.
    file_index = chess.square_file(king)
    rank_index = chess.square_rank(king)
    direction = 1 if color == chess.WHITE else -1
    shield = 0
    open_files = 0

    for df in (-1, 0, 1):
        f = file_index + df

        if not 0 <= f <= 7:
            continue

        own_pawn_on_file = False

        for dr in (1, 2):
            r = rank_index + direction * dr

            if not 0 <= r <= 7:
                continue

            piece = board.piece_at(chess.square(f, r))

            if piece is not None and piece.piece_type == chess.PAWN and piece.color == color:
                shield += 1
                own_pawn_on_file = True
                break

        if not own_pawn_on_file:
            open_files += 1

    return {
        "attackers_distinct": len(distinct),
        "attackers_by_type": by_type,
        "shield_pawns": shield,
        "files_without_shield": open_files,
        "king_square": chess.square_name(king),
    }


def score_after(engine, board, move, depth):
    """myChess's evaluation of `move`, from the point of view of the side that plays it.

    Searched at ``depth - 1``, deliberately. The root chose its move looking `depth` plies
    ahead; a child searched at the full `depth` looks one ply further and is therefore not
    comparable with that choice. The first version of this script used the same depth for
    both and produced a margin of −600 cp for a move the engine had just picked itself — an
    impossible number, and the tell that the two searches were measuring different horizons.
    """
    probe = board.copy()
    probe.push(move)
    info = engine.analyse(probe, chess.engine.Limit(depth=max(1, depth - 1), time=SEARCH_TIMEOUT_S))
    score = info.get("score")

    if score is None:
        return None

    # analyse() reports relative to the side to move in `probe`, i.e. the opponent.
    return -score.relative.score(mate_score=100000)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--depth", type=int, default=DEFAULT_DEPTH)
    args = parser.parse_args()

    limit = chess.engine.Limit(depth=args.depth, time=SEARCH_TIMEOUT_S)
    sf_limit = chess.engine.Limit(depth=STOCKFISH_DEPTH)

    mychess = chess.engine.SimpleEngine.popen_uci(str(MYCHESS_UCI), stderr=subprocess.DEVNULL)
    stockfish = chess.engine.SimpleEngine.popen_uci(STOCKFISH)
    started = time.monotonic()

    try:
        with OUTPUT.open("w", encoding="utf-8") as out:
            for case in CASES:
                board = chess.Board(case["fen"])
                mover = "white" if board.turn == chess.WHITE else "black"

                truth = stockfish.analyse(board, sf_limit)
                sf_best = truth["pv"][0] if truth.get("pv") else None
                sf_cp = truth["score"].white().score(mate_score=100000)

                own = mychess.play(board, limit, info=chess.engine.INFO_SCORE)
                own_move = own.move
                own_cp = own.info["score"].white().score(mate_score=100000) \
                    if own.info.get("score") else None

                chosen = score_after(mychess, board, own_move, args.depth) if own_move else None
                alternative = score_after(mychess, board, sf_best, args.depth) if sf_best else None
                margin = None if chosen is None or alternative is None else chosen - alternative

                record = {
                    "id": case["id"],
                    "note": case["note"],
                    "fen": case["fen"],
                    "side_to_move": mover,
                    "expected_victim": case["expected_victim"],
                    "danger_white": danger_profile(board, chess.WHITE),
                    "danger_black": danger_profile(board, chess.BLACK),
                    "stockfish_cp_white": sf_cp,
                    "stockfish_best": board.san(sf_best) if sf_best else None,
                    "mychess_depth": args.depth,
                    "mychess_cp_white": own_cp,
                    "mychess_move": board.san(own_move) if own_move else None,
                    "agrees_with_stockfish": bool(sf_best and own_move == sf_best),
                    "cp_of_chosen": chosen,
                    "cp_of_stockfish_move": alternative,
                    "margin_cp": margin,
                }
                out.write(json.dumps(record) + "\n")
                out.flush()

                victim = case["expected_victim"]
                profile = record["danger_" + victim]
                agree = "=SF" if record["agrees_with_stockfish"] else "   "
                print(f"{case['id']:<12} {agree} myChess {str(own_cp):>7} vs SF {sf_cp:>7} cp | "
                      f"plays {record['mychess_move']:<6} SF {record['stockfish_best']:<6} | "
                      f"margin {str(margin):>6} cp | {victim} king: "
                      f"{profile['attackers_distinct']} attackers, "
                      f"{profile['shield_pawns']}/3 shield "
                      f"({(time.monotonic() - started) / 60:.1f} min)", flush=True)
    finally:
        mychess.quit()
        stockfish.quit()

    print(f"\n-> {OUTPUT}", flush=True)


if __name__ == "__main__":
    main()
