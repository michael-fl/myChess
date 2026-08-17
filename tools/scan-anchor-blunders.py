#!/usr/bin/env python3
"""Find myChess's real blunders in the anchor-bracket PGNs, with Stockfish.

The counterpart to ``lichess-blunder-scan.py`` for a different corpus: 2000
engine-vs-engine games from the absolute-Elo bracket instead of downloaded lichess games.
It deliberately **reuses that tool's measurement primitive** (``evaluate_move``), so a
loss reported here means the same thing as a loss reported there — evaluated before and
after the move, both from the mover's side, clamped identically. Two corpora, one
definition.

**Why losses to the weaker anchors come first.** A loss to an engine of known *lower*
rating almost always means something concrete went wrong. A loss to a stronger one may
simply be a stronger opponent, which is not a defect and not actionable. Five anchors
spanning 1609 to 2019 sort the corpus by themselves, and ``--anchors`` narrows it further.

**The self-evaluation column is diagnosis, not detection.** cutechess recorded what
myChess thought of every move it played, so next to Stockfish's verdict the report shows
what myChess believed at the time. The gap separates two different defects:

* myChess's own score was close to the truth → it saw the refutation, one ply too late.
  A horizon problem, addressable by search depth.
* myChess's own score stayed far above the truth → it never saw it. An evaluation
  problem, and no amount of depth fixes it.

An earlier version of this tool used that self-evaluation as the *detector* instead, to
avoid the engine cost. That was a mistake worth recording: of its eight strongest
candidates, four turned out to be noise between 0.21 and 0.90 pawns once Stockfish was
finally asked. A self-contradiction is a place to look, not a finding.

Two phases, as in the lichess tool: a cheap scan at ``--depth`` over every move myChess
played, then a re-check of the survivors at ``--verify-depth``. About 0.7 s per move
single-threaded on an M1 Pro, so ``--jobs 6`` brings the 151 losses to TSCP and Zeta Dva
to roughly 12 minutes and all 741 losses to about an hour.

Usage::

    ../lichess-bot/venv/bin/python tools/scan-anchor-blunders.py --anchors TSCP,ZetaDva
    ../lichess-bot/venv/bin/python tools/scan-anchor-blunders.py --all-games --jobs 8
    ../lichess-bot/venv/bin/python tools/scan-anchor-blunders.py --threshold 400 --top 40

@author Michael Fleischhauer
"""

import argparse
import importlib.util
import json
import multiprocessing as mp
import re
from pathlib import Path

import chess
import chess.engine
import chess.pgn

REPO_ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = REPO_ROOT / "test-results"
LICHESS_SCANNER = REPO_ROOT / "tools" / "lichess-blunder-scan.py"

#: The engine under examination, as cutechess names it in the PGN tags.
DEFAULT_ENGINE = "myChess-4.4.1"

#: Match files of the current anchor bracket. Deliberately narrow: test-results also holds
#: older myChess-vs-myChess matches, where "the opponent" is another myChess build and a
#: blunder says nothing about strength relative to a known rating.
DEFAULT_GLOB = "match-4.4.1-vs-*.pgn"

#: Anchors in ascending CCRL order; the index is the ranking's primary key.
ANCHOR_ORDER = ["tscp", "zetadva", "princhess", "kojiro", "bbc"]

#: Gap between myChess's own score for a move and the truth after it, above which the
#: finding is classed as an evaluation defect rather than a horizon effect.
BLIND_GAP_CP = 200

#: Seconds per position at depth 15, measured on an M1 Pro — used only for the estimate.
SECONDS_PER_POSITION = 0.36

SCORE_PATTERN = re.compile(r"^([+-]?)(M?)(\d+(?:\.\d+)?)/(\d+)")
MATE_CP = 10_000

_ENGINE: chess.engine.SimpleEngine | None = None
_SCANNER = None


