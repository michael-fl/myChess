#!/usr/bin/env python3
"""How a match's Elo estimate and interval developed over time, one line per time step.

tools/match-elo.py prints the standing as of now. This prints the same figures as they stood
at regular intervals since the first game. Each line covers every game finished up to that
moment, so the last line equals what match-elo.py prints today. It reads the games' own
`GameEndTime` tags, so it works across segments and pauses. A step without new games is
skipped rather than repeated, so a pause shows as a jump in the clock.

Usage, from the repository root:
    tools/match-elo-history.py [--every MINUTES] <candidate-name> <pgn> [<pgn> ...]

Example, the running 0.015 match in 30-minute steps:
    tools/match-elo-history.py --every 30 4.8.2-exchange-avoidance-0.015 \\
        test-results/match-exchange-avoidance-0.015-seg*.pgn
"""

import argparse
import datetime
import importlib.util
import pathlib
import re
import sys

SPEC = importlib.util.spec_from_file_location(
    "match_elo", pathlib.Path(__file__).with_name("match-elo.py"))
match_elo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(match_elo)

TAG = re.compile(r'^\[(\w+) "([^"]*)"\]')
END_TIME = re.compile(r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})')


def games(paths, candidate):
    """Yields (end time, outcome) per decided game the candidate played; outcome is 'w', 'l' or 'd'."""
    for path in paths:
        tags = {}

        for line in pathlib.Path(path).read_text(errors="ignore").splitlines():
            match = TAG.match(line)

            if match:
                tags[match.group(1)] = match.group(2)
                continue

            if not tags:
                continue

            outcome = outcome_for(tags, candidate)
            end = END_TIME.match(tags.get("GameEndTime", ""))

            if outcome and end:
                yield datetime.datetime.fromisoformat(end.group(1)), outcome

            tags = {}


def outcome_for(tags, candidate):
    """The game's result from the candidate's side, or None if it did not play or the game is unfinished."""
    white, black, result = tags.get("White"), tags.get("Black"), tags.get("Result")

    if candidate not in (white, black) or result not in ("1-0", "0-1", "1/2-1/2"):
        return None

    if result == "1/2-1/2":
        return "d"

    return "w" if (result == "1-0") == (white == candidate) else "l"


def history(played, step):
    """Cumulative (moment, wins, losses, draws) at each step that saw new games, ending with the last game."""
    played = sorted(played)

    if not played:
        return []

    rows = []
    wins = losses = draws = 0
    moment = played[0][0] + step
    index = 0

    while index < len(played):
        counted = False

        while index < len(played) and played[index][0] <= moment:
            outcome = played[index][1]
            wins += outcome == "w"
            losses += outcome == "l"
            draws += outcome == "d"
            index += 1
            counted = True

        if counted:
            rows.append((min(moment, played[index - 1][0]), wins, losses, draws))

        moment += step

    return rows


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--every", type=int, default=30, metavar="MINUTES",
                        help="time step between lines, default 30")
    parser.add_argument("candidate")
    parser.add_argument("paths", nargs="+", metavar="pgn")
    args = parser.parse_args()

    rows = history(games(args.paths, args.candidate), datetime.timedelta(minutes=args.every))

    if not rows:
        sys.exit("no finished games found - check the engine name")

    print(f"{args.candidate}, every {args.every} min, cumulative from the first game")
    print(f"  {'time':<16} {'games':>5}  {'W - L - D':<17} {'Elo':>6}  {'+/-':>5}  95 % interval")

    for moment, wins, losses, draws in rows:
        stats = match_elo.elo_and_interval(wins, losses, draws)

        if stats is None:
            continue

        elo, error, _, n, _ = stats
        score = f"{wins} - {losses} - {draws}"
        print(f"  {moment:%Y-%m-%d %H:%M} {n:>5}  {score:<17} {elo:>+6.1f}  {error:>5.1f}  "
              f"[{elo - error:+.1f}, {elo + error:+.1f}]")


if __name__ == "__main__":
    main()
