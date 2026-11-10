#!/usr/bin/env python3
"""Split a three-arm gauntlet into the two numbers it exists to produce.

cutechess prints one Elo column, rating every engine against the whole field. That is not
what a three-arm gauntlet asks. The question is how the three arms compare to EACH OTHER,
and it has two halves that pull in opposite directions:

    placebo - base        the COST of computing the term, in Elo under the clock
    candidate - placebo   the EFFECT of applying it, with the cost held constant
    candidate - base      the NET, which is what merging delivers

A candidate-versus-base number alone cannot separate cost from effect, so a zero there has
two opposite readings and calls for opposite decisions.

POINTS, NOT THE RATING MODEL. Every arm faces the same anchors equally often, so the raw
score rate is a sufficient statistic and no rating model is needed on top. The Elo figures
below are a delta-method transfer of the score difference, printed because Elo is the unit
the thresholds are written in - not because a rating fit happened.

PAIRED ON THE ROUND. `-openings policy=round` gives every arm the same opening against the
same anchor within a round, so the comparison is paired: the statistic is the per-round
difference between two arms, and its spread is the spread of those differences. That is
what buys roughly +/-17 instead of +/-23 at the same game count, and it is the reason this
script does not simply subtract two overall percentages.

Games with an unfinished result are skipped: an interrupted run writes [Result "*"] for the
games that were in flight, and those are not outcomes.

Safe to run against a live match: it only reads.

Usage:
    tools/gauntlet-summary.py --label <label>
    tools/gauntlet-summary.py <base> <placebo> <candidate> <pgn> [<pgn> ...]
    tools/gauntlet-summary.py --two-arm <base> <candidate> <pgn> [...]
    tools/gauntlet-summary.py --candidates <base> <candidate> [<candidate> ...] <pgn> [...]

--candidates compares every candidate with base, for a run with several candidate arms. The
arm names end at the first argument that ends in .pgn.

--label takes the arm names from the runner's own convention - mychess-base,
mychess-placebo, mychess-<label> - and reads every segment of that run.
"""

import collections
import datetime
import math
import pathlib
import re
import sys

SCORE = {"1-0": 1.0, "0-1": 0.0, "1/2-1/2": 0.5}
HEADER = re.compile(r'^\[(Event|Round|White|Black|Result|GameEndTime|Termination) "(.*)"\]')

Game = collections.namedtuple("Game", "round white black result end termination event")

# A round is identified by its SEGMENT and its number, never by the number alone: cutechess
# restarts round numbering at 1 in every resumed segment, so round "1" of segment 1 and round
# "1" of segment 4 are different openings against the same anchors. Pooling them destroys the
# pairing this whole comparison rests on - and it did, until 2026-09-22.


def _finish(current, segment):
    """A game record, or None when it is incomplete or was cut off mid-play."""
    if current.get("Result") not in SCORE:
        return None

    if not all(k in current for k in ("Round", "White", "Black")):
        return None

    return Game((segment, current["Round"]), current["White"], current["Black"],
                current["Result"], _parse_time(current.get("GameEndTime")), current.get("Termination"),
                current.get("Event", ""))


def _parse_time(stamp):
    """cutechess writes '2026-09-20T23:07:41.840 MESZ'; the zone name is not parseable."""
    if not stamp:
        return None

    try:
        return datetime.datetime.fromisoformat(stamp.split(" ")[0])
    except ValueError:
        return None


def read_games(paths):
    """Yield a Game for every finished game. Delimited by [Event], because the end time
    comes AFTER the result and a parser keyed on the result would never see it."""
    for path in paths:
        current = {}

        for line in pathlib.Path(path).read_text(errors="ignore").splitlines():
            match = HEADER.match(line)

            if not match:
                continue

            key, value = match.group(1), match.group(2)

            if key == "Event":
                game = _finish(current, path)

                if game:
                    yield game

                current = {}

            current[key] = value

        game = _finish(current, path)

        if game:
            yield game


SEGMENT_ARMS = re.compile(r"(\d+)-arm anchor gauntlet")
SEGMENT_NAME = re.compile(r"^gauntlet-(.+?)(-seg\d+)?\.pgn$")


def rounds_file(paths, label):
    """The runner's .rounds file: from --label, otherwise from the name of the first PGN."""
    if label:
        return pathlib.Path(__file__).resolve().parent.parent / "test-results" / f"gauntlet-{label}.rounds"

    first = pathlib.Path(paths[0])
    match = SEGMENT_NAME.match(first.name)

    return first.parent / f"gauntlet-{match.group(1)}.rounds" if match else None


