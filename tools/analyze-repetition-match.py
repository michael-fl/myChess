#!/usr/bin/env python3
"""Measure the repetition fix from a cutechess PGN, by counting events rather than Elo.

The § 12.23 fix targets one specific thing: games drawn by threefold repetition from a
position one side considered won. That event is far too rare to move a match score
detectably — a shift worth ~8 Elo hides inside the ±14 Elo confidence interval of a
1600-game run — but it is directly countable in the PGN cutechess writes anyway, and there
the same shift is overwhelming. This script does the counting.

**The prediction it tests.** In a corrected-vs-uncorrected match the two engines are
identical apart from the fix, so under the null hypothesis every repetition draw is equally
likely to have either engine as the side that was ahead. If the fix works, that symmetry
breaks in a specific way:

* the candidate should **throw** fewer wins — being ahead and settling for a repetition,
  because it now sees the repetition coming and avoids it;
* the candidate should **rescue** more losses — being behind and steering into a repetition
  it previously could not see.

Both are reported, with an exact binomial test against the 50/50 the null predicts.

Usage::

    ../lichess-bot/venv/bin/python tools/analyze-repetition-match.py \\
        test-results/sprt-repetition-fix.pgn --candidate mychess-4.4.1

    # tighter definition of "was ahead", longer look-back:
    ... --threshold 300 --window 20

**Validated against the 2026-08-11 hybrid-vs-4.3.4 match**, whose figures § 12.23 records:
it reproduces 586 repetition draws, 59 adjudicated, and 203 non-adjudicated draws with one
side at or above +2.00 within the last 12 plies (180 of them repetition draws, 23 other
natural ones). That match doubles as the **negative control**: both builds carry the bug, and
the script correctly finds no asymmetry — 95 threws to 85, p = 0.5 against 50/50. A run that
reports a comparable p on 4.4.1 vs 4.4.0 is telling you the fix did not take effect.

Scores come from the move comments cutechess records (``{+0.60/9 1.5s}``) and are read from
the perspective of the engine that played the move, which is what makes "this engine
believed it was winning" answerable at all.

@author Michael Fleischhauer
"""

import argparse
import math
import re
import sys
from collections import Counter
from pathlib import Path

HEADER_PATTERN = re.compile(r'\[(\w+)\s+"([^"]*)"\]')
COMMENT_PATTERN = re.compile(r"\{([^}]*)\}")
SCORE_PATTERN = re.compile(r"^([+-]?)(M?)(\d+(?:\.\d+)?)/(\d+)")
DRAW_REASON_PATTERN = re.compile(r"Draw by ([a-z0-9 -]+)")

#: Centipawn stand-in for a mate score, well above any material advantage.
MATE_CENTIPAWNS = 10_000

DEFAULT_THRESHOLD_CP = 200
DEFAULT_WINDOW_PLIES = 12   # matches the published § 12.23 analysis
REPETITION_REASON = "3-fold repetition"


