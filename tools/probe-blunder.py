#!/usr/bin/env python3
"""Work up one blunder into everything a BlunderTest case needs.

Given a game and a move number — or a bare FEN and a move — this prints the four things
a test needs and nothing else:

1. The **FEN before the move**, as a copy-ready constant, plus the material balance. The
   balance matters more often than it looks: myChess reporting a healthy advantage while
   a pawn *behind* rules out the material-only eval shortcut as the explanation, which
   changes the diagnosis.
2. **Stockfish's verdict** — its best move, the evaluation before, the evaluation after
   the move actually played, and the principal variation.
3. **What myChess plays at each depth** in a range, with its own evaluation. This is the
   part that decides the test: a move it abandons at depth 10 is knowledge two plies out
   of reach, one it keeps at every depth is a hole in the evaluation.
4. A **suggested pin depth** — the lowest depth at which the blunder reproduces.

Usage::

    ../lichess-bot/venv/bin/python tools/probe-blunder.py --game NMc7sp8h --move 33
    ../lichess-bot/venv/bin/python tools/probe-blunder.py --game gVJ7PdwQ --move 75 --depths 8-12
    ../lichess-bot/venv/bin/python tools/probe-blunder.py \\
        --fen "rr6/3bk1q1/p1nNp3/2R1p3/2Np2pp/P2R4/1PQ2PPP/6K1 w - - 1 33" --played f2f3

With ``--game`` the position is read from ``test-results/lichess/findings/``, so the game
must have been scanned by ``lichess-blunder-scan.py`` first.

**Never derive the UCI move by hand.** The one time it was done that way the king was
assumed on h1 while it stood on g1, the probe reported "not reproduced" for a move myChess
had in fact played, and the case was nearly dismissed. Here the move always comes from the
stored data or from ``--played``, and the SAN is printed alongside for checking.

@author Michael Fleischhauer
"""

import argparse
import glob
import json
import subprocess
import sys
from pathlib import Path

try:
    import chess
    import chess.engine
except ImportError as missing:
    sys.exit(f"missing dependency: {missing.name}. Run this with a Python that has "
             f"python-chess, e.g. ../lichess-bot/venv/bin/python")

REPO_ROOT = Path(__file__).resolve().parent.parent
FINDINGS_DIR = REPO_ROOT / "test-results" / "lichess" / "findings"
MYCHESS_UCI = REPO_ROOT / "mychess-uci.sh"
DEFAULT_STOCKFISH = "/opt/homebrew/bin/stockfish"
DEFAULT_SF_DEPTH = 22
DEFAULT_DEPTHS = "8-11"

PIECE_VALUES = {chess.PAWN: 100, chess.KNIGHT: 300, chess.BISHOP: 300,
                chess.ROOK: 500, chess.QUEEN: 900, chess.KING: 0}


def load_case(game_id: str, move_number: int) -> dict:
    """
    Return the stored finding for one game and move number.

    :raises SystemExit: if the game was never scanned or has no finding at that move.
    """
    findings: dict = {}
    for path in sorted(FINDINGS_DIR.glob("*.json")):
        with path.open() as handle:
            findings.update(json.load(handle))

    entry = findings.get(game_id)
    if entry is None:
        sys.exit(f"{game_id} is not in {FINDINGS_DIR.relative_to(REPO_ROOT)} — "
                 f"run lichess-blunder-scan.py first")

    for hit in entry["findings"]:
        if hit["move_number"] == move_number:
            return {"fen": hit["fen_before"], "uci": hit["uci"], "san": hit["move"],
                    "color": entry["color"], "chess960": entry.get("chess960", False),
                    "scan": hit["scan"], "verified": hit.get("verified")}

    available = sorted(hit["move_number"] for hit in entry["findings"])
    sys.exit(f"{game_id} has no recorded finding at move {move_number}; recorded: {available}")


def material_balance(board: chess.Board) -> tuple[int, int]:
    """Return the material total in centipawns for white and for black."""
    totals = [0, 0]
    for piece in board.piece_map().values():
        totals[0 if piece.color == chess.WHITE else 1] += PIECE_VALUES[piece.piece_type]

    return totals[0], totals[1]


def stockfish_verdict(board: chess.Board, uci: str, depth: int, binary: str) -> dict:
    """Return Stockfish's best move, its evaluation, and the evaluation after `uci`."""
    engine = chess.engine.SimpleEngine.popen_uci(binary)
    limit = chess.engine.Limit(depth=depth)
    try:
        info = engine.analyse(board, limit)
        best_move = info["pv"][0]
        result = {"best_san": board.san(best_move),
                  "best_uci": best_move.uci(),
                  "before": info["score"].pov(board.turn),
                  "pv": board.variation_san(info["pv"][:8])}

        board.push(chess.Move.from_uci(uci))
        result["after"] = engine.analyse(board, limit)["score"].pov(not board.turn)
        board.pop()

        return result
    finally:
        engine.quit()


