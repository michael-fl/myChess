#!/usr/bin/env python3
"""Error bars for the fitted king-attack curve.

`docs/king-safety.md` § 4.5 quotes three calibrations — +43.5 cp at the top of the table from
master games against +73.5 from the anchor corpus — and has no way to say whether those two
numbers are distinguishable at all. Point estimates without intervals are how a corpus
difference and a sampling accident look identical.

This resamples the fit. Each replicate draws a bootstrap sample of the positions, refits the
table, and the spread across replicates gives a percentile interval per entry.

**The anchor corpus needs the caveat spelled out.** Its 64 531 positions come from 2 000 games,
sampled 40 per game, so they are not independent draws: a bootstrap over *positions* will report
intervals that are far too narrow, because resampling cannot recreate variation that the
sampling never had. Where a game identifier is available the resampling is done over **games**
instead, which is the honest unit. `--block N` approximates that for corpora without one, by
resampling contiguous blocks of N positions — consecutive lines in these files come from the
same game.

Pick the block **larger** than the average positions-per-game rather than equal to it. Too large
means fewer independent draws and therefore wider intervals, which is the safe direction; too
small pretends to an independence the data does not have. For `human-dense.epd` the extractor
emits at most 40 per game and averages 24.6, so a block of 40 is conservative by construction.

Usage::

    ../lichess-bot/venv/bin/python tools/king-attack-bootstrap.py --epd tuning-data/human-dense.epd
    ../lichess-bot/venv/bin/python tools/king-attack-bootstrap.py \\
        --epd tuning-data/mychess-anchor-dense.epd --block 40 --replicates 40

@author Michael Fleischhauer
"""

import argparse
import json
import math
import random
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_EPD = REPO_ROOT / "tuning-data" / "human-dense.epd"
DEFAULT_OUTPUT = REPO_ROOT / "test-results" / "king-attack-bootstrap.json"
JAVA = "/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home/bin/java"
CLASSPATH = "target/classes:target/test-classes:target/dependency/*"
TUNER = "org.michaelfl.mychess.TexelKingAttackTuner"

DEFAULT_REPLICATES = 30
DEFAULT_BLOCK = 1
PERCENTILES = (5, 50, 95)


def fit(epd_path, initial_step, rounds):
    """Run the Java tuner over `epd_path` and return the fitted table.

    The step schedule is passed explicitly because the tuner's default one is a ceiling, not a
    detail: coordinate descent from 0 with steps 4/2/1/0.5 over 12 rounds cannot move a
    parameter past 90. The first bootstrap run used it and reported interval widths of 1.0 for
    the top three entries — every replicate hitting the same wall, which looks like precision
    and is its opposite.
    """
    result = subprocess.run([JAVA, "-Xmx8g", "-cp", CLASSPATH, TUNER, str(epd_path),
                             "0", str(initial_step), str(rounds)],
                            cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    curve = {}

    for line in result.stdout.splitlines():
        parts = line.split("->")

        if len(parts) == 2 and parts[0].strip().isdigit():
            curve[int(parts[0].strip())] = float(parts[1].split()[0])

    return curve


def resample(lines, block, rng):
    """A bootstrap draw of the same length, in blocks of `block` consecutive lines."""
    if block <= 1:
        return [lines[rng.randrange(len(lines))] for _ in range(len(lines))]

    blocks = [lines[i:i + block] for i in range(0, len(lines), block)]
    drawn = []

    while len(drawn) < len(lines):
        drawn.extend(blocks[rng.randrange(len(blocks))])

    return drawn[:len(lines)]


def percentile(values, share):
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(share / 100.0 * (len(ordered) - 1)))))

    return ordered[index]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--epd", default=str(DEFAULT_EPD))
    parser.add_argument("--replicates", type=int, default=DEFAULT_REPLICATES)
    parser.add_argument("--block", type=int, default=DEFAULT_BLOCK,
                        help="resample blocks of this many consecutive lines; use the "
                             "extractor's positions-per-game where the corpus has one")
    parser.add_argument("--initial-step", type=float, default=16.0,
                        help="tuner initial step; the reachable magnitude is roughly "
                             "2 * initial-step * rounds")
    parser.add_argument("--rounds", type=int, default=20)
    parser.add_argument("--seed", type=int, default=20260830)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    source = Path(args.epd)
    lines = [line for line in source.read_text(encoding="utf-8", errors="ignore").splitlines()
             if " c9 " in line]
    rng = random.Random(args.seed)
    scratch = REPO_ROOT / "tuning-data" / ("bootstrap-" + source.stem + ".epd")

    print(f"{source.name}: {len(lines):,} lines, {args.replicates} replicates, "
          f"block {args.block}, reachable magnitude "
          f"~{2 * args.initial_step * args.rounds:.0f}", flush=True)

    point = fit(source, args.initial_step, args.rounds)
    replicates = []
    started = time.monotonic()

    try:
        for run in range(args.replicates):
            scratch.write_text("\n".join(resample(lines, args.block, rng)) + "\n",
                               encoding="utf-8")
            replicates.append(fit(scratch, args.initial_step, args.rounds))
            print(f"  {run + 1}/{args.replicates} "
                  f"({(time.monotonic() - started) / 60:.1f} min)", flush=True)
    finally:
        scratch.unlink(missing_ok=True)

    report = {"epd": str(source), "lines": len(lines), "replicates": args.replicates,
              "block": args.block, "point": point, "entries": {}}

    print(f"\n{'units':>6}{'fitted':>9}{'p5':>9}{'p50':>9}{'p95':>9}{'width':>9}")

    for units in sorted(point):
        draws = [r.get(units, math.nan) for r in replicates if units in r]

        if not draws:
            continue

        low, mid, high = (percentile(draws, p) for p in PERCENTILES)
        report["entries"][units] = {"fitted": point[units], "p5": low, "p50": mid, "p95": high}
        print(f"{units:>6}{point[units]:>9.1f}{low:>9.1f}{mid:>9.1f}{high:>9.1f}"
              f"{high - low:>9.1f}")

    Path(args.output).write_text(json.dumps(report, indent=1), encoding="utf-8")
    print(f"\n-> {args.output}")
    print("\nAn interval that straddles zero means the entry is not distinguishable from no "
          "term at all at that index. Compare the widths against the gap between corpora "
          "before treating that gap as a finding.")


if __name__ == "__main__":
    main()