def scanner():
    """The lichess scanner module, imported for its measurement primitives."""
    global _SCANNER
    if _SCANNER is None:
        spec = importlib.util.spec_from_file_location("blunder_scan", LICHESS_SCANNER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _SCANNER = module

    return _SCANNER


def own_score(comment: str) -> int | None:
    """myChess's own evaluation of the move it just played, from the cutechess comment."""
    match = SCORE_PATTERN.match(comment.strip()) if comment else None
    if not match:
        return None

    sign = -1 if match.group(1) == "-" else 1
    if match.group(2) == "M":
        return sign * MATE_CP

    return int(round(sign * float(match.group(3)) * 100))


def anchor_of(path: Path) -> str:
    """The anchor name embedded in a match filename, lower-cased."""
    match = re.search(r"-vs-([A-Za-z][A-Za-z0-9]*)", path.stem)

    return match.group(1).lower() if match else path.stem.lower()


def game_moves(game: chess.pgn.Game, engine_name: str) -> list[dict]:
    """
    Every move `engine_name` played, with the position before it and its own verdict.

    Extracted without an engine, so the expensive phase receives a flat work list that
    parallelizes without touching the PGN again.
    """
    white, black = game.headers.get("White", ""), game.headers.get("Black", "")
    if engine_name not in (white, black):
        return []

    color = chess.WHITE if white == engine_name else chess.BLACK
    board = game.board()
    out: list[dict] = []

    for node in game.mainline():
        # Opening-book plies are NOT myChess's decisions: cutechess plays them from the
        # suite and marks them {book} with no evaluation. 2moves_v2.pgn carries some
        # objectively poor lines, so measuring them attributes the suite's choices to the
        # engine -- the first pass of this scan reported four such moves inside its top
        # 25, one of them "losing" 7.09 pawns on move 2.
        if board.turn == color and "book" not in node.comment.lower() \
                and own_score(node.comment) is not None:
            out.append({"fen": board.fen(), "uci": node.move.uci(),
                        "san": board.san(node.move),
                        "move_number": board.fullmove_number,
                        "own_cp": own_score(node.comment),
                        "color": color})
        board.push(node.move)

    return out


def init_worker(stockfish: str, hash_mb: int) -> None:
    """Open one Stockfish per worker process; reused for every position it handles."""
    global _ENGINE
    _ENGINE = chess.engine.SimpleEngine.popen_uci(stockfish)
    _ENGINE.configure({"Threads": 1, "Hash": hash_mb})


def scan_one(work: dict) -> dict:
    """Measure one move with the lichess tool's primitive, keeping the numbers comparable."""
    measured = scanner().evaluate_move(_ENGINE, work["fen"], work["uci"],
                                       work["color"], work["depth"])

    return {**work, **measured}


def collect_work(paths: list[Path], engine_name: str, losses_only: bool,
                 depth: int) -> tuple[list[dict], dict]:
    """Build the flat list of moves to measure, plus a per-anchor tally."""
    work: list[dict] = []
    tally: dict = {}

    for path in paths:
        anchor = anchor_of(path)
        counts = tally.setdefault(anchor, {"games": 0, "scanned": 0, "moves": 0})

        with path.open(errors="replace") as handle:
            while True:
                game = chess.pgn.read_game(handle)
                if game is None:
                    break

                counts["games"] += 1
                white = game.headers.get("White", "")
                result = game.headers.get("Result", "*")
                lost = (result == "0-1" and white == engine_name) or \
                       (result == "1-0" and white != engine_name)
                if losses_only and not lost:
                    continue

                counts["scanned"] += 1
                for move in game_moves(game, engine_name):
                    counts["moves"] += 1
                    work.append({**move, "anchor": anchor, "depth": depth})

    return work, tally


def rank_key(finding: dict) -> tuple:
    """Weakest opponent first, then the largest loss."""
    order = ANCHOR_ORDER.index(finding["anchor"]) if finding["anchor"] in ANCHOR_ORDER \
        else len(ANCHOR_ORDER)

    return (order, -finding["loss_cp"])


def show(centipawns: int | None) -> str:
    """Format a centipawn value, naming mate rather than printing 100.00."""
    if centipawns is None:
        return "—"
    if abs(centipawns) >= MATE_CP:
        return "mate" if centipawns > 0 else "-mate"

    return f"{centipawns / 100:+.2f}"


def is_eval_defect(finding: dict) -> bool:
    """Whether myChess's own score for the move stayed far above the truth after it."""
    own = finding.get("own_cp")

    return own is not None and own - finding["eval_after_cp"] >= BLIND_GAP_CP


def parse_args() -> argparse.Namespace:
    """Define and parse the command-line options."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("pgn", nargs="*", type=Path, help=f"match PGNs (default: {DEFAULT_GLOB})")
    parser.add_argument("--engine", default=DEFAULT_ENGINE, help="name in the PGN tags")
    parser.add_argument("--stockfish", default="/opt/homebrew/bin/stockfish")
    parser.add_argument("--anchors", default="", metavar="A,B",
                        help="restrict to these anchors (default: all)")
    parser.add_argument("--depth", type=int, default=15, help="scan depth (default: %(default)s)")
    parser.add_argument("--verify-depth", type=int, default=20,
                        help="re-check depth for survivors (default: %(default)s)")
    parser.add_argument("--threshold", type=int, default=300, metavar="CP",
                        help="loss that counts as a blunder (default: %(default)s)")
    parser.add_argument("--all-games", action="store_true", help="scan draws and wins too")
    parser.add_argument("--jobs", type=int, default=6, help="parallel Stockfish processes")
    parser.add_argument("--hash", type=int, default=128, help="MB per Stockfish process")
    parser.add_argument("--top", type=int, default=25, help="findings to print")

    return parser.parse_args()


def select_paths(args: argparse.Namespace) -> list[Path]:
    """The match PGNs to scan, after the default glob and the --anchors filter."""
    paths = args.pgn or sorted(p for p in RESULTS_DIR.glob(DEFAULT_GLOB)
                               if "INVALID" not in p.name and "superseded" not in p.name)
    if args.anchors:
        wanted = {a.strip().lower() for a in args.anchors.split(",")}
        paths = [p for p in paths if anchor_of(p) in wanted]

    return [p for p in paths if p.is_file()]


def report(findings: list[dict], top: int) -> None:
    """Print the ranked findings and the horizon-versus-evaluation split."""
    header = (f"{'#':>3} {'anchor':<10}{'mv':>4} {'played':<8}{'SF before':>10}"
              f"{'SF after':>10}{'loss':>7}{'myChess said':>13}{'defect':>8}")
    print(header)
    print("-" * len(header))
    for i, finding in enumerate(findings[:top], 1):
        kind = "—" if finding.get("own_cp") is None else \
            ("EVAL" if is_eval_defect(finding) else "horizon")
        print(f"{i:>3} {finding['anchor']:<10}{finding['move_number']:>4} {finding['san']:<8}"
              f"{show(finding['eval_before_cp']):>10}{show(finding['eval_after_cp']):>10}"
              f"{finding['loss_cp'] / 100:>7.2f}{show(finding.get('own_cp')):>13}{kind:>8}")

    print("\nFENs before the suspect moves (top 6):")
    for finding in findings[:6]:
        print(f"   {finding['fen']}   played {finding['uci']}"
              f"  ({finding['anchor']}, loss {finding['loss_cp'] / 100:.2f})")

    if not findings:
        return

    defects = sum(1 for f in findings if is_eval_defect(f))
    print(f"\n{defects}/{len(findings)} are EVAL defects — myChess's own score for the move "
          f"stayed at least {BLIND_GAP_CP / 100:.0f} pawns above the truth after it, so more "
          f"depth would not have helped. The rest are horizon cases.")


def main() -> None:
    """Scan the match PGNs with Stockfish and print a ranked blunder list."""
    args = parse_args()
    paths = select_paths(args)
    if not paths:
        raise SystemExit("no match PGNs selected")

    work, tally = collect_work(paths, args.engine, not args.all_games, args.depth)
    print(f"\n=== {len(paths)} file(s), {args.engine}, scan depth {args.depth}, "
          f"{args.jobs} process(es) ===\n")
    for anchor in sorted(tally, key=lambda a: ANCHOR_ORDER.index(a)
                         if a in ANCHOR_ORDER else len(ANCHOR_ORDER)):
        counts = tally[anchor]
        print(f"   {anchor:<12} {counts['games']:>4} games, {counts['scanned']:>4} scanned, "
              f"{counts['moves']:>5} moves")

    estimate = len(work) * 2 * SECONDS_PER_POSITION / 60 / max(args.jobs, 1)
    print(f"\n{len(work)} positions to measure (~{estimate:.0f} min)\n")

    context = mp.get_context("spawn")
    with context.Pool(args.jobs, initializer=init_worker,
                      initargs=(args.stockfish, args.hash)) as pool:
        scanned = pool.map(scan_one, work, chunksize=8)

        candidates = [s for s in scanned if s["loss_cp"] >= args.threshold]
        print(f"scan found {len(candidates)} loss(es) >= {args.threshold / 100:.2f}; "
              f"re-checking at depth {args.verify_depth}")
        verified = pool.map(scan_one,
                            [{**c, "depth": args.verify_depth} for c in candidates],
                            chunksize=4)

    findings = sorted((v for v in verified if v["loss_cp"] >= args.threshold), key=rank_key)
    print(f"{len(findings)} survive verification\n")
    report(findings, args.top)

    # Persisted so a different --top or a re-reading of the classification costs nothing;
    # the scan itself is 17 minutes and should not have to be repeated for a report tweak.
    out = RESULTS_DIR / "blunder-scan-findings.json"
    out.write_text(json.dumps([{k: v for k, v in f.items() if k != "color"}
                               for f in findings], indent=1))
    print(f"\nfindings written to {out.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
