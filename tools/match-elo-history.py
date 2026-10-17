#!/usr/bin/env python3
"""How a match's Elo estimate and interval developed over time, one line per time step.

tools/match-elo.py prints the standing as of now. This prints the same figures as they stood
at regular intervals since the first game. Each line covers every game finished up to that
moment, so the last line equals what match-elo.py prints today. It reads the games' own
`GameEndTime` tags, so it works across segments and pauses. A step without new games is
skipped rather than repeated, so a pause shows as a jump in the clock.

Below the table it estimates the time left: the declared length comes from the
`match-<name>.rounds` file that tools/run-resumable-match.sh keeps next to the segments
(or from --total), the pace from the median gap between the last game ends. The median
ignores pauses and the interleaving of parallel games.

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
SEGMENT = re.compile(r'^(.*)-seg\d+\.pgn$')

# Number of most recent games whose end times set the pace of the time-left estimate.
PACE_WINDOW = 200
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


def declared_total(paths):
    """Games the whole match is declared to have, from the runner's state file, or None if absent."""
    for path in paths:
        match = SEGMENT.match(str(path))

        if not match:
            continue

        state = pathlib.Path(match.group(1) + ".rounds")

        if state.exists():
            first = state.read_text().splitlines()[0].strip()

            return 2 * int(first) if first.isdigit() else None

    return None


def time_left(end_times, total, window=PACE_WINDOW):
    """(played, to go, games per hour, time left, expected end) from the game end times, or None.

    The pace is the median gap between the last `window` game ends, so a pause or a burst of
    parallel games finishing together does not distort it. The expected end counts from the
    last game's end, not from now, which keeps the estimate reproducible.
    """
    times = sorted(end_times)
    played = len(times)

    if total is None or played < 2 or played >= total:
        return None

    recent = times[-(window + 1):]
    gaps = sorted((later - earlier).total_seconds() for earlier, later in zip(recent, recent[1:]))
    middle = len(gaps) // 2
    gap = gaps[middle] if len(gaps) % 2 else (gaps[middle - 1] + gaps[middle]) / 2

    if gap <= 0:
        return None

    left = datetime.timedelta(seconds=gap * (total - played))

    return played, total - played, 3600 / gap, left, times[-1] + left


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--every", type=int, default=30, metavar="MINUTES",
                        help="time step between lines, default 30")
    parser.add_argument("--total", type=int, metavar="GAMES",
                        help="declared match length; default: read from match-<name>.rounds")
    parser.add_argument("candidate")
    parser.add_argument("paths", nargs="+", metavar="pgn")
    args = parser.parse_args()

    played = list(games(args.paths, args.candidate))
    rows = history(played, datetime.timedelta(minutes=args.every))

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

    total = args.total if args.total is not None else declared_total(args.paths)
    estimate = time_left([end for end, _ in played], total)

    if estimate is not None:
        done, to_go, rate, left, finish = estimate
        print(f"\n  {done} of {total} played, {to_go} to go - {rate:.0f} games/hour "
              f"(median gap, last {min(PACE_WINDOW, done - 1)}) -> about {left.total_seconds() / 3600:.1f} h left, "
              f"done about {finish:%a %d %b %H:%M}")


if __name__ == "__main__":
    main()
