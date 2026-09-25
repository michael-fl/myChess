#!/usr/bin/env python3
"""Elo, interval and resume point for a match spread over several PGN segments.

WHY THIS EXISTS. cutechess-cli has no --resume, so a match that must be paused - to close
the lid, to free the machine for something else, to survive a reboot - can only be continued
as a SECOND run whose games land in a second PGN. The score line cutechess prints then covers
that segment alone. This adds the segments up and computes the figures itself.

That matters more since the acceptance criteria became bounds on the interval: criterion 0
needs the lower bound at +3, which at +/-8.9 means a point estimate near +12. A short run
cannot reach it at any point estimate, because its interval alone is wider than the gate. So
the ability to accumulate games across interruptions is what makes the criteria reachable.

HOW TO MAKE A MATCH RESUMABLE. Use `order=sequential start=N` instead of `order=random`:
sequential mode plays openings 1, 2, 3 ... in order, and START says where to begin. On resume
this script prints the next start. With `-games 2 -repeat` one round is two games on the same
opening with the colours swapped, so rounds = games / 2 and the resume point is the first
round not completely played. At most one unpaired game per interruption survives, which is a
colour imbalance of one game in thousands.

A match already running under `order=random` can still be continued - random openings stay a
random sample - it just is not reproducible.

FORMULAS, matching what cutechess prints (verified against its own output):
    p   = (w + d/2) / n
    elo = -400 log10(1/p - 1)
    var = (w + d/4) / n - p^2            the score's variance, draws counted at a quarter
    interval = 400 * 1.96 * sqrt(var/n) / (ln(10) * p * (1-p))
The interval is the delta-method transfer of the score's error onto the Elo scale, which is
why it is not symmetric in score space and why it widens as p leaves 0.5.

Usage, from the repository root:
    tools/match-elo.py [--gate SPEC ...] <candidate-name> <pgn> [<pgn> ...]

ACCEPTANCE GATES. Each match has its own pre-registered criteria, so they are passed in rather
than built in - a hard-coded set outlives the match it was written for and then reports
verdicts on criteria nobody agreed to. A gate is `LABEL:QUANTITY OP VALUE`, where QUANTITY is
`lower` or `upper` (the bounds of the 95 % interval) or `elo` (the point estimate) and OP is one
of >=, >, <=, <. Without --gate no verdicts are printed. Example, the ramp match:
    tools/match-elo.py --gate 'K1:lower>=-10' --gate 'K2:elo>=0' <candidate> <pgn> ...

The companion is tools/run-resumable-match.sh, which produces the segments and works the
resume point out on its own.
"""

import argparse
import math
import operator
import pathlib
import re
import sys

RESULT = re.compile(r'^\[Result "([^"]+)"\]')
WHITE = re.compile(r'^\[White "([^"]+)"\]')
BLACK = re.compile(r'^\[Black "([^"]+)"\]')

GATE = re.compile(r'^([^:]+):\s*(lower|upper|elo)\s*(>=|>|<=|<)\s*([-+]?\d+(?:\.\d+)?)$')

OPERATORS = {">=": operator.ge, ">": operator.gt, "<=": operator.le, "<": operator.lt}


def tally(paths, candidate):
    wins = losses = draws = 0
    white = black = None

    for path in paths:
        for line in pathlib.Path(path).read_text(errors="ignore").splitlines():
            match = WHITE.match(line)

            if match:
                white = match.group(1)
                continue

            match = BLACK.match(line)

            if match:
                black = match.group(1)
                continue

            match = RESULT.match(line)

            if not match or white is None or black is None:
                continue

            result = match.group(1)

            if result == "1/2-1/2":
                draws += 1
            elif (result == "1-0") == (white == candidate):
                wins += 1 if candidate in (white, black) else 0
            elif candidate in (white, black):
                losses += 1

            white = black = None

    return wins, losses, draws


def elo_and_interval(wins, losses, draws):
    n = wins + losses + draws

    if n == 0:
        return None

    p = (wins + 0.5 * draws) / n

    if p <= 0 or p >= 1:
        return None

    elo = -400 * math.log10(1 / p - 1)
    variance = (wins + 0.25 * draws) / n - p * p
    error = 400 * 1.96 * math.sqrt(variance / n) / (math.log(10) * p * (1 - p))
    los = 0.5 * (1 + math.erf((wins - losses) / math.sqrt(2 * (wins + losses)))) \
        if wins + losses else 0.5

    return elo, error, los, n, p


def parse_gate(spec):
    """Turns `LABEL:QUANTITY OP VALUE` into (label, quantity, op, value), or fails the parse."""
    match = GATE.match(spec.strip())

    if not match:
        raise argparse.ArgumentTypeError(
            f"bad gate {spec!r} - expected LABEL:QUANTITY OP VALUE, e.g. 'K1:lower>=-10'")

    label, quantity, op, value = match.groups()

    return label.strip(), quantity, op, float(value)


def print_gates(gates, elo, lower, upper):
    """Prints one PASS/FAIL line per gate, with the value it was judged on."""
    if not gates:
        print("  no gates given (pass them with --gate, see --help)")
        return

    values = {"elo": elo, "lower": lower, "upper": upper}
    print("  pre-registered gates")

    for label, quantity, op, threshold in gates:
        value = values[quantity]
        verdict = "PASS" if OPERATORS[op](value, threshold) else "FAIL"
        print(f"    {label:<4} {quantity} {op} {threshold:+.1f} : {verdict}   ({quantity} {value:+.1f})")


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--gate", action="append", default=[], type=parse_gate,
                        help="acceptance gate LABEL:QUANTITY OP VALUE, repeatable")
    parser.add_argument("candidate")
    parser.add_argument("paths", nargs="+", metavar="pgn")
    args = parser.parse_args()

    candidate, paths = args.candidate, args.paths
    wins, losses, draws = tally(paths, candidate)
    stats = elo_and_interval(wins, losses, draws)

    if stats is None:
        sys.exit("no decided games found - check the engine name")

    elo, error, los, n, p = stats
    lower, upper = elo - error, elo + error

    print(f"{candidate} over {len(paths)} segment(s)")
    print(f"  {wins} - {losses} - {draws}   [{p:.3f}]   {n} games")
    print(f"  Elo difference: {elo:+.1f} +/- {error:.1f}   LOS {100 * los:.1f} %")
    print(f"  95 % interval : [{lower:+.1f}, {upper:+.1f}]")
    print()
    print_gates(args.gate, elo, lower, upper)
    print()
    print(f"  to resume: -openings ... order=sequential start={n // 2 + 1}")

    if n % 2:
        print(f"  note: {n} games is an odd count, so one opening was played once only;"
              " a colour imbalance of a single game")


if __name__ == "__main__":
    main()