def progress(games, arms_given, paths, label):
    """Games done, games left and how long the rest will take.

    The rate comes from a RECENT window, not from the whole run: a resumable match spends
    time stopped, and dividing the total by the wall clock since the first game would count
    every interruption as slow play and understate what is left.
    """
    rounds_seen = collections.Counter(g.round for g in games)
    # games of a full round: every mychess- arm plays every anchor once with each color. Read
    # off the names rather than off a round, which is still incomplete early in a run; the arms
    # named on the command line count too, since some may not have played yet
    names = {g.white for g in games} | {g.black for g in games}
    arms = {n for n in names if n.startswith("mychess-")} | set(arms_given)
    anchors = len(names - arms)
    per_round = 2 * len(arms) * anchors or max(rounds_seen.values())

    # A segment may have run with fewer arms, when arms were dropped on a resume. Its round size
    # follows from the arm count in its Event tag ("<label> 5-arm anchor gauntlet").
    segment_size = {}

    for g in games:
        match = SEGMENT_ARMS.search(g.event)
        segment_size.setdefault(g.round[0], 2 * int(match.group(1)) * anchors if match else per_round)

    segments = sorted(segment_size)
    complete = collections.Counter(seg for (seg, _), n in rounds_seen.items() if n == segment_size[seg])
    print(f"\n{len(games)} finished games over {len(rounds_seen)} rounds")

    total = None
    state = rounds_file(paths, label)

    if state and state.exists():
        try:
            declared = int(state.read_text().splitlines()[0])
        except (ValueError, IndexError):
            declared = None

        if declared is not None and segments:
            banked = sum(complete[seg] * segment_size[seg] for seg in segments)
            last = segments[-1]
            in_last_partial = sum(n for (seg, _), n in rounds_seen.items()
                                  if seg == last and n != segment_size[last])
            total = banked + (declared - sum(complete.values())) * segment_size[last]
            done = banked + in_last_partial

    if total is None:
        print()
        return

    left = max(total - done, 0)
    stamped = sorted(g.end for g in games if g.end)
    window = stamped[-200:] if len(stamped) > 200 else stamped
    rate = None

    # The MEDIAN gap between consecutive finishes, not the span of the window divided by its
    # count. A resumable run spends hours stopped, and a span-based rate counts that pause as
    # slow play - after the first resume it halved the reported rate and doubled the estimate.
    # A window does not fix that on its own: it only helps once enough games have run since
    # the restart to push the gap out of it. One huge gap among 199 normal ones cannot move a
    # median, so this is right immediately. Concurrency needs no correction either - the gaps
    # are between finishes, so parallel play is already in them.
    if len(window) >= 3:
        gaps = sorted((window[i + 1] - window[i]).total_seconds()
                      for i in range(len(window) - 1))
        middle = gaps[len(gaps) // 2]
        # Drop the pauses, keep the skew. The median alone is immune to an interruption but
        # is a TYPICAL gap, and the distribution has a long right tail - most games finish
        # quickly, a few run long - so a median-based rate flatters the estimate. Trimming
        # at ten times the median removes a stop-and-resume gap, which is orders of magnitude
        # out, while leaving the slow games that genuinely belong in the average.
        kept = [g for g in gaps if g <= 10 * middle] or gaps

        if sum(kept) > 0:
            rate = 3600.0 * len(kept) / sum(kept)

    print(f"  {done} of {total} played, {left} to go"
          f"  ({100.0 * done / total:.1f} %)")

    if rate:
        hours = left / rate
        finish = window[-1] + datetime.timedelta(hours=hours)
        print(f"  {rate:.0f} games/hour (trimmed mean gap, last {len(window)} games)"
              f"  ->  {hours:.1f} h left, done about {finish:%a %d %b %H:%M}")
    else:
        print("  not enough timestamps yet for a rate")

    print()


def time_losses(games, arm):
    """Games the arm lost on time."""
    return sum(1 for g in games if g.termination == "time forfeit"
               and ((g.white == arm and g.result == "0-1") or (g.black == arm and g.result == "1-0")))


def per_round_points(games, arms):
    """points[arm][round] = points scored that round, and the game count behind it."""
    points = {arm: collections.defaultdict(float) for arm in arms}
    counts = {arm: collections.defaultdict(int) for arm in arms}

    for game in games:
        if game.white in points:
            points[game.white][game.round] += SCORE[game.result]
            counts[game.white][game.round] += 1

        if game.black in points:
            points[game.black][game.round] += 1.0 - SCORE[game.result]
            counts[game.black][game.round] += 1

    return points, counts


def paired_difference(points, counts, arm_a, arm_b):
    """Mean per-game score difference over COMPLETE rounds, with its 95 % half-width.

    Only rounds where both arms played their full complement count. A round left half-played
    by an interruption has a tiny denominator - six games against twenty, in the run this was
    written for - and an unweighted mean over rounds hands it the same weight as a full one.
    Two such rounds out of 106 moved the difference from -0.14 points to +0.33 and flipped its
    sign, which is how this was found. Dropping them makes the paired figure agree with the
    pooled score difference, as it must.
    """
    full = max(counts[arm_a].values(), default=0)
    diffs = []

    for rnd in points[arm_a]:
        games_a, games_b = counts[arm_a][rnd], counts[arm_b].get(rnd, 0)

        if games_a != full or games_b != full:
            continue

        diffs.append(points[arm_a][rnd] / games_a - points[arm_b][rnd] / games_b)

    if len(diffs) < 2:
        return None

    mean = sum(diffs) / len(diffs)
    variance = sum((d - mean) ** 2 for d in diffs) / (len(diffs) - 1)
    half = 1.96 * math.sqrt(variance / len(diffs))

    return mean, half, len(diffs)


def to_elo(score_delta, rate):
    """Delta-method transfer of a score difference onto the Elo scale at a given score rate."""
    rate = min(max(rate, 0.05), 0.95)

    return 400.0 / math.log(10) * score_delta / (rate * (1.0 - rate))


def main():
    argv = sys.argv[1:]
    two_arm = False
    label = None
    extra = []

    if len(argv) == 2 and argv[0] == "--label":
        label = argv[1]
        root = pathlib.Path(__file__).resolve().parent.parent
        paths = sorted(str(p) for p in
                       (root / "test-results").glob(f"gauntlet-{label}-seg*.pgn"))

        if not paths:
            sys.exit(f"no segments found for label '{label}' in {root / 'test-results'}")

        argv = ["mychess-base", "mychess-placebo", f"mychess-{label}"] + paths

    if argv and argv[0] == "--candidates":
        names = []

        for arg in argv[1:]:
            if arg.endswith(".pgn"):
                break

            names.append(arg)

        if len(names) < 2:
            sys.exit(__doc__)

        extra = names[2:]
        argv = ["--two-arm"] + names[:2] + argv[1 + len(names):]

    if argv and argv[0] == "--two-arm":
        two_arm, argv = True, argv[1:]

    needed = 3 if two_arm else 4

    if len(argv) < needed:
        sys.exit(__doc__)

    if two_arm:
        base, candidate = argv[0], argv[1]
        placebo, paths = None, argv[2:]
        arms = [base, candidate] + extra
    else:
        base, placebo, candidate = argv[0], argv[1], argv[2]
        paths = argv[3:]
        arms = [base, placebo, candidate]

    games = list(read_games(paths))

    if not games:
        sys.exit("no finished games found")

    points, counts = per_round_points(games, arms)

    progress(games, arms, paths, label)
    width = max(26, max(len(arm) for arm in arms) + 2)
    print(f"{'arm':<{width}}{'games':>7}{'score':>9}{'lost on time':>14}")

    rates = {}

    for arm in arms:
        total_games = sum(counts[arm].values())
        total_points = sum(points[arm].values())

        if total_games == 0:
            sys.exit(f"arm '{arm}' played no games - check the name against the PGN")

        rates[arm] = total_points / total_games
        print(f"  {arm:<{width - 2}}{total_games:>7}{rates[arm]:>8.1%}{time_losses(games, arm):>14}")

    comparisons = [("NET      candidate - base", candidate, base)]

    if extra:
        comparisons = [(f"{arm.removeprefix('mychess-')} - base", arm, base) for arm in [candidate] + extra]

    if placebo:
        comparisons = [
            ("COST     placebo - base", placebo, base),
            ("EFFECT   candidate - placebo", candidate, placebo),
            ("NET      candidate - base", candidate, base),
        ]

    label_width = max(28, max(len(c[0]) for c in comparisons))
    print(f"\n{'':<{label_width + 2}}{'score pts':>11}{'Elo':>9}{'± 95 %':>8}{'95 % interval (Elo)':>22}{'rounds':>8}")

    for label, arm_a, arm_b in comparisons:
        result = paired_difference(points, counts, arm_a, arm_b)

        if result is None:
            print(f"  {label:<{label_width}}  not enough shared rounds")
            continue

        mean, half, rounds = result
        rate = (rates[arm_a] + rates[arm_b]) / 2
        elo, elo_half = to_elo(mean, rate), to_elo(half, rate)

        interval = f"[{elo - elo_half:+.1f}, {elo + elo_half:+.1f}]"

        print(f"  {label:<{label_width}}{100 * mean:>+10.2f}{elo:>+9.1f}{elo_half:>8.1f}{interval:>22}{rounds:>8}")

    print()


if __name__ == "__main__":
    main()