def ask_mychess(fen: str, depth: int, chess960: bool) -> tuple[str, str]:
    """
    Ask myChess for its move at a fixed depth. Returns (uci, score-as-reported).

    Driven through the UCI front-end rather than in-process because that is the artefact
    that plays: the same binary, the same options, no test harness in between.
    """
    process = subprocess.Popen([str(MYCHESS_UCI)], stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                               text=True, bufsize=1)
    score = "?"
    try:
        process.stdin.write("uci\n")
        process.stdin.flush()
        for line in process.stdout:
            if line.startswith("uciok"):
                break

        if chess960:
            process.stdin.write("setoption name UCI_Chess960 value true\n")

        process.stdin.write(f"position fen {fen}\ngo depth {depth}\n")
        process.stdin.flush()

        for line in process.stdout:
            tokens = line.split()
            if line.startswith("info") and "score" in tokens:
                index = tokens.index("score")
                score = " ".join(tokens[index + 1:index + 3])
            if line.startswith("bestmove"):
                return tokens[1], score
    finally:
        try:
            process.stdin.write("quit\n")
            process.stdin.flush()
        except (BrokenPipeError, ValueError):
            # The engine has already exited; there is nothing left to tell it.
            pass
        process.wait(timeout=30)

    return "?", score


def parse_depths(spec: str) -> list[int]:
    """Parse ``8-11`` or ``8,10,12`` into a list of depths."""
    if "-" in spec:
        low, high = spec.split("-", 1)
        return list(range(int(low), int(high) + 1))

    return [int(part) for part in spec.split(",")]


def parse_args() -> argparse.Namespace:
    """Define and parse the command-line options."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--game", help="lichess game id, looked up in the scanner's findings")
    parser.add_argument("--move", type=int, help="move number of the blunder (with --game)")
    parser.add_argument("--fen", help="position before the move, instead of --game/--move")
    parser.add_argument("--played", help="the move actually played, in UCI (with --fen)")
    parser.add_argument("--chess960", action="store_true", help="treat --fen as a Chess960 position")
    parser.add_argument("--depths", default=DEFAULT_DEPTHS,
                        help="myChess depths to probe, e.g. 8-11 or 8,10 (default: %(default)s)")
    parser.add_argument("--sf-depth", type=int, default=DEFAULT_SF_DEPTH,
                        help="Stockfish depth for the reference verdict (default: %(default)s)")
    parser.add_argument("--stockfish", default=DEFAULT_STOCKFISH, help="path to the Stockfish binary")

    args = parser.parse_args()
    if args.game and args.move:
        return args
    if args.fen and args.played:
        return args

    parser.error("give either --game with --move, or --fen with --played")


def main() -> None:
    """Probe one position and print everything a test case needs."""
    args = parse_args()

    if args.game:
        case = load_case(args.game, args.move)
        label = f"{args.game} move {args.move}"
    else:
        board = chess.Board(args.fen, chess960=args.chess960)
        move = chess.Move.from_uci(args.played)
        case = {"fen": args.fen, "uci": args.played, "san": board.san(move),
                "color": "white" if board.turn == chess.WHITE else "black",
                "chess960": args.chess960, "scan": None, "verified": None}
        label = f"{args.fen[:30]}… {case['san']}"

    board = chess.Board(case["fen"], chess960=case["chess960"])
    white, black = material_balance(board)
    variant = " [Chess960]" if case["chess960"] else ""

    print(f"\n=== {label}: {case['san']} ({case['color']}){variant} ===\n")
    print(f"  FEN            {case['fen']}")
    print(f"  side to move   {'white' if board.turn == chess.WHITE else 'black'}, "
          f"move {board.fullmove_number}")
    print(f"  material       white {white}, black {black} "
          f"(white {white - black:+d} cp)")
    if case["verified"]:
        print(f"  scanner        loss {case['verified']['loss_cp']} cp at depth "
              f"{case['verified']['depth']}")

    verdict = stockfish_verdict(board, case["uci"], args.sf_depth, args.stockfish)
    print(f"\n  Stockfish (depth {args.sf_depth}), from {case['color']}'s side:")
    print(f"    played  {case['san']:<8} -> {verdict['after']}")
    print(f"    best    {verdict['best_san']:<8} -> {verdict['before']}   ({verdict['best_uci']})")
    print(f"    pv      {verdict['pv']}")

    print(f"\n  myChess per depth (looking for {case['san']} = {case['uci']}):")
    reproduced = []
    for depth in parse_depths(args.depths):
        got, score = ask_mychess(case["fen"], depth, case["chess960"])
        hit = got == case["uci"]
        if hit:
            reproduced.append(depth)
        marker = "  <== reproduces" if hit else ""
        print(f"    depth {depth:>3}   {got:<8} score {score:<10}{marker}")

    print()
    if reproduced:
        print(f"  Suggested pin: depth {reproduced[0]}. Reproduces at {reproduced}.")
        print(f"  Assertion to write first (must go red):")
        square_from, square_to = case["uci"][:2], case["uci"][2:4]
        print(f"    assertEngineAvoids(result, Board.{square_from}, Board.{square_to}, "
              f"\"{case['san']}\");")
    else:
        print("  Not reproduced at any probed depth. Either widen --depths, or the case is")
        print("  time- rather than depth-dependent — then it belongs in a time-bounded test.")
    print()


if __name__ == "__main__":
    main()