class Game:
    """One parsed game: who played which color, how it ended, and the two score traces."""

    def __init__(self, headers: dict, comments: list[str]):
        self.white = headers.get("White", "?")
        self.black = headers.get("Black", "?")
        self.result = headers.get("Result", "*")
        self.reason = self._draw_reason(comments)
        self.scores = {self.white: [], self.black: []}

        for ply, comment in enumerate(comments):
            score = parse_score(comment)
            if score is not None:
                self.scores[self.white if ply % 2 == 0 else self.black].append(score)

    @staticmethod
    def _draw_reason(comments: list[str]) -> str | None:
        """Return the draw reason cutechess appended to the final comment, if any."""
        for comment in reversed(comments):
            match = DRAW_REASON_PATTERN.search(comment)
            if match:
                return match.group(1).strip()

        return None

    def best_seen(self, engine: str, window_plies: int) -> int | None:
        """Highest score `engine` reported for itself within the last `window_plies`."""
        trace = self._tail(engine, window_plies)

        return max(trace) if trace else None

    def worst_seen(self, engine: str, window_plies: int) -> int | None:
        """Lowest score `engine` reported for itself within the last `window_plies`."""
        trace = self._tail(engine, window_plies)

        return min(trace) if trace else None

    def _tail(self, engine: str, window_plies: int) -> list[int]:
        """
        The scores one engine reported inside the last `window_plies` of the game.

        The window is given in plies to match the published § 12.23 analysis, but each
        engine only moves on half of them — so the trace is cut to half that many of its
        own moves. Getting this wrong doubles the window silently: measured against the
        2026-08-11 hybrid match, 12 plies reproduces the recorded 203 games, while reading
        the same 12 as own-moves gives 226.
        """
        trace = self.scores.get(engine, [])

        return trace[-(window_plies // 2):] if trace else []


def parse_score(comment: str) -> int | None:
    """
    Parse one cutechess move comment into centipawns from the mover's own perspective.

    Handles ``+0.60/9``, ``0.00/12``, and the mate forms ``+M9/10`` / ``-M8/13``. Returns
    ``None`` for comments that carry no score, such as ``{book}``.
    """
    match = SCORE_PATTERN.match(comment.strip())
    if not match:
        return None

    sign = -1 if match.group(1) == "-" else 1
    if match.group(2) == "M":
        return sign * MATE_CENTIPAWNS

    return int(round(sign * float(match.group(3)) * 100))


def read_games(path: Path) -> list[Game]:
    """Parse every game in a cutechess PGN file."""
    games: list[Game] = []
    headers: dict = {}
    movetext: list[str] = []

    def flush() -> None:
        if headers and movetext:
            games.append(Game(headers, COMMENT_PATTERN.findall(" ".join(movetext))))

    for line in path.read_text(errors="replace").splitlines():
        stripped = line.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            if movetext:
                flush()
                headers, movetext = {}, []
            match = HEADER_PATTERN.match(stripped)
            if match:
                headers[match.group(1)] = match.group(2)
        elif stripped:
            movetext.append(stripped)

    flush()

    return games


def binomial_p_value(hits: int, total: int) -> float:
    """
    Exact two-sided binomial p-value against p = 0.5.

    The null here is engine symmetry: with the fix removed the two builds are identical, so
    each event should fall to either engine with equal probability.
    """
    if total == 0:
        return 1.0

    def tail(k: int) -> float:
        return sum(math.comb(total, i) for i in range(k + 1)) / 2 ** total

    lower = min(hits, total - hits)

    return min(1.0, 2 * tail(lower))


def classify(games: list[Game], candidate: str, baseline: str, threshold: int,
             window: int) -> dict:
    """Count repetition draws by which engine was ahead, and by which was behind."""
    counts = {"threw": Counter(), "rescued": Counter(), "reasons": Counter(),
              "results": Counter(), "repetition_draws": 0, "undecided": 0}

    for game in games:
        counts["results"][game.result] += 1
        counts["reasons"][game.reason or "played out"] += 1
        if game.reason != REPETITION_REASON:
            continue

        counts["repetition_draws"] += 1
        decided = False
        for engine in (candidate, baseline):
            best = game.best_seen(engine, window)
            worst = game.worst_seen(engine, window)
            if best is not None and best >= threshold:
                counts["threw"][engine] += 1
                decided = True
            if worst is not None and worst <= -threshold:
                counts["rescued"][engine] += 1
                decided = True

        if not decided:
            counts["undecided"] += 1

    return counts


def report(counts: dict, candidate: str, baseline: str, threshold: int, window: int) -> None:
    """Print the counts and the significance of the asymmetry between the two engines."""
    total_games = sum(counts["results"].values())
    print(f"\n=== {total_games} games, "
          f"threshold {threshold / 100:+.2f} over the last {window} plies ===\n")

    print("  results")
    for result, n in sorted(counts["results"].items()):
        print(f"    {result:<8} {n:>5}")

    print("\n  how games ended")
    for reason, n in counts["reasons"].most_common():
        print(f"    {reason:<28} {n:>5}")

    print(f"\n  repetition draws: {counts['repetition_draws']}"
          f"  (of which {counts['undecided']} with neither side past the threshold)")

    for label, note in (("threw", "was ahead and settled for the repetition"),
                        ("rescued", "was behind and saved half a point")):
        hits = counts[label][candidate]
        misses = counts[label][baseline]
        total = hits + misses
        print(f"\n  {label} — {note}")
        print(f"    {candidate:<24} {hits:>5}")
        print(f"    {baseline:<24} {misses:>5}")
        if total:
            p = binomial_p_value(hits, total)
            share = hits / total
            print(f"    candidate share {share:6.1%} of {total}   "
                  f"exact binomial p = {p:.2g} against 50/50")
        else:
            print("    no events — nothing to test")

    print("\n  Read 'threw' as the primary signal: the fix is meant to stop the candidate")
    print("  from walking into a repetition while ahead, so its share should fall well")
    print("  below half. 'rescued' is the mirror image and should rise above half.\n")


def parse_args() -> argparse.Namespace:
    """Define and parse the command-line options."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("pgn", type=Path, help="cutechess PGN of the corrected-vs-uncorrected match")
    parser.add_argument("--candidate", help="name of the corrected engine as it appears in the tags")
    parser.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD_CP,
                        help="centipawns that count as 'ahead' (default: %(default)s)")
    parser.add_argument("--window", type=int, default=DEFAULT_WINDOW_PLIES, metavar="PLIES",
                        help="plies before the end to look at; each engine moves on half of "
                             "them (default: %(default)s)")

    return parser.parse_args()


def main() -> None:
    """Count the repetition events in one match and test them for engine asymmetry."""
    args = parse_args()
    if not args.pgn.is_file():
        sys.exit(f"no such file: {args.pgn}")

    games = read_games(args.pgn)
    if not games:
        sys.exit(f"no games parsed from {args.pgn}")

    names = sorted({name for game in games for name in (game.white, game.black)})
    if len(names) != 2:
        sys.exit(f"expected exactly two engines, found: {', '.join(names)}")

    candidate = args.candidate or names[0]
    if candidate not in names:
        sys.exit(f"{candidate} is not one of the engines in this file: {', '.join(names)}")

    baseline = next(name for name in names if name != candidate)
    counts = classify(games, candidate, baseline, args.threshold, args.window)
    report(counts, candidate, baseline, args.threshold, args.window)


if __name__ == "__main__":
    main()
